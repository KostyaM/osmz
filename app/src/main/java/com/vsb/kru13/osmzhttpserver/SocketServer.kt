package com.vsb.kru13.osmzhttpserver

import android.util.Log
import com.vsb.kru13.osmzhttpserver.requestParser.RequestParser
import com.vsb.kru13.osmzhttpserver.response.HttpResponse
import com.vsb.kru13.osmzhttpserver.response.Response
import com.vsb.kru13.osmzhttpserver.response.StreamResponse
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*

@Suppress("BlockingMethodInNonBlockingContext")
class SocketServer(private val requestParser: RequestParser) : AutoCloseable {
    private companion object {
        const val PORT = 12345
        const val ACCEPT_THREAD_POOL_SIZE = 10
        const val LOG_TAG = "SERVER"

        val TO_BUSY_MESSAGE = """
            <html>
                <head></head>
                <body>
                    <center><h1>503 Server too busy</h1></center>
                    <hr>
                </body>
            </html>
        """.trimIndent()


        val BAD_REQUEST = """
            <html>
                <head></head>
                <body>
                
                    <center><h1>400 Bad request</h1></center>
                    <hr>
                </body>
            </html>
        """.trimIndent()

        val BACKEND_ERROR = """
            <html>
                <head></head>
                <body>
                    <center><h1>502 Server error</h1></center>
                    <hr>
                </body>
            </html>
        """.trimIndent()
    }


    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var serverJob: Job? = null

    private val connections = mutableMapOf<String, Socket>()
    private var serverSocket: ServerSocket? = null

    var stateListener: OnStateChangedListener? = null

    fun startServer() {
        serverJob = coroutineScope.launch {
            Log.d(LOG_TAG, "Creating Socket")
            stateListener?.onStarted()
            serverSocket = ServerSocket(PORT)

            while (!serverSocket!!.isClosed) {
                Log.d(LOG_TAG, "Socket Waiting for connection")
                val socket = try {
                    serverSocket!!.accept()
                } catch (t: Throwable) {
                    continue
                }
                Log.d(LOG_TAG, "Socket Accepted")
                val connectionUUID = UUID.randomUUID().toString().replace("-", "")
                performConnection(
                        connectionUUID = connectionUUID,
                        socket = socket
                )

            }

        }.apply {
            invokeOnCompletion { error ->
                Log.d(LOG_TAG, "Close server socket")
                serverSocket!!.close()
                error?.let {
                    Log.e(LOG_TAG, "Cause: ", it)
                }
                stateListener?.onStopped()
            }
        }
    }

    override fun close() {
        Log.d(LOG_TAG, "Close server socket")
        connections.values.forEach {
            it.close()
        }
        connections.clear()
        serverJob?.cancel()
        serverSocket!!.close()
        stateListener?.onStopped()
    }

    private fun closeConnection(connectionUUID: String) {
        connections[connectionUUID]?.close()
        connections.remove(connectionUUID)
        
        stateListener?.onConnectionCountChanged(connections.size)

    }

    private fun performConnection(connectionUUID: String, socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            connections[connectionUUID] = socket
            if (connections.size > ACCEPT_THREAD_POOL_SIZE) {
                sendResponse(
                        socket = socket,
                        response = HttpResponse(
                                status = 503,
                                statusMessaage = "Server too busy",
                                contentType = "text/html",
                                responseBody = TO_BUSY_MESSAGE.toByteArray()
                        ),
                        connectionUUID = connectionUUID
                )
                closeConnection(connectionUUID)
                return@launch
            }
            stateListener?.onConnectionCountChanged(
                    connectionsCount = connections.size
            )
            val iStream = try {
                BufferedReader(InputStreamReader(socket.getInputStream()))
            } catch (t: Throwable) {
                closeConnection(connectionUUID)
                return@launch
            }
            tryOrNull {
                iStream.readLine()
            }?.let { request ->
               requestParser.parseRequest(
                        request = request
                ) { response ->
                   sendResponse(
                           socket = socket,
                           response = response,
                           connectionUUID = connectionUUID
                   )
                }
            } ?: run {
                iStream.close()
                sendResponse(
                        socket = socket,
                        response = HttpResponse(
                                status = 400,
                                statusMessaage = "Bad request",
                                contentType = "text/html",
                                responseBody = BAD_REQUEST.toByteArray()
                        ),
                        connectionUUID = connectionUUID
                )
            }

        }
    }

    private fun sendResponse(connectionUUID: String, socket: Socket, response: Response) = try {
        val outputStream = socket.getOutputStream()
        outputStream.write(response.toByteArray())
        outputStream.flush()
        if(response !is StreamResponse || socket.isClosed)
            closeConnection(connectionUUID)
        Unit
    } catch (t: Throwable) {
        Log.e(LOG_TAG, "Failed to send response", t)
        closeConnection(connectionUUID)
    }

    interface OnStateChangedListener {
        fun onStarted()
        fun onStopped()
        fun onConnectionCountChanged(connectionsCount: Int)
    }

}
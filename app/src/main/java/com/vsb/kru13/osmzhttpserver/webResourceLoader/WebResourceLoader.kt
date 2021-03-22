package com.vsb.kru13.osmzhttpserver.webResourceLoader

import android.content.Context
import com.vsb.kru13.osmzhttpserver.VideoSource
import com.vsb.kru13.osmzhttpserver.response.HttpResponse
import com.vsb.kru13.osmzhttpserver.response.Response
import com.vsb.kru13.osmzhttpserver.response.StreamResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class WebResourceLoader(
        private val videoSource: VideoSource
) {
    private companion object {
        const val DEFAULT_RESPONE = """
            <!doctype html>
            <html>
                <head></head>
                <body>
                    Hello from server!
                </body>
            </html>
        """

        const val NOT_FOUND_RESPONSE = """
            <html>
                <head></head>
                <body> <h1>404 Not found</h1> </body>
            </html>
        """
    }

    var rootDirectoryPath: String? = null

    fun getResponse(path: String, onResponseListener: (response: Response) -> Unit) {
        if(path == "/camera/stream") {
            subscribeToVideoStream(onResponseListener)
            return
        }

        val content = rootDirectoryPath?.let {
            val file = File("$it$path")
            if (!file.exists())
                return onResponseListener(
                        HttpResponse(
                                status = 404,
                                statusMessaage = "Not found",
                                contentType = "text/html",
                                responseBody = NOT_FOUND_RESPONSE.toByteArray()
                        )
                )

            val mimeType = file.toURI().toURL().openConnection().getContentType()
            when (mimeType) {
                "text/plain", "text/html", "application/json" ->
                    return onResponseListener(
                            HttpResponse(
                                    status = 200,
                                    statusMessaage = "OK",
                                    contentType = mimeType,
                                    responseBody = file.readBytes()
                            )
                    )
                else -> return onResponseListener(
                        Response(
                                body = file.readBytes()
                        )
                )
            }

        } ?: HttpResponse(
                status = 200,
                statusMessaage = "OK",
                contentType = "text/html",
                responseBody = DEFAULT_RESPONE.toByteArray()
        )

        onResponseListener(content)
    }

    private fun subscribeToVideoStream(onResponseListener: (response: Response) -> Unit) {
        videoSource.subscribeToVideo {
            onResponseListener(
                    StreamResponse(
                            responseBody = it
                    )
            )
        }
    }

    class FileNotFoundException : Exception()
}
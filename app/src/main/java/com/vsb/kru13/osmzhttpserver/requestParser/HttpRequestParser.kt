package com.vsb.kru13.osmzhttpserver.requestParser

import com.vsb.kru13.osmzhttpserver.response.HttpResponse
import com.vsb.kru13.osmzhttpserver.response.Response
import com.vsb.kru13.osmzhttpserver.webResourceLoader.WebResourceLoader
import java.io.BufferedReader
import java.io.StringReader

class HttpRequestParser(
        private val webResourceLoader: WebResourceLoader
) : RequestParser {
    override fun parseRequest(request: String?, onResponseListener: (response: Response) -> Unit) {
        val reader = BufferedReader(StringReader(request))

        // GET /path/to/file/index.html HTTP / 1.0
        val requestLine = reader.readLine()
        val requestLineChunks = requestLine.split(" ")

        val requestType = requestLineChunks[0]
        try {
            when (requestType) {
                "GET" -> {
                    val filePath = requestLineChunks[1]
                    return webResourceLoader.getResponse(filePath, onResponseListener)
                }
                "POST" -> throw NotImplementedError()
                else -> throw NotImplementedError()
            }
        } catch (t: Throwable) {
            return when (t) {
                is NotImplementedError -> onResponseListener(
                        HttpResponse(
                                status = 400,
                                statusMessaage = "Bad request",
                                contentType = "text/plain",
                                responseBody = (t.message ?: "Unknown error").toByteArray()
                        )
                )
                else -> onResponseListener(
                        HttpResponse(
                                status = 500,
                                statusMessaage = "Backend error",
                                contentType = "text/plain",
                                responseBody = (t.message ?: "Unknown error").toByteArray()
                        )
                )
            }
        }
    }
}
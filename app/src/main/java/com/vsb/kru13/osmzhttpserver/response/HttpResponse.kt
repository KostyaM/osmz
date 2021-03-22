package com.vsb.kru13.osmzhttpserver.response

import java.nio.ByteBuffer

data class HttpResponse(
        val status: Int,
        val statusMessaage: String,
        val contentType: String,
        val responseBody: ByteArray?
) : Response(responseBody) {
    override fun toByteArray(): ByteArray {
        val responseType = "HTTP/1.0 $status $statusMessaage".toByteArray()
        val contentTypeHeader = "Content-Type: $contentType".toByteArray()
        val contentLengthHeader = "Content-Length: ${responseBody?.size ?: 0}".toByteArray()
        val connectionHeader = "Connection: Closed".toByteArray()


        val size = responseType.size + connectionHeader.size + contentTypeHeader.size + contentLengthHeader.size + (responseBody?.size ?: 0)
        val byteBuffer = ByteBuffer.allocate(size)
                .put(responseType)
                .put(contentTypeHeader)
                .put(contentLengthHeader)
                .put(connectionHeader)

        responseBody?.let {
            byteBuffer.put(it)
        }

        return byteBuffer.array()
    }
}
package com.vsb.kru13.osmzhttpserver.response

import java.nio.ByteBuffer

data class StreamResponse (
    val responseBody: ByteArray
): Response(responseBody) {
    override fun toByteArray(): ByteArray {
       val headers = """
HTTP/1.0 200 OK
Content-Type: multipart/x-mixed-replace; boundary =OSMZ_boundary

--OSMZ_boundary
Content-Type: image/jpeg
Content-Length: ${responseBody.size}
Connection: Keep Alive


       """.trimIndent().toByteArray()


        val size = headers.size+ (responseBody.size ?: 0)
        val byteBuffer = ByteBuffer.allocate(size)
                .put(headers)

        responseBody.let {
            byteBuffer.put(it)
        }

        return byteBuffer.array()
    }
}

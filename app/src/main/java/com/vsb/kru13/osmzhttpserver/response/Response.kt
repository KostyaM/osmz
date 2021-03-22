package com.vsb.kru13.osmzhttpserver.response

open class Response (
        val body: ByteArray?
) {
    open fun toByteArray(): ByteArray = body!!
}
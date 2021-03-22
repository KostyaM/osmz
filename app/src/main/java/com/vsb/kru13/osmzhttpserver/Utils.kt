package com.vsb.kru13.osmzhttpserver

inline fun <T>tryOrNull(block: () -> T?): T? = try {
    block()
} catch (t: Throwable) {
    null
}
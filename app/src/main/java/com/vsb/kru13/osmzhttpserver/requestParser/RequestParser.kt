package com.vsb.kru13.osmzhttpserver.requestParser

import com.vsb.kru13.osmzhttpserver.response.Response

interface RequestParser {
    fun parseRequest(request: String?, onResponseListener: (response: Response) -> Unit )
}
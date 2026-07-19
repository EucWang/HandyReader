package com.wxn.reader.data.remote.opds

class OpdsAuthException(
    val catalogId: Long,
    message: String = "Authentication required"
) : Exception(message)

class OpdsParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class OpdsNetworkException(
    val statusCode: Int,
    message: String
) : Exception("HTTP $statusCode: $message")

class OpdsContentTypeException(
    val contentType: String,
    val url: String,
    message: String = "Server returned non-XML content ($contentType)"
) : Exception(message)


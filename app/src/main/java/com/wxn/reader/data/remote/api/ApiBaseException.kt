package com.wxn.reader.data.remote.api

/**
 * 自定义网络异常
 */
class ApiBaseException(
    val code: String? = null,
    override val message: String? = null,
) : Exception(message)
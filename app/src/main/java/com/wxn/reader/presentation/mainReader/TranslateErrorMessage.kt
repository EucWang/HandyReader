package com.wxn.reader.presentation.mainReader

import android.content.Context
import com.wxn.reader.R
import com.wxn.reader.data.remote.api.ApiBaseException
import com.wxn.reader.data.remote.api.ApiCode

object TranslateErrorMessage {

    private val serverCodeMap = mapOf(
        "MISSING_REQUIRED_FIELD" to R.string.translate_error_missing_field,
        "INVALID_TRANSLATION_PARAMS" to R.string.translate_error_invalid_params,
        "TRANSLATION_FAILED" to R.string.translate_error_service,
        "RATE_LIMIT_EXCEEDED" to R.string.translate_error_rate_limit,
        "DAILY_LIMIT_EXCEEDED" to R.string.translate_error_daily_limit,
        "TRANSLATION_SERVICE_UNAVAILABLE" to R.string.translate_error_unavailable,
    )

    private val clientCodeMap = mapOf(
        ApiCode.CODE_SERV_UNKOWN to R.string.translate_error_network,
        ApiCode.CODE_TIME_OUT to R.string.translate_error_timeout,
        ApiCode.CODE_NETWORK_ERROR to R.string.translate_error_network,
        ApiCode.CODE_SERIALIZATION_ERROR to R.string.translate_error_service,
        ApiCode.CODE_SERV_ERROR to R.string.translate_error_service,
        ApiCode.CODE_UNKNOWN_ERROR to R.string.translate_error_service,
    )

    fun getMessage(context: Context, throwable: Throwable): String {
        if (throwable is ApiBaseException) {
            val code = throwable.code
            if (code != null) {
                serverCodeMap[code]?.let { return context.getString(it) }
                clientCodeMap[code]?.let { return context.getString(it) }
            }
        }
        return throwable.message ?: context.getString(R.string.translation_failed)
    }
}

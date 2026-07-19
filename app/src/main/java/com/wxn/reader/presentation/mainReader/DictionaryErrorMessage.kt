package com.wxn.reader.presentation.mainReader

import android.content.Context
import com.wxn.reader.R
import com.wxn.reader.data.remote.api.ApiBaseException
import com.wxn.reader.data.remote.api.ApiCode

object DictionaryErrorMessage {

    private val serverCodeMap = mapOf(
        "MISSING_AUTH_HEADER" to R.string.dict_error_auth,
        "INVALID_TOKEN" to R.string.dict_error_auth,
        "EXPIRED_TOKEN" to R.string.dict_error_auth,
        "MISSING_REQUIRED_FIELD" to R.string.dict_error_missing_field,
        "INVALID_LANGUAGE" to R.string.dict_error_invalid_lang,
        "INVALID_PARAMETER" to R.string.dict_error_invalid_param,
    )

    private val clientCodeMap = mapOf(
        ApiCode.CODE_SERV_UNKOWN to R.string.dict_error_network,
        ApiCode.CODE_TIME_OUT to R.string.dict_error_timeout,
        ApiCode.CODE_NETWORK_ERROR to R.string.dict_error_network,
        ApiCode.CODE_SERIALIZATION_ERROR to R.string.dict_error_service,
        ApiCode.CODE_SERV_ERROR to R.string.dict_error_service,
        ApiCode.CODE_UNKNOWN_ERROR to R.string.dict_error_service,
        ApiCode.CODE_DAILY_LIMIT to R.string.dict_error_daily_limit,
    )

    fun getMessage(context: Context, throwable: Throwable): String {
        if (throwable is ApiBaseException) {
            val code = throwable.code
            if (code != null) {
                serverCodeMap[code]?.let { return context.getString(it) }
                clientCodeMap[code]?.let { return context.getString(it) }
            }
        }
        return throwable.message ?: context.getString(R.string.dict_error_service)
    }
}

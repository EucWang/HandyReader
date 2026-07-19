package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.data.remote.dto.TranslateLanguagesResult
import com.wxn.reader.data.remote.dto.TranslateResult

interface TranslateApi {

    suspend fun translate(text: String, targetLang: String, sourceLang: String): Result<BaseResponse<TranslateResult>>

    suspend fun getSupportedLanguages(): Result<BaseResponse<TranslateLanguagesResult>>
}

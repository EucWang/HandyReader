package com.wxn.reader.domain.repository

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.data.remote.dto.SupportedLanguage
import com.wxn.reader.data.remote.dto.TranslateResult

interface TranslateRepository {
    suspend fun getSupportedLanguages(): List<SupportedLanguage>
    suspend fun translate(text: String, targetLang: String, sourceLang: String): Result<BaseResponse<TranslateResult>>
}

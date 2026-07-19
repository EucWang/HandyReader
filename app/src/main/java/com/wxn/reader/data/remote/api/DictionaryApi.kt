package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.data.model.WordResult

interface DictionaryApi {
    suspend fun lookup(word: String, lang: String): Result<BaseResponse<WordResult>>
}

package com.wxn.reader.domain.repository

import com.wxn.reader.data.model.WordResult

interface DictionaryRepository {
    suspend fun lookup(word: String, lang: String): Result<WordResult>
    fun getCached(word: String, lang: String): WordResult?
}

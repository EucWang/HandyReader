package com.wxn.reader.data.model

data class DictionaryPreferences(
    val lastDictLang: String = "",
    val defaultLookupApp: String = "",
    val lookupDate: String = "",
    val lookupCount: Int = 0
)

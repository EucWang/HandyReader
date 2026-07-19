package com.wxn.reader.data.dto

import androidx.room.Entity

@Entity(
    tableName = "dictionary_cache",
    primaryKeys = ["word", "lang"]
)
data class DictionaryCacheEntity(
    val word: String,
    val lang: String,
    val dataJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

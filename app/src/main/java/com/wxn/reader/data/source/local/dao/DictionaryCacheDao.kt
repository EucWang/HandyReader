package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.DictionaryCacheEntity

@Dao
interface DictionaryCacheDao {

    @Query("SELECT * FROM dictionary_cache WHERE word = :word AND lang = :lang LIMIT 1")
    suspend fun getByWordAndLang(word: String, lang: String): DictionaryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: DictionaryCacheEntity)
}

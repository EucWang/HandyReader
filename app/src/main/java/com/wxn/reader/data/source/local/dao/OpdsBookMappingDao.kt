package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.OpdsBookMappingEntity

@Dao
interface OpdsBookMappingDao {

    @Query("""
        SELECT m.* FROM opds_book_mapping m
        INNER JOIN books b ON m.bookId = b.id
        WHERE m.remoteUrl = :remoteUrl AND m.catalogId = :catalogId
        AND b.deleted = 0 AND b.importStatus = 0
        LIMIT 1
    """)
    suspend fun getActiveByRemoteUrl(remoteUrl: String, catalogId: Long): OpdsBookMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: OpdsBookMappingEntity)

    @Query("DELETE FROM opds_book_mapping WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}

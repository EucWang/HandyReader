package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.DeletedBookEntity

@Dao
interface DeletedBookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: DeletedBookEntity)

    @Query("SELECT documentId FROM deleted_books")
    suspend fun getAllDocumentIds(): List<String>

    @Query("DELETE FROM deleted_books WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM deleted_books WHERE scanDirectoryUri = :scanDirectoryUri")
    suspend fun deleteByScanDirectoryUri(scanDirectoryUri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_books WHERE documentId = :documentId)")
    suspend fun exists(documentId: String): Boolean
}

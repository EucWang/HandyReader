package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.wxn.reader.data.dto.BookAnnotationEntity

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations")
    fun getAllAnnotations(): Flow<List<BookAnnotationEntity>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: BookAnnotationEntity): Long

    @Update
    suspend fun update(annotation: BookAnnotationEntity)

    @Delete
    suspend fun delete(annotation: BookAnnotationEntity)

    @Query("SELECT * FROM annotations WHERE bookId = :bookId")
    fun getAnnotationsForBook(bookId: Long): Flow<List<BookAnnotationEntity>>
}
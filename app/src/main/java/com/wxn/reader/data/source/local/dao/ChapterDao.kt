package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.BookChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER By chapterIndex")
    fun getChapters(bookId:Long): Flow<List<BookChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapters(chapters: List<BookChapterEntity>)

    @Query("UPDATE chapters SET cachedName = :cachedName WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapter(bookId: Long, chapterIndex: Int, cachedName: String)

    @Query("UPDATE chapters SET chaptersSize = :chaptersSize WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapter(bookId: Long, chapterIndex: Int, chaptersSize: Int)

}
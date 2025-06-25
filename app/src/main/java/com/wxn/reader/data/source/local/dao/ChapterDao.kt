package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.BookChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao interface ChapterDao {

    @Query("SELECT count(*) FROM chapters WHERE bookId = :bookId")
    fun getChapterCount(bookId: Long): Flow<Int>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER By chapterIndex")
    fun getChapters(bookId:Long): Flow<List<BookChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    fun getChapter(bookId: Long, chapterIndex: Int): Flow<BookChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapters(chapters: List<BookChapterEntity>)

    @Query("UPDATE chapters SET srcName = :srcName WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapter(bookId: Long, chapterIndex: Int, srcName: String)

    @Query("UPDATE chapters SET chaptersSize = :chaptersSize WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapter(bookId: Long, chapterIndex: Int, chaptersSize: Int)

    @Query("UPDATE chapters SET wordCount = :wordCount, picCount = :picCount, chapterProgress = :chapterProgress WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapterWordCount(bookId: Long, chapterIndex: Int, wordCount: Long, picCount: Long, chapterProgress: Float)

}
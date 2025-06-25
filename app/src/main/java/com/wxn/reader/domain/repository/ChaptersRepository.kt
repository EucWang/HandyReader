package com.wxn.reader.domain.repository

import com.wxn.base.bean.BookChapter
import kotlinx.coroutines.flow.Flow

interface ChaptersRepository {

    fun getAllChapters(bookId: Long) : Flow<List<BookChapter>>

    fun getChapter(bookId:Long, chapterIndex: Int): Flow<BookChapter>

    suspend fun insertChapters(chapters: List<BookChapter>)

    fun getChapterCount(bookId:Long): Flow<Int>

    suspend fun updateChapterWordCount(bookId:Long, chapterIndex: Int, wordCount: Long, picCount: Long, chapterProgress: Float)
}
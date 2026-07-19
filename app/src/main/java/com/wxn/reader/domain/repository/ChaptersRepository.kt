package com.wxn.reader.domain.repository

import com.wxn.base.bean.BookChapter
import kotlinx.coroutines.flow.Flow

interface ChaptersRepository {

    fun getAllChapters(bookId: Long) : Flow<List<BookChapter>>

    fun getChapter(bookId:Long, chapterIndex: Int): Flow<BookChapter?>

    suspend fun insertChapters(chapters: List<BookChapter>)

    /***
     * 删除指定书籍的全部章节（用于章节结构陈旧时强制重新解析）。
     */
    suspend fun deleteChaptersByBookId(bookId: Long)

    /***
     * 原子替换某书的全部章节(单事务 delete + insert)。
     * 用于脏数据自动失效:消除 delete/insert 中间态被并发收集器读到的窗口。
     */
    suspend fun replaceChapters(bookId: Long, chapters: List<BookChapter>)

    fun getChapterCount(bookId:Long): Flow<Int>

    suspend fun updateChapterWordCount(bookId:Long, chapterIndex: Int, wordCount: Long, picCount: Long, chapterProgress: Float)
}
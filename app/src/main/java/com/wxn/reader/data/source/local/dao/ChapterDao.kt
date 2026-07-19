package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wxn.reader.data.dto.BookChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao interface ChapterDao {

    @Query("SELECT count(*) FROM chapters WHERE bookId = :bookId")
    fun getChapterCount(bookId: Long): Flow<Int>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER By chapterIndex")
    fun getChapters(bookId:Long): Flow<List<BookChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    fun getChapter(bookId: Long, chapterIndex: Int): Flow<BookChapterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<BookChapterEntity>)

    @Query("UPDATE chapters SET srcName = :srcName WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapter(bookId: Long, chapterIndex: Int, srcName: String)

    @Query("UPDATE chapters SET chaptersSize = :chaptersSize WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapter(bookId: Long, chapterIndex: Int, chaptersSize: Int)

    @Query("UPDATE chapters SET wordCount = :wordCount, picCount = :picCount, chapterProgress = :chapterProgress WHERE chapterIndex = :chapterIndex AND bookId = :bookId")
    suspend fun setChapterWordCount(bookId: Long, chapterIndex: Int, wordCount: Long, picCount: Long, chapterProgress: Float)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersByBookId(bookId: Long)

    /**
     * ★ v12 TXT 统一字节偏移方案（plan-txt-unify-byte-offset.md §3.5.2）：
     * 取某书全部章节的 wordCount（按 chapterIndex 升序），供 TxtTextParser.getWordCount 用，
     * 避免冷启动全文件重扫。类型为 Long（与 BookChapterEntity.wordCount 列一致）。
     */
    @Query("SELECT wordCount FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun getChapterWordCountsByBookId(bookId: Long): List<Long>

    /***
     * 原子替换某书的全部章节:单事务内 delete + insert。
     *
     * 用于脏数据自动失效([com.wxn.reader.domain.use_case.chapters.ReplaceChaptersByBookIdUseCase]):
     * 避免 delete 与 insert 之间出现「已删未插」中间态被并发 Flow 收集器读到(chapterSize=0 / 空列表)。
     */
    @Transaction
    suspend fun replaceChapters(bookId: Long, chapters: List<BookChapterEntity>) {
        deleteChaptersByBookId(bookId)
        insertChapters(chapters)
    }

}
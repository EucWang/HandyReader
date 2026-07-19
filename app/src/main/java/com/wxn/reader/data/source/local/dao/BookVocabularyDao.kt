package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wxn.reader.data.dto.BookVocabularyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookVocabularyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BookVocabularyEntity): Long

    @Update
    suspend fun update(entity: BookVocabularyEntity)

    @Query("UPDATE book_vocabulary SET status = -1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("""
        SELECT * FROM book_vocabulary
        WHERE status = 0
        AND (:bookId IS NULL OR bookId = :bookId)
        AND (:lang IS NULL OR lang = :lang)
        ORDER BY
            CASE WHEN :sortBy = 'createdAt' AND :isAsc = 1 THEN createdAt END ASC,
            CASE WHEN :sortBy = 'createdAt' AND :isAsc = 0 THEN createdAt END DESC,
            CASE WHEN :sortBy = 'word' AND :isAsc = 1 THEN word END ASC,
            CASE WHEN :sortBy = 'word' AND :isAsc = 0 THEN word END DESC
    """)
    fun getVocabularyEntries(
        bookId: Long?,
        lang: String?,
        sortBy: String,
        isAsc: Boolean
    ): Flow<List<BookVocabularyEntity>>

    @Query("SELECT DISTINCT bookId FROM book_vocabulary WHERE status = 0")
    fun getDistinctBookIds(): Flow<List<Long>>

    @Query("SELECT DISTINCT lang FROM book_vocabulary WHERE status = 0")
    fun getDistinctLangs(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM book_vocabulary WHERE status = 0")
    fun getActiveCount(): Flow<Int>

    // ===== ★ 同步方案 §3.2.4 一期新增:per-row HLC(不加 deleted 列,严重-4)=====
    /** 更新事件 HLC(active 行,status=0)。 */
    @Query("UPDATE book_vocabulary SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE id = :id")
    suspend fun updateSyncHlcById(id: Long, l: Long, c: Int, d: String)

    /** 删除事件 HLC(status=-1 行,独立跟踪删除时刻;status 列保留既有 -1 软删)。 */
    @Query("UPDATE book_vocabulary SET deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE id = :id")
    suspend fun updateDeletedHlcById(id: Long, l: Long, c: Int, d: String)

    /** 按 uuid 查询。 */
    @Query("SELECT * FROM book_vocabulary WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): BookVocabularyEntity?

    /** 按 bookId 查全部(含 status=-1 软删),备份导出用。 */
    @Query("SELECT * FROM book_vocabulary WHERE bookId = :bookId")
    suspend fun getByBookIdIncludeDeleted(bookId: Long): List<BookVocabularyEntity>
}

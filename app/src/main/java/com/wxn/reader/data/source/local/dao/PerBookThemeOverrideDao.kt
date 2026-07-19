package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.PerBookThemeOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * per_book_theme_overrides 表 DAO（v12 起，全量快照模式）。
 *
 * **v12 重构**：从 17 个字段级 UPDATE + ensureRow 改为单 `upsert`（@Insert onConflict=REPLACE），
 * 与 [ReaderThemeConfigDao] 的写入模式对齐。整行替换语义——ViewModel 传入完整快照，一次写入。
 *
 * UI 操作串行（滑条节流），无并发 lost-update 风险；Room REPLACE 是单行原子操作。
 */
@Dao
interface PerBookThemeOverrideDao {

    /**
     * 整行写入（saveSnapshot 语义）。REPLACE：行已存在则整行替换（v12 全量快照，对齐 ReaderThemeConfigDao.upsert）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PerBookThemeOverrideEntity)

    /**
     * 观察某 (book,theme) 的快照（effective 流数据源）。null=无快照行（effective 流走 preset 兜底）。
     */
    @Query("SELECT * FROM per_book_theme_overrides WHERE bookId = :bookId AND themeId = :themeId")
    fun observeByBookIdAndTheme(bookId: Long, themeId: String): Flow<PerBookThemeOverrideEntity?>

    /**
     * 观察某书的全部快照行（* 标记非活跃主题判定用）。
     */
    @Query("SELECT * FROM per_book_theme_overrides WHERE bookId = :bookId")
    fun observeByBookId(bookId: Long): Flow<List<PerBookThemeOverrideEntity>>

    /**
     * 一次性读取某 (book,theme) 快照。
     */
    @Query("SELECT * FROM per_book_theme_overrides WHERE bookId = :bookId AND themeId = :themeId")
    suspend fun getByBookIdAndTheme(bookId: Long, themeId: String): PerBookThemeOverrideEntity?

    /**
     * 清除某 (book,theme) 的快照（重置按钮用，effective 流回退 preset 兜底；开关保持开）。
     */
    @Query("DELETE FROM per_book_theme_overrides WHERE bookId = :bookId AND themeId = :themeId")
    suspend fun clearForTheme(bookId: Long, themeId: String)

    /**
     * 清除某书的全部快照（删书时由 FK CASCADE 自动触发，此方法供显式清理用）。
     */
    @Query("DELETE FROM per_book_theme_overrides WHERE bookId = :bookId")
    suspend fun clearForBook(bookId: Long)
}

package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.PerBookMetaEntity
import kotlinx.coroutines.flow.Flow

/**
 * per_book_meta 表 DAO（见设计方案 §Step 1.4）。
 *
 * 一书一行，存储"仅本书生效"开关状态 + per-book 选中主题 id。
 */
@Dao
interface PerBookMetaDao {

    /**
     * 插入或替换元信息（togglePerBookOverride / switchTheme 的 per-book 分支用）。
     * REPLACE：bookId 已存在则整行覆盖。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PerBookMetaEntity)

    /**
     * 观察某书的元信息变化（effective 流 + isPerBookEnabled 的数据源）。
     * 一书一行，故返回单个 entity 或 null。
     */
    @Query("SELECT * FROM per_book_meta WHERE bookId = :bookId")
    fun observeByBookId(bookId: Long): Flow<PerBookMetaEntity?>

    /**
     * 一次性读取某书元信息（switchTheme / applyAutoModeSwitch 的 per-book 分支用）。
     */
    @Query("SELECT * FROM per_book_meta WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: Long): PerBookMetaEntity?

    /**
     * 删除某书元信息（删书时由 FK CASCADE 自动触发，此方法供显式清理用）。
     */
    @Query("DELETE FROM per_book_meta WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}

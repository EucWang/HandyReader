package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.ReaderThemeConfigEntity

/**
 * 阅读主题配置存档 DAO。
 *
 * 切换主题（switchTheme）的存档/加载流程：
 * 1. saveCurrent：[upsert] 当前主题配置（含用户微调）
 * 2. loadTarget：[getByThemeId] 读取目标主题存档（null 则用预设默认值）
 * 3. resetTheme：[deleteByThemeId] 删除存档（恢复该主题的预设默认值，T13 状态转换）
 */
@Dao
interface ReaderThemeConfigDao {

    /***
     * 插入或替换主题配置（saveCurrent 用）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ReaderThemeConfigEntity)

    /***
     * 按 themeId 查找主题配置（loadTarget 用）。不存在返回 null（降级为预设默认值）。
     */
    @Query("SELECT * FROM reader_theme_configs WHERE themeId = :themeId")
    suspend fun getByThemeId(themeId: String): ReaderThemeConfigEntity?

    /***
     * 按 themeId 删除主题配置（resetTheme 用，恢复预设默认值）。
     */
    @Query("DELETE FROM reader_theme_configs WHERE themeId = :themeId")
    suspend fun deleteByThemeId(themeId: String)

    /***
     * 读取全部已存档主题（用于 * 号标识：哪些主题被用户微调过）。
     * 已存档 = 用户切换走时 saveCurrent 过 = 被微调过（显示 *）。
     */
    @Query("SELECT * FROM reader_theme_configs")
    suspend fun getAll(): List<ReaderThemeConfigEntity>
}

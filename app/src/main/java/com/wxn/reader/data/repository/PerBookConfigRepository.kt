package com.wxn.reader.data.repository

import com.wxn.reader.data.dto.PerBookMetaEntity
import com.wxn.reader.data.dto.toPerBookSnapshot
import com.wxn.reader.data.source.local.dao.PerBookMetaDao
import com.wxn.reader.data.source.local.dao.PerBookThemeOverrideDao
import com.wxn.bookread.data.model.preference.ReaderPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * per-book 阅读配置 Repository（v12 起，全量快照模式）。
 *
 * **v12 重构**：从 16 个字段级 `updateX`（ensureRow + 字段级 UPDATE）改为单 [saveSnapshot]，
 * 与 [com.wxn.reader.data.source.local.dao.ReaderThemeConfigDao.upsert] 写入模式对齐。
 * ViewModel 传入完整 effective 偏好，一次性整行替换。
 *
 * **不再需要事务**：单行 upsert（@Insert REPLACE）已是原子操作，无多字段中间态。
 */
@Singleton
class PerBookConfigRepository @Inject constructor(
    private val overridesDao: PerBookThemeOverrideDao,
    private val metaDao: PerBookMetaDao,
) {

    // ---- meta ----

    suspend fun upsertMeta(meta: PerBookMetaEntity) = metaDao.upsert(meta)

    suspend fun getMeta(bookId: Long): PerBookMetaEntity? = metaDao.getByBookId(bookId)

    // ---- snapshot ----

    /**
     * 保存 per-book × per-theme 全量快照（saveSnapshot 语义）。
     * 将 [prefs] 转为快照实体并整行 upsert（REPLACE）。行已存在则整行替换。
     *
     * @param bookId 书籍 id
     * @param themeId 主题 id
     * @param prefs 当前完整阅读偏好（通常是全局 raw 偏好 copy 改动字段后的新值）
     */
    suspend fun saveSnapshot(bookId: Long, themeId: String, prefs: ReaderPreferences) {
        overridesDao.upsert(prefs.toPerBookSnapshot(bookId, themeId))
    }
}

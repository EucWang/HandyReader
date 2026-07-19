package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * per_book_meta 表：per-book 阅读配置的元信息（一书一行）。
 *
 * 记录每本书的"仅本书生效"开关状态 + 当前 per-book 选中的主题 id。delta（具体覆盖了哪些字段）
 * 存于 [PerBookThemeOverrideEntity]（per_book_theme_overrides 表，按 `(bookId, themeId)` 稀疏存储）。
 *
 * 设计要点（见设计方案 §二.1）：
 * - `bookId` 为主键并作为指向 `books(id)` 的外键（ON DELETE CASCADE）——删书时自动清理，无孤儿数据。
 * - `overrideEnabled=false` 时该书的 delta 不生效（effective 回退纯全局 DataStore），但 delta 行保留。
 * - `selectedThemeId` 为 null 表示使用全局主题；per-book 开启时由 ViewModel 兜底为当前全局主题或默认主题。
 *
 * @param bookId 书籍 id（主键 + 外键 → books.id）
 * @param overrideEnabled "仅本书生效"开关（false=关/true=开）
 * @param selectedThemeId 当前 per-book 选中的主题 id（null=跟随全局）
 * @param createdAt 创建时间（epoch millis）
 * @param updatedAt 最后更新时间（epoch millis）
 */
@Entity(
    tableName = "per_book_meta",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PerBookMetaEntity(
    @PrimaryKey val bookId: Long,
    val overrideEnabled: Boolean = false,
    val selectedThemeId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val readerThemeMode: String? = null,  // LIGHT/DARK/AUTO 的 name；null=跟随全局
)

package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * per_book_theme_overrides 表：per-book × per-theme 的全量快照存储（v12 起）。
 *
 * **v12 重构**：从稀疏 delta（17 字段 nullable）改为全量快照（17 字段 NOT NULL），
 * 与 [ReaderThemeConfigEntity] 字段集完全对齐。每行表示"某本书在某主题下的完整主题配置快照"。
 * 放弃"未覆盖字段跟随全局"语义（原决策 #8），per-book 改为独立冻结快照——
 * 开启 per-book 瞬间冻结当前全局配置，后续改动只写 snapshot 表，全局变化不再影响该书主题字段。
 *
 * 复合主键 `(bookId, themeId)`，保证同一书不同主题各有独立快照；切主题时各主题配置互不干扰。
 *
 * 字段范围 = reader_theme_configs 纳管后全部视觉字段（17 个）：
 * 15 视觉/排版字段 + userTextAlign + forceAlignOverride。明确排除 `fontBold`/`wordSpacing`
 * （ReaderPreferences 中已 `@Deprecated("never used")`，无 UI 入口、无渲染读取，属死字段）。
 *
 * 类型转换列：[forceAlignOverride] 是 `Int`（0/1，对应模型 Boolean），写入侧
 * `if (value) 1 else 0`，读取侧见 [toReaderPreferences] 的 `!= 0`。
 *
 * @param bookId 书籍 id（复合主键之一 + 外键 → books.id ON DELETE CASCADE）
 * @param themeId 主题 id（复合主键之一）
 * @param createdAt 创建时间（epoch millis）
 * @param updatedAt 最后更新时间（epoch millis）
 */
@Entity(
    tableName = "per_book_theme_overrides",
    primaryKeys = ["bookId", "themeId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // 复合 PK (bookId, themeId) 已覆盖 bookId 前缀查询；加单独索引优化 observeByBookId/clearForBook
    indices = [Index("bookId")]
)
data class PerBookThemeOverrideEntity(
    val bookId: Long,
    val themeId: String,
    val fontSize: Double,
    val lineHeight: Double,
    val letterSpacing: Double,
    val pageHorizontalMargins: Double,
    val pageVerticalMargins: Double,
    val paragraphIndent: Double,
    val paragraphSpacing: Double,
    val textColor: Int,
    val backgroundColor: Int,
    val backgroundImage: String,
    val font: String,
    val fontVariant: String,
    val titleSize: Double,
    val titleTopSpacing: Double,
    val titleBottomSpacing: Double,
    val userTextAlign: Int,
    val forceAlignOverride: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * 转为 [ReaderThemeConfigEntity] 以复用其 [differsFrom] 判定 * 标记（P-OVER-1）。
     * 两表字段集完全一致，纯字段 copy，零逻辑转换。
     */
    fun toReaderThemeConfigEntity(): ReaderThemeConfigEntity = ReaderThemeConfigEntity(
        themeId = themeId,
        backgroundColor = backgroundColor,
        textColor = textColor,
        backgroundImage = backgroundImage,
        font = font,
        fontVariant = fontVariant,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphIndent = paragraphIndent,
        paragraphSpacing = paragraphSpacing,
        pageHorizontalMargins = pageHorizontalMargins,
        pageVerticalMargins = pageVerticalMargins,
        titleSize = titleSize,
        titleTopSpacing = titleTopSpacing,
        titleBottomSpacing = titleBottomSpacing,
        userTextAlign = userTextAlign,
        forceAlignOverride = forceAlignOverride,
        updatedAt = updatedAt,
    )
}

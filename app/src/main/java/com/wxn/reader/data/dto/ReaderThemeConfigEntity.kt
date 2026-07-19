package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 阅读主题配置存档（Room，每主题一行，至多 10 行）。
 *
 * 当前主题切走时的快照存档：切换主题时先 upsert 当前主题到本表（saveCurrent），再加载目标主题
 * （本表存档 or 预设默认值）。注意：saveCurrent 是无条件写入的，即便值与预设完全一致也会留下一行，
 * **因此"本表是否存在某 themeId 的行"不再等价于"该主题被微调过"**。
 *
 * 是否"已微调"由 [com.wxn.reader.data.dto.differsFrom] 运行时逐字段比对预设判定（用于 * 号标识）：
 * 行存在但值=预设 → 不视为已微调；老版本遗留的"值=预设"行会在刷新时被自动过滤（一次性自愈）。
 *
 * 字段与 [com.wxn.bookread.data.model.preference.ReaderThemePreset] 一一对应
 * （O-01 决策：保留 Room 存档，切主题不丢失微调）。
 *
 * 对齐字段（[userTextAlign]/[forceAlignOverride]）：v11 起随 per-book 特性同步纳入归档管控，
 * 使全局主题归档链路（saveCurrent/loadTarget）与 per-book delta 覆盖范围对齐到同一字段集。
 * 注意二者**不参与** [differsFrom] 比对——对齐是强用户偏好，不随主题预设变化（见 differsFrom 文档）。
 *
 * @param themeId 主键，10 个预设 id 之一（亮：default/cream/classic/sepia/green；暗：amoled_black/night/dark_blue/dark_grey/dark_green）
 * @param userTextAlign 用户对齐方式（1=Left/2=Right/3=Center/4=Justify），v11 新增
 * @param forceAlignOverride 是否强制覆盖书籍 CSS 对齐（0=false/1=true），v11 新增
 * @param updatedAt 最后更新时间（epoch millis）
 */
@Entity(tableName = "reader_theme_configs")
data class ReaderThemeConfigEntity(
    @PrimaryKey
    val themeId: String,
    val backgroundColor: Int,
    val textColor: Int,
    val backgroundImage: String,
    val font: String,
    val fontVariant: String,
    val fontSize: Double,
    val lineHeight: Double,
    val letterSpacing: Double,
    val paragraphIndent: Double,
    val paragraphSpacing: Double,
    val pageHorizontalMargins: Double,
    val pageVerticalMargins: Double,
    val titleSize: Double,
    val titleTopSpacing: Double,
    val titleBottomSpacing: Double,
    val userTextAlign: Int = 4,
    val forceAlignOverride: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

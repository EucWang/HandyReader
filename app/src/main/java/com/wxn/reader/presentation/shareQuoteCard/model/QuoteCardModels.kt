package com.wxn.reader.presentation.shareQuoteCard.model

/**
 * 书摘卡片输入数据（不可变快照）。
 *
 * 在 [com.wxn.reader.presentation.mainReader.MainReadViewModel.snapshotQuoteData] 中一次性组装，
 * 后续不响应 book 变化（数据快照，防 U-20 竞态）。
 */
data class QuoteCardData(
    val rawQuoteText: String,
    val defaultEditableText: String,
    val bookTitle: String,
    val bookAuthor: String?,
    val chapterName: String?,
    val chapterIndex: Int?,
    val coverPath: String?,
    val readingProgress: Float?,
    val bookFileType: String,
    val createdAt: Long
)

/**
 * 卡片样式模板（5 种）。
 *
 * 卡片定义本身在 [com.wxn.reader.presentation.shareQuoteCard.components.QuoteCardComposable]，
 * 此枚举仅携带元数据（KMP-clean，不依赖 Android Resources）。
 */
enum class QuoteCardStyle(val isDark: Boolean) {
    MINIMAL_WHITE(false),
    DARK_NIGHT(true),
    PARCHMENT(false),
    COVER_POSTER(false),
    BIG_QUOTE(false);

    /** 判断当前数据是否支持该样式（封面海报需要封面图） */
    fun isAvailableFor(data: QuoteCardData): Boolean = when (this) {
        COVER_POSTER -> !data.coverPath.isNullOrEmpty()
        else -> true
    }
}

/**
 * 卡片尺寸比例（4 种，用户可配置）。
 * width/height 为最终输出 Bitmap 的像素尺寸。
 */
enum class QuoteCardRatio(val width: Int, val height: Int) {
    RATIO_3_4(1080, 1440),
    RATIO_1_1(1080, 1080),
    RATIO_9_16(1080, 1920),
    RATIO_4_5(1080, 1350)
}

/**
 * 卡片字号档位（3 种，用户可配置）。
 * scale 为相对于基准字号的缩放系数。
 */
enum class QuoteFontSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.2f);

    /**
     * 循环切换：小 → 中 → 大 → 小 → 中 → 大 → …
     * 顺序与 UI 单按钮切换逻辑保持一致。
     */
    fun next(): QuoteFontSize = when (this) {
        SMALL -> MEDIUM
        MEDIUM -> LARGE
        LARGE -> SMALL
    }
}

/**
 * 用户配置（运行时状态 + DataStore 持久化载体）。
 */
data class QuoteCardConfig(
    val style: QuoteCardStyle = QuoteCardStyle.MINIMAL_WHITE,
    val ratio: QuoteCardRatio = QuoteCardRatio.RATIO_3_4,
    val fontSize: QuoteFontSize = QuoteFontSize.MEDIUM,
    val showProgress: Boolean = false
)

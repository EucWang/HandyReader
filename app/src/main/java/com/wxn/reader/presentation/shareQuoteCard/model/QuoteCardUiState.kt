package com.wxn.reader.presentation.shareQuoteCard.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 书摘卡片 UI 状态。
 *
 * 存储在 [com.wxn.reader.presentation.mainReader.MainReadViewModel] 的 [_quoteCardState] 中，
 * 配置变更时自动保留（VM 生命周期）。
 */
data class QuoteCardUiState(
    val data: QuoteCardData? = null,
    val config: QuoteCardConfig = QuoteCardConfig(),
    val coverBitmap: ImageBitmap? = null,
    val phase: QuotePhase = QuotePhase.IDLE,
    val errorCode: QuoteErrorCode? = null
) {
    /** 渲染中或分享中时禁用按钮 */
    val isBusy: Boolean
        get() = phase == QuotePhase.RENDERING || phase == QuotePhase.SHARE_CHOOSER
}

/**
 * 书摘卡片业务状态机。
 *
 * 状态转换详见 `docs/plans/2026-06-20-share-quote-card-design.md` 第五章。
 */
enum class QuotePhase {
    IDLE,
    DATA_PREPARE,
    DIALOG_OPEN,
    RENDERING,
    SHARE_CHOOSER,
    SAVED,
    ERR_DATA,
    ERR_RENDER,
    ERR_TIMEOUT
}

/**
 * 错误码。与 stringResource 映射见 QuoteCardStrings.kt。
 * TEXT_TRUNCATED 不在此（属警告，见 [QuoteCardUiState.isTruncated]）。
 */
enum class QuoteErrorCode {
    DATA_MISSING,
    COVER_LOAD_FAIL,
    RENDER_OOM,
    RENDER_TIMEOUT,
    GALLERY_PERMISSION_DENIED,
    GALLERY_IO,
    NO_SHARE_TARGET,
    TEXT_TOO_SHORT,
    QUOTE_EDIT_EMPTY;

    /** 是否为严重错误（ErrorBanner 不自动消失，需用户操作） */
    val isSevere: Boolean
        get() = this in SEVERE_CODES

    companion object {
        private val SEVERE_CODES = setOf(
            RENDER_OOM, RENDER_TIMEOUT,
            GALLERY_PERMISSION_DENIED, GALLERY_IO,
            NO_SHARE_TARGET
        )
    }
}

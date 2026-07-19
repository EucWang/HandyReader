package com.wxn.reader.presentation.shareQuoteCard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wxn.reader.R
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardRatio
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardStyle
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteFontSize

/** 枚举 → 字符串资源映射（Android-specific 层，隔离模型与资源） */
@Composable
fun QuoteCardStyle.displayName(): String = stringResource(
    when (this) {
        QuoteCardStyle.MINIMAL_WHITE -> R.string.quote_style_minimal_white
        QuoteCardStyle.DARK_NIGHT -> R.string.quote_style_dark_night
        QuoteCardStyle.PARCHMENT -> R.string.quote_style_parchment
        QuoteCardStyle.COVER_POSTER -> R.string.quote_style_cover_poster
        QuoteCardStyle.BIG_QUOTE -> R.string.quote_style_big_quote
    }
)

@Composable
fun QuoteCardRatio.displayName(): String = stringResource(
    when (this) {
        QuoteCardRatio.RATIO_3_4 -> R.string.ratio_3_4
        QuoteCardRatio.RATIO_1_1 -> R.string.ratio_1_1
        QuoteCardRatio.RATIO_9_16 -> R.string.ratio_9_16
        QuoteCardRatio.RATIO_4_5 -> R.string.ratio_4_5
    }
)

@Composable
fun QuoteFontSize.displayName(): String = stringResource(
    when (this) {
        QuoteFontSize.SMALL -> R.string.font_size_small
        QuoteFontSize.MEDIUM -> R.string.font_size_medium
        QuoteFontSize.LARGE -> R.string.font_size_large
    }
)

@Composable
fun QuoteErrorCode.displayMessage(): String = stringResource(
    when (this) {
        QuoteErrorCode.DATA_MISSING -> R.string.err_data_missing
        QuoteErrorCode.COVER_LOAD_FAIL -> R.string.err_cover_load_fail
        QuoteErrorCode.RENDER_OOM -> R.string.err_render_oom
        QuoteErrorCode.RENDER_TIMEOUT -> R.string.err_render_timeout
        QuoteErrorCode.GALLERY_PERMISSION_DENIED -> R.string.err_gallery_permission
        QuoteErrorCode.GALLERY_IO -> R.string.err_gallery_io
        QuoteErrorCode.NO_SHARE_TARGET -> R.string.err_no_share_target
        QuoteErrorCode.TEXT_TOO_SHORT -> R.string.quote_too_short
        QuoteErrorCode.QUOTE_EDIT_EMPTY -> R.string.err_quote_empty
    }
)

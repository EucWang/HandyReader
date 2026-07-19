package com.wxn.reader.presentation.shareQuoteCard.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxn.reader.R
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardConfig
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardStyle

/**
 * 卡片设计的固定密度：1dp = 3px（卡片按 360dp 宽设计）。
 *
 * 预览与 QuoteCardCapture 输出共享同一 density，保证所见即所得（X1）。
 * fontScale=1：分享图应保持设计尺寸，不跟随系统"超大字号"无障碍设置。
 */
private const val CARD_DESIGN_DENSITY = 3.0f

/**
 * 书摘卡片 Composable 入口（纯 Compose，KMP-clean）。
 *
 * 根据 [QuoteCardStyle] 分发到对应样式。父容器负责设置尺寸（preview 0.5x / 最终输出 1.0x）。
 *
 * @param editableText 用户编辑后的引文文本（非 [QuoteCardData.rawQuoteText]）
 */
@Composable
fun QuoteCard(
    data: QuoteCardData,
    editableText: String,
    config: QuoteCardConfig,
    coverBitmap: ImageBitmap?,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier
) {
    // 固定 density，预览与输出一致（X1）
    CompositionLocalProvider(
        LocalDensity provides Density(CARD_DESIGN_DENSITY, fontScale = 1f)
    ) {
        val baseFontSize = 18f * config.fontSize.scale
        val infoFontSize = 12f * config.fontSize.scale

        when (config.style) {
            QuoteCardStyle.MINIMAL_WHITE -> MinimalWhiteCard(data, editableText, config, fontFamily, baseFontSize, infoFontSize, modifier)
            QuoteCardStyle.DARK_NIGHT -> DarkNightCard(data, editableText, config, fontFamily, baseFontSize, infoFontSize, modifier)
            QuoteCardStyle.PARCHMENT -> ParchmentCard(data, editableText, config, fontFamily, baseFontSize, infoFontSize, modifier)
            QuoteCardStyle.COVER_POSTER -> CoverPosterCard(data, editableText, config, coverBitmap, fontFamily, baseFontSize, infoFontSize, modifier)
            QuoteCardStyle.BIG_QUOTE -> BigQuoteCard(data, editableText, config, fontFamily, baseFontSize, infoFontSize, modifier)
        }
    }
}

// ==================== 品牌标识（Logo + 应用名，强制 LTR，不跟随 RTL） ====================

@Composable
private fun QuoteWatermark(
    textColor: Color,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.padding(end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Logo 显示原本颜色（品牌凭证，不做 tint/alpha 处理）
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            // 应用名（textColor 保证各卡片样式下的可读性）
            androidx.compose.material3.Text(
                text = stringResource(R.string.app_name),
                color = textColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==================== 书籍信息块 ====================

@Composable
private fun BookInfoBlock(
    data: QuoteCardData,
    config: QuoteCardConfig,
    fontFamily: FontFamily?,
    fontSize: Float,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Text(
            text = "《${data.bookTitle}》",
            color = textColor,
            fontSize = fontSize.sp,
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        data.bookAuthor?.takeIf { it.isNotBlank() }?.let { author ->
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = author,
                color = textColor.copy(alpha = 0.7f),
                fontSize = (fontSize * 0.8f).sp,
                fontFamily = fontFamily ?: FontFamily.Default,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (config.showProgress) {
            data.readingProgress?.let { progress ->
                Spacer(modifier = Modifier.height(3.dp))
                androidx.compose.material3.Text(
                    text = stringResource(R.string.quote_reading_progress, progress),
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = (fontSize * 0.65f).sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==================== 极简白 ====================

@Composable
private fun MinimalWhiteCard(
    data: QuoteCardData,
    editableText: String,
    config: QuoteCardConfig,
    fontFamily: FontFamily?,
    quoteFontSize: Float,
    infoFontSize: Float,
    modifier: Modifier
) {
    Box(modifier = modifier.background(Color(0xFFFAFAFA))) {
            Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 装饰引号（半透明，与 DarkNight/Parchment/CoverPoster 统一规格）
            androidx.compose.material3.Text(
                text = "\u201C",
                color = Color(0xFF333333).copy(alpha = 0.3f),
                fontSize = (quoteFontSize * 1.8f).sp,
                fontFamily = FontFamily.Serif
            )
            Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                androidx.compose.material3.Text(
                    text = editableText,
                    color = Color(0xFF333333),
                    fontSize = quoteFontSize.sp,
                    fontFamily = fontFamily ?: FontFamily.Serif,
                    lineHeight = (quoteFontSize * 1.6f).sp,
                    textAlign = TextAlign.Start
                )
            }
            BookInfoBlock(data, config, fontFamily, infoFontSize, Color(0xFF333333))
        }
        QuoteWatermark(Color(0xFF333333), Modifier.align(Alignment.BottomEnd))
    }
}

// ==================== 暗夜 ====================

@Composable
private fun DarkNightCard(
    data: QuoteCardData,
    editableText: String,
    config: QuoteCardConfig,
    fontFamily: FontFamily?,
    quoteFontSize: Float,
    infoFontSize: Float,
    modifier: Modifier
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A))
    )
    Box(modifier = modifier.background(gradient)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.Text(
                text = "\u201C",
                color = Color(0xFFE0E0E0).copy(alpha = 0.3f),
                fontSize = (quoteFontSize * 1.8f).sp,
                fontFamily = FontFamily.Serif
            )
            Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                androidx.compose.material3.Text(
                    text = editableText,
                    color = Color(0xFFEEEEEE),
                    fontSize = quoteFontSize.sp,
                    fontFamily = fontFamily ?: FontFamily.Default,
                    lineHeight = (quoteFontSize * 1.7f).sp,
                    textAlign = TextAlign.Start
                )
            }
            BookInfoBlock(data, config, fontFamily, infoFontSize, Color(0xFFEEEEEE))
        }
        QuoteWatermark(Color(0xFFEEEEEE), Modifier.align(Alignment.BottomEnd))
    }
}

// ==================== 羊皮纸 ====================

@Composable
private fun ParchmentCard(
    data: QuoteCardData,
    editableText: String,
    config: QuoteCardConfig,
    fontFamily: FontFamily?,
    quoteFontSize: Float,
    infoFontSize: Float,
    modifier: Modifier
) {
    Box(modifier = modifier.background(Color(0xFFF5E6C8))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFD7C49E), RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 装饰引号（半透明）
                androidx.compose.material3.Text(
                    text = "\u201C",
                    color = Color(0xFF5D4037).copy(alpha = 0.3f),
                    fontSize = (quoteFontSize * 1.8f).sp,
                    fontFamily = FontFamily.Serif
                )
                Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                    androidx.compose.material3.Text(
                        text = editableText,
                        color = Color(0xFF5D4037),
                        fontSize = quoteFontSize.sp,
                        fontFamily = fontFamily ?: FontFamily.Serif,
                        lineHeight = (quoteFontSize * 1.7f).sp,
                        textAlign = TextAlign.Start
                    )
                }
                BookInfoBlock(data, config, fontFamily, infoFontSize, Color(0xFF5D4037))
            }
        }
        QuoteWatermark(Color(0xFF5D4037), Modifier.align(Alignment.BottomEnd))
    }
}

// ==================== 封面海报 ====================

@Composable
private fun CoverPosterCard(
    data: QuoteCardData,
    editableText: String,
    config: QuoteCardConfig,
    coverBitmap: ImageBitmap?,
    fontFamily: FontFamily?,
    quoteFontSize: Float,
    infoFontSize: Float,
    modifier: Modifier
) {
    Box(modifier = modifier.background(Color(0xFF1A1A1A))) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)))
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 52.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 装饰引号（半透明）
            androidx.compose.material3.Text(
                text = "\u201C",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = (quoteFontSize * 1.8f).sp,
                fontFamily = FontFamily.Serif
            )
            Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                androidx.compose.material3.Text(
                    text = editableText,
                    color = Color.White,
                    fontSize = quoteFontSize.sp,
                    fontFamily = fontFamily ?: FontFamily.Default,
                    lineHeight = (quoteFontSize * 1.6f).sp,
                    textAlign = TextAlign.Start
                )
            }
            BookInfoBlock(data, config, fontFamily, infoFontSize, Color.White)
        }
        QuoteWatermark(Color.White, Modifier.align(Alignment.BottomEnd))
    }
}

// ==================== 大引号 ====================

@Composable
private fun BigQuoteCard(
    data: QuoteCardData,
    editableText: String,
    config: QuoteCardConfig,
    fontFamily: FontFamily?,
    quoteFontSize: Float,
    infoFontSize: Float,
    modifier: Modifier
) {
    Box(modifier = modifier.background(Color(0xFFF0F0F0))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = "\u201C",
                color = Color(0xFFBDBDBD),
                fontSize = (quoteFontSize * 2.5f).sp,
                fontFamily = FontFamily.Serif
            )
            androidx.compose.material3.Text(
                text = editableText,
                color = Color(0xFF424242),
                fontSize = (quoteFontSize * 0.9f).sp,
                fontFamily = fontFamily ?: FontFamily.Serif,
                lineHeight = (quoteFontSize * 1.6f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            BookInfoBlock(data, config, fontFamily, infoFontSize, Color(0xFF424242))
        }
        QuoteWatermark(Color(0xFF424242), Modifier.align(Alignment.BottomEnd))
    }
}

package com.wxn.reader.presentation.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun BookCoverPlaceholder(
    title: String,
    author: String,
    isAudiobook: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    showText: Boolean = true,
) {
    val bgColor = remember(title) { title.toCoverColor() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor),
    ) {
        if (showText) {
            // 半透明深色渐变遮罩：保证任意 hue 下白字对比度 ≥ 4.5:1 (WCAG AA)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.28f),
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (isAudiobook) {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(if (showText) Alignment.TopEnd else Alignment.Center)
                    .padding(if (showText) 4.dp else 0.dp)
                    .size(if (showText) 18.dp else 36.dp),
            )
        }
    }
}

/**
 * 根据书名 hashCode 生成确定性的、对白字友好的封面背景色。
 *
 * 对比度保证：所有 hue 下，叠加遮罩后白色文字对比度 ≥ 4.5:1 (WCAG AA)。
 * - 黄/绿/青区（hue 40-160）相对亮度高，lightness 压低到 0.26。
 * - 其余色相 lightness=0.34。
 * - 空 title 返回固定中性深蓝灰，避免 hue=0（纯红）。
 */
private fun String.toCoverColor(): Color {
    if (isBlank()) return Color.hsl(220f, 0.12f, 0.30f)
    val hash = hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }
    val hue = (hash % 360).toFloat()
    val lightness = if (hue in 40f..160f) 0.26f else 0.34f
    return Color.hsl(hue = hue, saturation = 0.45f, lightness = lightness)
}

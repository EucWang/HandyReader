package com.wxn.reader.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp


/****
 *  心型显示的 RatingBar
 *  @param rating: 取值 0~5
 *  @param 点击选择进度事件
 *  @param maxHearts
 *  @param filledHeartColor, 填充满的颜色
 *  @param  emptyHeartColor, 空的颜色
 *  @param  starSize, 星星的大小
 *  @param  modifier
 */
@Composable
fun HeartRatingBar(
    rating: Float,
    onRatingChanged: ((Float) -> Unit)? = null,
    maxHearts: Int = 5,
    filledHeartColor: Color = MaterialTheme.colorScheme.outlineVariant,
    emptyHeartColor: Color = MaterialTheme.colorScheme.primaryContainer,
    starSize: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        for (i in 1..maxHearts) {
            when {
                i <= rating -> { // 完全填充
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Rating $i",
                        tint = filledHeartColor,
                        modifier = Modifier
                            .size(starSize)
                            .clickable { onRatingChanged?.invoke(i.toFloat()) }
                    )
                }

                i - rating in 0.1f..0.9f -> {  // 半填充（可用自定义半爱心图标）
                    HalfFilledHeart(
                        i - rating,
                        filledColor = filledHeartColor,
                        emptyColor = emptyHeartColor,
                        modifier = Modifier.size(starSize)
                    )
                }

                else -> {                       // 未填充
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Rating $i",
                        tint = emptyHeartColor,
                        modifier = Modifier
                            .size(starSize)
                            .clickable { onRatingChanged?.invoke(i.toFloat()) }
                    )
                }
            }
            if (i < maxHearts) {
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
fun HalfFilledHeart(
    fillFraction: Float = 0.5f,        // 0.0 ~ 1.0，填充比例（从左向右）
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp,
    filledColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(modifier = modifier.size(starSize)) {
        // 底层：空心爱心（始终完整显示）
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = emptyColor,
            modifier = Modifier.matchParentSize()
        )
        // 上层：实心爱心，通过裁剪只显示左半部分
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = filledColor,
            modifier = Modifier
                .matchParentSize()
                .clip(RectangleCutShape(1.0f- fillFraction))
        )
    }
}

class RectangleCutShape(private val cutFraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cutWidth = size.width * cutFraction
        // 创建一个矩形，其宽度被裁剪为 cutFraction 比例
        val rect = Rect(0f, 0f, cutWidth, size.height)
        return Outline.Rectangle(rect)
    }
}
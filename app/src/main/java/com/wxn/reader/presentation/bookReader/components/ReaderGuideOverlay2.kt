package com.wxn.reader.presentation.bookReader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxn.reader.R

/**
 * 阅读引导页覆盖层
 * 显示三个点击区域的说明：左侧翻上一页，中间显示菜单，右侧翻下一页
 */
@Composable
fun ReaderGuideOverlay2(
    onDismiss: () -> Unit,
    leftHandMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onDismiss() }
    ) {
        // 半透明背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // 获取屏幕尺寸
        val density = LocalDensity.current
        val screenWidthPx = with(density) { 1080.dp.toPx() } // 估算值，实际会自适应
        val screenHeightPx = with(density) { 2400.dp.toPx() }

        // 绘制虚线框和说明文字
        GuideOverlayContent2(
            leftHandMode,
            modifier = Modifier.fillMaxSize()
        )

        // 关闭按钮
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.reader_guide_close),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun GuideOverlayContent2(
    leftHandMode: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // topRectF 的定义: RectF(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
            val topLeft = 0f
            val topTop = 0f
            val topRight = width
            val topBottom = height * 0.4f

            // 虚线样式
            val dashEffect =  PathEffect.dashPathEffect(floatArrayOf(10f, 20f), 25f)// PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 25f)
            val stroke = Stroke(
                width = 4.dp.toPx(),
                pathEffect = dashEffect
            )

            drawRoundRect(
                color = Color.White,
                style = stroke,
                topLeft = Offset(
                    x = topLeft + width * 0.01f,
                    y = topTop + height * 0.01f
                ),
                size = Size(
                    width = (topRight - topLeft) * 0.98f,
                    height = (topBottom - topTop) * 0.98f
                ),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )

            drawLine(
                cap = StrokeCap.Round,
                color = Color.White,
                start = Offset(width/2f, topBottom),
                end = Offset(width /2f, height),
                strokeWidth = 8f,
                pathEffect = dashEffect
            )
        }


        Column(modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 中间说明
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.reader_guide_tap_here),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.reader_guide_toggle_menu),
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 说明文字
            Row(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧说明
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reader_guide_tap_here),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(
                                if (leftHandMode) {
                                    R.string.reader_guide_next_page
                                } else {
                                    R.string.reader_guide_prev_page
                                }),
                            color = Color.White,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }


                // 右侧说明
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reader_guide_tap_here),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(
                                if (leftHandMode) {
                                    R.string.reader_guide_prev_page
                                } else {
                                    R.string.reader_guide_next_page
                                }),
                            color = Color.White,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

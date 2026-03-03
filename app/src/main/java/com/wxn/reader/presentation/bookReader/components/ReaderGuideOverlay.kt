package com.wxn.reader.presentation.bookReader.components

import android.graphics.PathDashPathEffect
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
fun ReaderGuideOverlay(
    onDismiss: () -> Unit,
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
        GuideOverlayContent(
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
private fun GuideOverlayContent(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // centerRectF 的定义: RectF(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
            val centerLeft = width * 0.33f
            val centerTop = height * 0.33f
            val centerRight = width * 0.66f
            val centerBottom = height * 0.66f

            // 虚线样式
            val dashEffect =  PathEffect.dashPathEffect(floatArrayOf(10f, 20f), 25f)// PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 25f)
            val stroke = Stroke(
                width = 4.dp.toPx(),
                pathEffect = dashEffect
            )

            // 左侧区域 (0 ~ centerLeft)
//            drawRoundRect(
//                color = Color.White,
//                style = stroke,
//                topLeft = androidx.compose.ui.geometry.Offset(
//                    x = width * 0.05f,
//                    y = height * 0.25f
//                ),
//                size = androidx.compose.ui.geometry.Size(
//                    width = centerLeft * 0.9f,
//                    height = height * 0.5f
//                ),
//                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
//            )

            // 中间区域 (centerLeft ~ centerRight)
            drawRoundRect(
                color = Color.White,
                style = stroke,
                topLeft = Offset(
                    x = centerLeft + width * 0.01f,
                    y = centerTop + height * 0.01f
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (centerRight - centerLeft) * 0.98f,
                    height = (centerBottom - centerTop) * 0.98f
                ),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )

            drawLine(
                cap = StrokeCap.Round,
                color = Color.White,
                start = Offset(width/2f, 0f),
                end = Offset(width /2f, centerTop + width * 0.01f),
                strokeWidth = 8f,
                pathEffect = dashEffect
            )

            drawLine(
                cap = StrokeCap.Round,
                color = Color.White,
                start = Offset(width/2f, centerBottom + width * 0.01f),
                end = Offset(width /2f, height),
                strokeWidth = 8f,
                pathEffect = dashEffect
            )
        }

        // 说明文字
        Row(
            modifier = Modifier.fillMaxSize(),
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
                        text = stringResource(R.string.reader_guide_prev_page),
                        color = Color.White,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

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
                        text = stringResource(R.string.reader_guide_next_page),
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

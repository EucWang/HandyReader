package com.wxn.reader.util

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import com.wxn.base.util.Logger
import com.wxn.reader.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val VIEW_CLICK_INTERVAL_TIME = 400


@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun rememberMutableInteractionSource() = remember { MutableInteractionSource() }


@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T> rememberSaveableMutableStateOf(
    value: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy()
): MutableState<T> = rememberSaveable(init = { mutableStateOf(value, policy) })


class Ref(var value: Int)

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun LogCompositions(msg: String) {
    if (BuildConfig.DEBUG) {
        val ref = remember { Ref(0) }
        SideEffect { ref.value++ }
        Logger.d("Compositions: $msg ${ref.value}")
    }
}

@Composable
fun OnFirstLaunch(delayTime: Long = 300L, block: suspend CoroutineScope.() -> Unit) {
    var firstLaunch by rememberSaveableMutableStateOf(true)
    LaunchedEffect(null) {
        if (firstLaunch) {
            firstLaunch = false
            delay(delayTime)
            block.invoke(this)
        }
    }
}

@Suppress("unused")
@Composable
fun <T> OnLaunchFlow(key: Any? = null, emitter: () -> T, observer: suspend (value: T) -> Unit) {
    LaunchedEffect(key) {
        snapshotFlow(emitter).collect(observer)
    }
}


/***
 * 无水波纹的点击,防重复点击
 */
@Suppress("unused")
fun Modifier.clickPlainly(enabled: Boolean = true, time:Int = VIEW_CLICK_INTERVAL_TIME, onClick: () -> Unit) = composed {
    var lastClickTime by remember { mutableStateOf(0L) }
    clickable(
        enabled = enabled,
        interactionSource = rememberMutableInteractionSource(),
        indication = null,
        onClick = {
            val curTimemillis = System.currentTimeMillis()
            if (curTimemillis - lastClickTime > time) {
                onClick.invoke()
                lastClickTime = curTimemillis
            }
        }
    )
}

val emptyBlock = { }

@Suppress("unused")
val emptyComposeBlock: @Composable () -> Unit = {}

@Suppress("unused")
fun Modifier.consumeClick() = composed {
    clickable(
        enabled = true, interactionSource = rememberMutableInteractionSource(), indication = null,
        onClick = emptyBlock
    )
}

@Suppress("unused")
fun Modifier.background(
    colors: List<Color>,
    shape: Shape = RectangleShape,
    alpha: Float = 1F,
    orientation: Int = Orientation.HORIZONTAL
): Modifier {
    val size = colors.size
    if (size == 0) return this
    if (size == 1) return background(color = colors.first(), shape = shape)
    val brush = if (orientation == Orientation.HORIZONTAL) {
        Brush.horizontalGradient(colors = colors)
    } else {
        Brush.verticalGradient(colors = colors)
    }
    return background(brush = brush, shape = shape, alpha = alpha)
}

@Suppress("unused")
fun Modifier.roundCornerShape(color: Color, size: Dp) = background(color = color, shape = RoundedCornerShape(size))

@Suppress("unused")
fun Modifier.roundCornerShape(colors: List<Color>, size: Dp) = background(colors = colors, shape = RoundedCornerShape(size))

@Suppress("unused")
@Composable
fun Space(width: Dp = 0.dp, height: Dp = 0.dp) {
    Spacer(modifier = Modifier.size(width, height))
}

@Suppress("unused")
@Composable
fun WidthSpace(width: Dp) {
    Spacer(modifier = Modifier.width(width))
}

@Suppress("unused")
@Composable
fun WidthSpace(intrinsicSize: IntrinsicSize) {
    Spacer(modifier = Modifier.width(intrinsicSize))
}

@Suppress("unused")
@Composable
fun HeightSpace(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}

@Suppress("unused")
@Composable
fun HeightSpace(intrinsicSize: IntrinsicSize) {
    Spacer(modifier = Modifier.height(intrinsicSize))
}

@Suppress("unused")
@Composable
fun RowScope.WeightSpace(weight: Float, fill: Boolean = true) {
    Spacer(modifier = Modifier.weight(weight, fill))
}

@Suppress("unused")
@Composable
fun ColumnScope.WeightSpace(weight: Float, fill: Boolean = true) {
    Spacer(modifier = Modifier.weight(weight, fill))
}

@Suppress("unused")
@Composable
fun WidthDivider(width: Dp = 8.dp, color: Color = Color.Unspecified) {
    if (color != Color.Unspecified) {
        Box(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .background(color)
        )
    } else {
        Box(modifier = Modifier.width(width).fillMaxHeight())
    }
}

@Suppress("unused")
@Composable
fun HeightDivider(height: Dp = 8.dp, color: Color = Color.Unspecified) {
    if (color != Color.Unspecified) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(color)
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(height))
    }
}

@Suppress("unused")
fun Modifier.tapGesture(
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) = pointerInput(null) {
    detectTapGestures(
        onTap = {
            onClick?.invoke()
        }, onLongPress = {
            onLongClick?.invoke()
        }, onDoubleTap = {
            onDoubleClick?.invoke()
        })
}

@Stable
@Suppress("unused")
class BorderStyle(val width: Dp, val color: Color)

@Suppress("unused")
fun Modifier.border(style: BorderStyle? = null, shape: Shape) = if (style != null) {
    border(width = style.width, color = style.color, shape = shape)
} else {
    this
}


/**
 *  虚线border、
 */
@Suppress("unused")
fun Modifier.dashedBorder(width: Dp, radius: Dp, color: Color) =
    drawBehind {
        drawIntoCanvas {
            val paint = Paint()
                .apply {
                    strokeWidth = width.toPx()
                    this.color = color
                    style = PaintingStyle.Stroke
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                }
            it.drawRoundRect(
                width.toPx(),
                width.toPx(),
                size.width - width.toPx(),
                size.height - width.toPx(),
                radius.toPx(),
                radius.toPx(),
                paint
            )
        }
    }
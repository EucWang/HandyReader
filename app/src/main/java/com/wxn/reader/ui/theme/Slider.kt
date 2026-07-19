package com.wxn.reader.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wxn.reader.util.rememberMutableInteractionSource
import kotlin.math.roundToInt

/**
 * 通用滑块，对齐 [androidx.compose.material3.Slider] 语义：支持 [valueRange]、[steps]、[enabled]、
 * tap-to-seek、拖动 seek、M3 ±10% thumb 缩放反馈。
 *
 * 自绘轨道与 thumb（不依赖 Material3 内部实现），通过 [BoxWithConstraints] 同步获取宽度，
 * 避免首帧空白与 onGloballyPositioned 频繁重组。
 *
 * 注意：[onValueChange] 在拖动期间会高频触发，调用方应自行节流；持久化请在
 * [onValueChangeFinished] 中提交。
 *
 * @param value 当前值（在 [valueRange] 范围内）。
 * @param onValueChange 值变化回调（拖动与点击轨道均会触发，高频）。
 * @param enabled 是否启用（禁用时降透明度且不响应手势）。
 * @param valueRange 取值范围。
 * @param steps 离散步进点数量（不含两端），0 表示连续。
 * @param onValueChangeFinished 拖动/点击结束时触发，用于持久化提交。
 * @param colors 颜色配置，默认取自 [SliderDefaults.colors]。
 * @param thumbSize thumb 直径（视觉尺寸；按下时动画至 1.1 倍）。
 * @param trackHeight 轨道高度。
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    thumbSize: Dp = 20.dp,
    trackHeight: Dp = 4.dp,
) {
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val rangeSpan = (rangeEnd - rangeStart).coerceAtLeast(MIN_RANGE_SPAN)

    val interactionSource = rememberMutableInteractionSource()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // 固定 thumb 像素尺寸（拖动全程恒定，避免分母跳变）
    val thumbSizePx = with(density) { thumbSize.toPx() }
    // 按下时动画到 1.1 倍（M3 反馈）
    val animThumbSize by animateDpAsState(
        targetValue = if (enabled) thumbSize * 1.1f else thumbSize,
        label = "slider-thumb"
    )

    var isDragging by remember { mutableStateOf(false) }
    // 沉淀期标记：拖动/点击结束后屏蔽一段时间的外部 value 同步。
    // 解决手指抬起瞬间 thumb 抖动/回跳：调用方常在 onValueChange 里做异步写
    // （如 updateAnimSpeed(it.toInt()) → dataStore.edit，或 TTS 的 service IPC 往返），
    // 拖动期间累积的排队写入在 isDragging 变 false 后陆续回环 emit，
    // 每个都会触发 LaunchedEffect(value) 覆盖内部 progress，导致 thumb 在中间值之间跳动。
    var isSettling by remember { mutableStateOf(false) }
    // 沉淀计时 token：每次 release 自增以重启延时
    var settleToken by remember { mutableIntStateOf(0) }

    // progress 纯派生自 value（不 saveable），拖动期与沉淀期屏蔽外部同步以防跳变
    var progress by remember {
        mutableFloatStateOf(normalizeValue(value, rangeStart, rangeSpan))
    }
    LaunchedEffect(value, rangeStart, rangeSpan) {
        if (!isDragging && !isSettling) {
            progress = normalizeValue(value, rangeStart, rangeSpan)
        }
    }
    // 沉淀计时器：token 变化即取消旧计时并重启；延时结束后恢复外部同步。
    // 取 300ms：DataStore 写入虽快，但长拖动会累积大量排队 edit，
    // 需足够窗口让回环 emit 排空后再做最终同步（一次平滑吸附，而非多次抖动）。
    LaunchedEffect(settleToken) {
        if (settleToken > 0) {
            delay(SYNC_SETTLE_MS)
            isSettling = false
        }
    }

    val thumbColor = if (enabled) colors.thumbColor else colors.disabledThumbColor
    val activeTrackColor = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactiveTrackColor = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor

    val valuePercent = "${(normalizeValue(value, rangeStart, rangeSpan) * 100).roundToInt()}%"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize)
            .semantics {
                stateDescription = valuePercent
            }
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(thumbSizePx)
        val trackEndPx = (trackWidthPx - thumbSizePx).coerceAtLeast(MIN_RANGE_SPAN)

        // 统一入口：更新内部进度并对外回调。
        // snapBaseline=true 时把内部 raw 进度锚定到吸附点（用于点击，避免漂移）；
        // snapBaseline=false 时保留 raw 进度作为下次拖动的累加基线
        // （关键：若把基线吸附，小 delta 会被反复拽回吸附点 → 拖动不灵敏/滑不到端点）。
        // 对外 onValueChange 与 thumb 渲染始终用 snapped 值，保持 steps 离散语义。
        fun commitProgress(newProgress: Float, snapBaseline: Boolean) {
            val clamped = newProgress.coerceIn(0f, 1f)
            progress = if (snapBaseline) snapProgressToStep(clamped, steps) else clamped
            val snapped = snapProgressToStep(clamped, steps)
            val newValue = denormalizeValue(snapped, rangeStart, rangeSpan)
            if (!newValue.isNaN() && !newValue.isInfinite()) {
                onValueChange(newValue.coerceIn(rangeStart, rangeEnd))
            }
        }

        // draggableState 必须在 composable scope 创建；闭包通过局部变量捕获 trackEndPx
        val draggableState = rememberDraggableState { delta ->
            if (!enabled || trackEndPx <= 0f) return@rememberDraggableState
            val orientedDelta = if (isRtl) -delta else delta
            // 用 raw progress 累加 delta，不吸附基线 → 拖动连续灵敏，能跨过档位到达端点
            val newProgress = progress + orientedDelta / trackEndPx
            commitProgress(newProgress, snapBaseline = false)
        }

        val snappedProgress = snapProgressToStep(progress, steps)
        val activeTrackPx = trackEndPx * snappedProgress

        val seekToTouchX: (Float) -> Unit = { touchX ->
            val touchedProgress = (touchX / trackEndPx).coerceIn(0f, 1f)
            val oriented = if (isRtl) 1f - touchedProgress else touchedProgress
            // 点击是绝对位置 → 锚定到吸附点，下次拖动从吸附值起步
            commitProgress(oriented, snapBaseline = true)
        }

        // 整个可拖动 + 可点击的命中区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbSize)
                .pointerInput(enabled, trackEndPx, isRtl) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { offset ->
                            seekToTouchX(offset.x)
                            // 点击 seek 同样存在异步回环问题，进入沉淀期
                            isSettling = true
                            settleToken++
                            onValueChangeFinished?.invoke()
                        }
                    )
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    interactionSource = interactionSource,
                    enabled = enabled,
                    startDragImmediately = true,
                    onDragStopped = {
                        isDragging = false
                        // 拖动结束：把内部 raw baseline 锚定到当前吸附点，
                        // 让 thumb 视觉位置与下次拖动起点一致，避免累积漂移
                        progress = snapProgressToStep(progress, steps)
                        // 进入沉淀期，屏蔽随后到达的异步回环 value（见上注释）
                        isSettling = true
                        settleToken++
                        onValueChangeFinished?.invoke()
                    },
                    onDragStarted = { isDragging = true }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // inactive track（满宽）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .background(inactiveTrackColor, CircleShape)
            )
            // active track：宽度 = thumb 中心位置（thumbSize/2 + trackEnd*progress），
            // 使 active track 右端点恰好落在 thumb 中心，与 thumb 视觉融合。
            // （若用 +thumbSizePx 会超出 thumb 中心半个 thumb，与 thumb 错位）
            // RTL 下 progress=0 在最右，active track 应从右向左填充，
            // 通过 offset 把它推到 track 右侧。
            val activeTrackWidthPx = activeTrackPx + thumbSizePx / 2f
            val activeTrackOffsetDp = if (isRtl) {
                with(density) { (trackWidthPx - activeTrackWidthPx).toDp() }
            } else {
                0.dp
            }
            Box(
                modifier = Modifier
                    .offset(x = activeTrackOffsetDp)
                    .requiredSize(
                        width = with(density) { activeTrackWidthPx.toDp() },
                        height = trackHeight
                    )
                    .background(activeTrackColor, CircleShape)
            )
            // thumb（基于固定 thumbSizePx 定位，避免动画导致跳变）
            // 注意：不在 thumb 上用 minimumInteractiveComponentSize()——它会给 thumb 外层
            // 套一个 48dp 占位盒并使 thumb 在其中居中，叠加父 Box 的 CenterStart 对齐后，
            // thumb 会被推离 track 左边缘约半个占位盒的距离（progress=0 时无法贴左边缘）。
            // 触摸热区已由外层 fillMaxWidth() 的命中区覆盖整个轨道，thumb 无需额外占位。
            val thumbOffsetDp = with(density) { (trackEndPx * snappedProgress).toDp() }
            val resolvedOffset = if (isRtl) {
                with(density) { (trackWidthPx - thumbSizePx - trackEndPx * snappedProgress).toDp() }
            } else {
                thumbOffsetDp
            }
            Box(
                modifier = Modifier
                    .offset(x = resolvedOffset)
                    .requiredSize(animThumbSize)
                    .clip(CircleShape)
                    .background(thumbColor, CircleShape)
            )
        }
    }
}

// ---- 纯函数（便于单测）----

/** value 归一化到 [0,1]，除零兜底。NaN/Infinity 输入返回 0。 */
internal fun normalizeValue(value: Float, start: Float, span: Float): Float {
    if (value.isNaN() || value.isInfinite() || span <= 0f) return 0f
    return ((value - start) / span).coerceIn(0f, 1f)
}

/** progress [0,1] 反归一化回原始值。NaN/Infinity 输入返回 start。 */
internal fun denormalizeValue(progress: Float, start: Float, span: Float): Float {
    if (progress.isNaN() || progress.isInfinite()) return start
    return start + progress.coerceIn(0f, 1f) * span
}

/**
 * steps=0 → 原样返回（连续，夹紧到 [0,1]）。
 * steps>0 → 量化到最近离散点。区间被分为 steps+1 段，共 steps+2 个点（含两端）。
 */
internal fun snapProgressToStep(progress: Float, steps: Int): Float {
    if (steps <= 0) return progress.coerceIn(0f, 1f)
    val tickCount = steps + 2
    val stepCount = (tickCount - 1).coerceAtLeast(1)
    val raw = (progress.coerceIn(0f, 1f) * stepCount).roundToInt()
    return (raw.toFloat() / stepCount.toFloat()).coerceIn(0f, 1f)
}

private const val MIN_RANGE_SPAN = 1e-6f

/**
 * 拖动/点击结束后的"沉淀"时长：在此期间屏蔽外部 value 对内部 progress 的同步，
 * 让调用方异步写入管道（DataStore / service IPC）排空后再做一次平滑同步。
 * 过短 → 仍会抖动；过长 → 外部真正变化（如 TTS 进度自动推进）延迟反映。
 */
private const val SYNC_SETTLE_MS = 300L
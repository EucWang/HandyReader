package com.wxn.reader.presentation.shareQuoteCard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.presentation.shareQuoteCard.components.QuoteCard
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardStyle
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode
import com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardRatio
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteFontSize
import kotlin.math.min

/**
 * 书摘分享卡片全屏对话框（Surface 叠加在 ReaderView Box 上，对齐 TextToolbar 视觉模式）。
 *
 * 布局：垂直二分屏——上半弹性预览（半透明遮罩透出阅读页），下半控制面板（自适应高度，上限 55% 屏高）。
 *
 * 必须在 [MaterialTheme] 内包裹调用（P0-B 主题继承）。外层由 ReaderView 用
 * [androidx.compose.animation.AnimatedVisibility] 包裹实现渐显渐隐。
 */
@Composable
fun ShareQuoteCardDialog(
    viewModel: MainReadViewModel,
    onDismiss: () -> Unit,
    fontFamily: FontFamily?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.quoteCardState.collectAsState()
    val data = uiState.data

    // API<29 保存到相册需 WRITE_EXTERNAL_STORAGE 权限（API 29+ 用 MediaStore 无需权限）
    // 仅在用户点"保存到相册"时按需申请，避免分享被打断
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.renderAndSave()
        } else {
            // 拒绝授权：保存会失败，提前提示用户
            viewModel.setQuoteCardError(QuoteErrorCode.GALLERY_PERMISSION_DENIED)
        }
    }

    /**
     * 保存到相册入口：API<29 且无权限 → 申请；否则直接保存
     */
    fun onSaveToGallery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.renderAndSave()
        }
    }

    // 外层透明 Surface：不画背景，让 Box 内自画的半透明遮罩透出阅读页
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
        ) {
            // 半透明遮罩 + 拦截点击穿透（S1 + G1）。
            // 用 clickable 而非 pointerInput(consume)：clickable 只消费点击抬起，不影响子组件滚动；
            // pointerInput(awaitEachGesture + consume) 会消费 move 事件，导致 LazyRow/Column 滑不动。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
            // 整体统一避让系统导航栏，避免预览区底部透出阅读页条带
            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                // 上半：弹性预览区（padding 加在外层 modifier，S1：让 BoxWithConstraints 拿到扣除 padding 后的约束）
                PreviewArea(
                    data = data,
                    editableText = data?.defaultEditableText ?: "",
                    config = uiState.config,
                    coverBitmap = uiState.coverBitmap,
                    fontFamily = fontFamily,
                    isRendering = uiState.phase == QuotePhase.RENDERING,
                    modifier = Modifier.weight(1f).padding(16.dp)
                )

                // 下半：控制面板（高度自适应，上限 50% 屏高，尽量留空间给预览）
                ControlPanel(
                    viewModel = viewModel,
                    uiState = uiState,
                    data = data,
                    fontFamily = fontFamily,
                    onDismiss = onDismiss,
                    onSaveToGallery = { onSaveToGallery() },
                    context = context,
                    modifier = Modifier
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
                        .windowInsetsPadding(WindowInsets.ime)
                )
            }
        }
    }
}

// ==================== 预览区（graphicsLayer 缩放方案，所见即所得） ====================

@Composable
private fun PreviewArea(
    data: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData?,
    editableText: String,
    config: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardConfig,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    fontFamily: FontFamily?,
    isRendering: Boolean,
    modifier: Modifier = Modifier
) {
    if (data == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.err_data_missing))
        }
        return
    }
    val outerDensity = LocalDensity.current
    val cardPxW = config.ratio.width.toFloat()
    val cardPxH = config.ratio.height.toFloat()

    // BoxWithConstraints 同步拿到尺寸，避免 onSizeChanged 首帧空白（A2）
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val previewW = constraints.maxWidth.toFloat()
        val previewH = constraints.maxHeight.toFloat()
        // 取 min 保证完整显示不裁剪
        val scale = if (previewW > 0f && previewH > 0f) {
            min(previewW / cardPxW, previewH / cardPxH)
        } else 0f

        if (scale > 0f) {
            // 用外层 density 反算 px→dp，使 requiredSize 渲染成精确输出像素尺寸（A1）
            val cardWDp = (cardPxW / outerDensity.density).dp
            val cardHDp = (cardPxH / outerDensity.density).dp
            QuoteCard(
                data = data,
                editableText = editableText,
                config = config,
                coverBitmap = coverBitmap,
                fontFamily = fontFamily,
                modifier = Modifier
                    .requiredSize(width = cardWDp, height = cardHDp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            )
        }
        if (isRendering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.quote_rendering),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// ==================== 控制面板 ====================

@Composable
private fun ControlPanel(
    viewModel: MainReadViewModel,
    uiState: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardUiState,
    data: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData?,
    fontFamily: FontFamily?,
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        // 三层布局：标题栏（固定）+ 配置项（可滚动）+ Banner/按钮（固定）。
        // 主操作按钮始终固定在底部，符合 Material 对话框/底部表单惯例。
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题栏：标题 + 关闭按钮（固定，不参与滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quote_share_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }

            // 配置项滚动区：Style / Ratio / FontSize / Progress（含 data==null 兜底）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (data == null) {
                    Text(stringResource(R.string.err_data_missing))
                } else {
                    // Style 选择
                    Text(
                        stringResource(R.string.quote_style),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(QuoteCardStyle.entries) { style ->
                            FilterChip(
                                selected = uiState.config.style == style,
                                onClick = {
                                    if (style.isAvailableFor(data)) {
                                        viewModel.updateQuoteCardConfig(uiState.config.copy(style = style))
                                    } else {
                                        Toast.makeText(context, R.string.cover_not_available, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(style.displayName()) },
                                enabled = style.isAvailableFor(data)
                            )
                        }
                    }

                    // Ratio 选择
                    RatioSelector(
                        currentRatio = uiState.config.ratio,
                        onRatioSelected = { ratio ->
                            viewModel.updateQuoteCardConfig(uiState.config.copy(ratio = ratio))
                        }
                    )

                    // 字号选择（label 与切换按钮并排一行）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.font_size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FontSizeSelector(
                            currentSize = uiState.config.fontSize,
                            onSizeSelected = { size ->
                                viewModel.updateQuoteCardConfig(uiState.config.copy(fontSize = size))
                            }
                        )
                    }

                    // 显示进度（独立一行，需求 5）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.show_progress),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.config.showProgress,
                            onCheckedChange = { show ->
                                viewModel.updateQuoteCardConfig(uiState.config.copy(showProgress = show))
                            }
                        )
                    }
                }
            }

            // 固定底部区：成功/错误提示（紧贴按钮）+ Loading 文字 + 底部按钮。
            // 固定不滚动：保存结果始终可见，主操作始终可达。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 成功/错误提示，紧贴底部按钮之上
                if (uiState.phase == QuotePhase.SAVED) {
                    SuccessBanner(onDismiss = { viewModel.setQuoteCardPhase(QuotePhase.DIALOG_OPEN) })
                }
                uiState.errorCode?.let { errorCode ->
                    ErrorBanner(errorCode = errorCode, onDismiss = { viewModel.setQuoteCardError(null) })
                }

                // Loading 文字提示
                if (uiState.phase == QuotePhase.RENDERING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${stringResource(R.string.quote_rendering)} ${stringResource(R.string.quote_rendering_estimated)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveToGallery,
                        modifier = Modifier.weight(1f),
                        enabled = data != null && !uiState.isBusy
                    ) {
                        if (uiState.phase == QuotePhase.RENDERING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(stringResource(R.string.save_to_gallery))
                        }
                    }
                    Button(
                        onClick = { viewModel.renderAndShare() },
                        modifier = Modifier.weight(1f),
                        enabled = data != null && !uiState.isBusy
                    ) {
                        if (uiState.phase == QuotePhase.RENDERING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.share))
                        }
                    }
                }
            }
        }
    }
}

// ==================== 比例选择 ====================

@Composable
private fun RatioSelector(
    currentRatio: QuoteCardRatio,
    onRatioSelected: (QuoteCardRatio) -> Unit
) {
    val ratios = QuoteCardRatio.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ratios.forEach { ratio ->
            FilterChip(
                selected = currentRatio == ratio,
                onClick = { onRatioSelected(ratio) },
                label = { Text(ratio.displayName()) }
            )
        }
    }
}

// ==================== 字号选择 ====================

@Composable
private fun FontSizeSelector(
    currentSize: QuoteFontSize,
    onSizeSelected: (QuoteFontSize) -> Unit
) {
    // 单按钮循环切换：中 → 大 → 小 → 中 …
    // 图标 FormatSize（Aa）+ 当前档位文字 + SwapVert（垂直双向箭头，表示可切换）
    Surface(
        onClick = { onSizeSelected(currentSize.next()) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = currentSize.displayName(),
                style = MaterialTheme.typography.labelLarge
            )
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==================== 错误提示横幅 ====================

@Composable
private fun ErrorBanner(
    errorCode: QuoteErrorCode,
    onDismiss: () -> Unit
) {
    // 幂等守卫：防止 3 秒自动消失与用户手动点关闭重复触发 onDismiss
    var dismissed by remember(errorCode) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                errorCode.displayMessage(),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { if (!dismissed) { dismissed = true; onDismiss() } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
    // 轻微错误 3 秒后自动消失
    if (!errorCode.isSevere) {
        LaunchedEffect(errorCode) {
            kotlinx.coroutines.delay(3000)
            if (!dismissed) { dismissed = true; onDismiss() }
        }
    }
}

// ==================== 成功提示横幅 ====================

@Composable
private fun SuccessBanner(
    onDismiss: () -> Unit
) {
    // 幂等守卫：防止 3 秒自动消失与用户手动点关闭重复触发 onDismiss
    var dismissed by remember { mutableStateOf(false) }
    // 硬编码成功绿（与 ErrorBanner 的 errorContainer 红色形成成功/失败视觉对比；
    // 不用 primaryContainer，因其是 App 主色调、无"成功"语义保证）
    val bg = Color(0xFFE8F5E9)
    val fg = Color(0xFF2E7D32)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 路径含 "Pictures/HandyReader/"，强制 LTR 防 RTL 语言镜像（对齐 QuoteWatermark 做法）
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    stringResource(R.string.saved_to_gallery_with_path),
                    color = fg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = { if (!dismissed) { dismissed = true; onDismiss() } },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fg
                )
            }
        }
    }
    // 3 秒后自动消失
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        if (!dismissed) { dismissed = true; onDismiss() }
    }
}

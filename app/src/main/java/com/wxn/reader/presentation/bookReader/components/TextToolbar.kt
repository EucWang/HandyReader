package com.wxn.reader.presentation.bookReader.components

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.wxn.base.ext.sendToClip
import com.wxn.base.ext.toStringColor
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.domain.model.AnnotationType
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import androidx.compose.ui.graphics.Color as ComposeColor


@Composable
fun TextToolbar(
    navController: NavHostController,
    viewModel: MainReadViewModel,
    selectedText: String?,
    rect: Rect,
    onHighlight: (ComposeColor) -> Unit,
    onUnderline: (ComposeColor) -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit,
    onTranslatePanel: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    appPreferences: AppPreferences,
    selectedAnnotation: BookAnnotation?,
    onRemoveAnnotation: (BookAnnotation) -> Unit,
    colorHistory: List<ComposeColor>,
    onColorHistoryUpdated: (List<ComposeColor>) -> Unit,
    showColorSelectionPanel: Boolean
) {
    val context = LocalContext.current
    var showHighlightAction by remember { mutableStateOf(false) }
    var showUnderlineAction by remember { mutableStateOf(false) }
    var isPaletteVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf<ComposeColor?>(null) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidthDp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    val toolbarWidth = minOf(300.dp, screenWidthDp - 16.dp)
    val toolbarWidthPx = with(density) { toolbarWidth.toPx() }

    val toolbarHeight =
        if (showHighlightAction || showUnderlineAction || showColorSelectionPanel) 260f else 160f

    val toolbarVerticalPadding = with(density) { 24.dp.toPx() }

    val offsetX = calculateOffsetX(rect, screenWidthPx, toolbarWidthPx)
    val isNearTop = rect.top < toolbarHeight
    val targetOffsetY = if (isNearTop) {
        minOf(rect.bottom + toolbarVerticalPadding, screenHeightPx - toolbarHeight)
    } else {
        maxOf(rect.top - toolbarHeight - toolbarVerticalPadding, 0f)
    }

    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = ""
    )

    val controller = rememberColorPickerController()

    Box(
        modifier = Modifier.offset {
            IntOffset(
                offsetX.toInt(),
                animatedOffsetY.toInt()
            )
        }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                //不消耗事件，只记录按下和抬起的状态
                viewModel.setToolbarTouchActive(true)
                try {
                    while(true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                } finally {
                    viewModel.setToolbarTouchActive(false)
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.width(toolbarWidth),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column {
                AnimatedVisibility(visible = !showHighlightAction && !showUnderlineAction && !showColorSelectionPanel) {
                    ActionButtons(
                        onHighlight = {
                            showHighlightAction = true
                            viewModel.onShowTextAnnotationAction(AnnotationType.HIGHLIGHT)
                        },
                        onUnderline = {
                            showUnderlineAction = true
                            viewModel.onShowTextAnnotationAction(AnnotationType.UNDERLINE)
                        },
                        onNote = onNote,
                        onTranslate = onTranslatePanel,
                        onDictionary = {
                            viewModel.onDictionaryClicked()
                        },
                        onCopy = {
                            selectedText?.let {
                                context.sendToClip(it)
                            }
                        },
                        onSearch = onSearch,
                        onShare = onShare,
                    )
                }

                AnimatedVisibility(visible = showHighlightAction || showUnderlineAction || showColorSelectionPanel) {
                    ColorSelectionPanel(
                        selectedAnnotation = selectedAnnotation,
                        onRemoveAnnotation = onRemoveAnnotation,
                        onCustomColorClick = {
                            if (appPreferences.isPremium) isPaletteVisible = true
                            else {
                                navController.navigate(Screens.PremiumScreen.route)
                            }
                        },
                        onColorSelected = { color ->
                            selectedColor = color
                            handleColorSelection(
                                color,
                                showHighlightAction = showHighlightAction || (showColorSelectionPanel && selectedAnnotation?.type == AnnotationType.HIGHLIGHT),
                                showUnderlineAction = showUnderlineAction || (showColorSelectionPanel && selectedAnnotation?.type == AnnotationType.UNDERLINE),
                                onHighlight = {
                                    if (showColorSelectionPanel) {
                                        viewModel.updateAnnotation(
                                            selectedAnnotation!!.copy(
                                                color = color.toStringColor()
                                            )
                                        )
                                    } else {
                                        onHighlight(color)
                                    }
                                },
                                onUnderline = {
                                    if (showColorSelectionPanel) {
                                        viewModel.updateAnnotation(
                                            selectedAnnotation!!.copy(
                                                color = color.toStringColor()
                                            )
                                        )
                                    } else {
                                        onUnderline(color)
                                    }
                                },
                                colorHistory,
                                onColorHistoryUpdated
                            )
                        },
                        onBackClick = {
                            showHighlightAction = false
                            showUnderlineAction = false
                            if (showColorSelectionPanel) {
                                onDismiss()
                            }
                        },
                        colorHistory = colorHistory
                    )
                }
            }
        }
    }

    if (isPaletteVisible && appPreferences.isPremium) {
        ColorPickerOverlay(
            selectedColor = selectedColor,
            controller = controller,
            onColorChanged = { selectedColor = it },
            onColorSelected = { color ->
                handleColorSelection(
                    color,
                    showHighlightAction = showHighlightAction || (showColorSelectionPanel && selectedAnnotation?.type == AnnotationType.HIGHLIGHT),
                    showUnderlineAction = showUnderlineAction || (showColorSelectionPanel && selectedAnnotation?.type == AnnotationType.UNDERLINE),
                    onHighlight = {
                        if (showColorSelectionPanel) {
                            viewModel.updateAnnotation(
                                selectedAnnotation!!.copy(
                                    color = color.toStringColor()
                                )
                            )
                        } else {
                            onHighlight(color)
                        }
                    },
                    onUnderline = {
                        if (showColorSelectionPanel) {
                            viewModel.updateAnnotation(
                                selectedAnnotation!!.copy(
                                    color = color.toStringColor()
                                )
                            )
                        } else {
                            onUnderline(color)
                        }
                    },
                    colorHistory,
                    onColorHistoryUpdated
                )
                isPaletteVisible = false
            }
        )
    }

}


@Composable
fun ActionButtons(
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onNote: () -> Unit,
    onTranslate: () -> Unit,
    onDictionary: () -> Unit,
    onCopy: ()->Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val fadeWidth = 30.dp

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .fadeEdges(
                state = lazyListState,
                fadeWidth = fadeWidth,
                fadeColor = fadeColor,
            )
            .padding(horizontal = 4.dp),
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHighlight) {
                    Icon(Icons.Outlined.BorderColor, contentDescription = "Highlight")
                }
                VerticalDivider()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUnderline) {
                    Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Underline")
                }
                VerticalDivider()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNote) {
                    Icon(Icons.Outlined.EditNote, contentDescription = "Add Note")
                }
                VerticalDivider()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Content Copy")
                }
                VerticalDivider()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTranslate) {
                    Icon(Icons.Outlined.Translate, contentDescription = "Translate")
                }
                VerticalDivider()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDictionary) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Dictionary")
                }
                VerticalDivider()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search in Book")
                }

                VerticalDivider()
            }
        }
        item {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share Quote")
            }
        }
    }
}


@Composable
fun ColorSelectionPanel(
    selectedAnnotation: BookAnnotation?,
    onRemoveAnnotation: (BookAnnotation) -> Unit,
    onCustomColorClick: () -> Unit,
    onColorSelected: (ComposeColor) -> Unit,
    onBackClick: () -> Unit,
    colorHistory: List<ComposeColor>
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    selectedAnnotation?.let {
                        onRemoveAnnotation(it)
                    }
                },
                enabled = selectedAnnotation != null,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete Annotation",
                    modifier = Modifier.size(24.dp),
                    tint = if (selectedAnnotation != null) LocalContentColor.current else LocalContentColor.current.copy(
                        alpha = 0.38f
                    )
                )
            }
            IconButton(
                onClick = onCustomColorClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Colorize,
                    contentDescription = "Custom Color",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        DefaultColors(
            onColorSelected = onColorSelected,
            colorHistory = colorHistory
        )
    }
}

@Composable
fun DefaultColors(
    onColorSelected: (ComposeColor) -> Unit,
    colorHistory: List<ComposeColor>
) {
    val defaultColors = listOf(
        ComposeColor(0xFF4CAF50), // Material Green
        ComposeColor(0xFFFFEB3B), // Material Yellow
        ComposeColor(0xFF2196F3), // Material Blue
        ComposeColor(0xFFE91E63), // Material Pink
        ComposeColor(0xFF9C27B0), // Material Purple
    )

    val colorsToShow = if (colorHistory.isNotEmpty()) {
        (colorHistory + defaultColors).distinct().take(10)
    } else {
        defaultColors
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = Int.MAX_VALUE,
            key = { index -> index }
        ) { index ->
            val color = colorsToShow[index % colorsToShow.size]
            ColorButton(color = color, onClick = { onColorSelected(color) })
        }
    }
}

@Composable
fun ColorButton(color: ComposeColor, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ComposeColor.Transparent,
        border = BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        modifier = Modifier.size(32.dp),
        interactionSource = interactionSource
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Outer circle
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2
                )

                // Inner circle (slightly darker shade for depth)
                drawCircle(
                    color = color.copy(alpha = 0.85f),
                    radius = size.minDimension / 2.5f
                )
            }
        }
    }
}

fun calculateOffsetX(rect: Rect, screenWidth: Float, toolbarWidth: Float): Float {
    val center = rect.left + (rect.right - rect.left) / 2
    var offsetX = center - toolbarWidth / 2

    // Adjust if the toolbar goes beyond the left edge
    if (offsetX < 0) {
        offsetX = 0f
    }

    // Adjust if the toolbar goes beyond the right edge
    if (offsetX + toolbarWidth > screenWidth) {
        offsetX = screenWidth - toolbarWidth
    }

    return maxOf(0f, offsetX)
}

private fun handleColorSelection(
    color: ComposeColor,
    showHighlightAction: Boolean,
    showUnderlineAction: Boolean,
    onHighlight: (ComposeColor) -> Unit,
    onUnderline: (ComposeColor) -> Unit,
    colorHistory: List<ComposeColor>,
    onColorHistoryUpdated: (List<ComposeColor>) -> Unit
) {
    val updatedHistory = (listOf(color) + colorHistory).distinct().take(1)
    onColorHistoryUpdated(updatedHistory)
    if (showHighlightAction) onHighlight(color)
    if (showUnderlineAction) onUnderline(color)
}

@Composable
fun ColorPickerOverlay(
    selectedColor: ComposeColor?,
    controller: ColorPickerController,
    onColorChanged: (ComposeColor) -> Unit,
    onColorSelected: (ComposeColor) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.8f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize()
        ) {
            HsvColorPicker(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(350.dp)
                    .padding(10.dp),
                controller = controller,
                initialColor = selectedColor ?: ComposeColor.White,
                onColorChanged = { colorEnvelope -> onColorChanged(colorEnvelope.color) }
            )

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(selectedColor ?: ComposeColor.White)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onColorSelected(selectedColor ?: ComposeColor.White) }) {
                Text(stringResource(R.string.select))
            }
        }
    }
}

@Composable
fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(50.dp)
            .background(color = MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun Modifier.fadeEdges(
    state: LazyListState,
    fadeWidth: Dp,
    fadeColor: ComposeColor,
): Modifier {
    val density = LocalDensity.current
    val fadeWidthPx = with(density) { fadeWidth.toPx() }
    var hasSettled by remember { mutableStateOf(false) }

    LaunchedEffect(state.canScrollForward, state.canScrollBackward) {
        if (state.canScrollForward || state.canScrollBackward) {
            hasSettled = true
        }
    }

    val spec = if (hasSettled) tween<Float>(durationMillis = 200) else snap()

    val leftAlpha by animateFloatAsState(
        targetValue = if (state.canScrollBackward || !hasSettled) 1f else 0f,
        animationSpec =  spec,
        label = "leftFade"
    )
    val rightAlpha by animateFloatAsState(
        targetValue = if (state.canScrollForward || !hasSettled) 1f else 0f,
        animationSpec = spec,
        label = "rightFade"
    )

    return this.drawWithContent {
        drawContent()
        if (leftAlpha > 0.01f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(fadeColor, ComposeColor.Transparent)
                ),
                topLeft = Offset.Zero,
                size = Size(fadeWidthPx, size.height),
                alpha = leftAlpha
            )
        }
        if (rightAlpha > 0.01f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(ComposeColor.Transparent, fadeColor)
                ),
                topLeft = Offset(size.width - fadeWidthPx, 0f),
                size = Size(fadeWidthPx, size.height),
                alpha = rightAlpha
            )
        }
    }
}
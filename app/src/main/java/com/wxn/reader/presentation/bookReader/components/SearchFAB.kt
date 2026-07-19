package com.wxn.reader.presentation.bookReader.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchFAB(
    resultCount: Int,
    isSearching: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showHighlight: Boolean = false,
) {
    var isLongPressed by remember { mutableStateOf(false) }
    val longPressScale by animateFloatAsState(
        targetValue = if (isLongPressed) 0.9f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "longPressScale"
    )

    LaunchedEffect(isLongPressed) {
        if (isLongPressed) {
            delay(150)
            onLongClick()
            isLongPressed = false
        }
    }

    Box(contentAlignment = Alignment.Center) {
        val fadeAlpha by animateFloatAsState(
            targetValue = if (showHighlight) 1f else 0f,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "highlightFade"
        )

        if (showHighlight || fadeAlpha > 0.01f) {
            val infiniteTransition = rememberInfiniteTransition(label = "searchFabHighlight")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.28f,
                targetValue = 1.72f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        alpha = fadeAlpha
                        val scale = if (showHighlight) pulseScale else 1.28f
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (showHighlight) pulseAlpha else 0.4f
                        ),
                        CircleShape
                    )
            )
        }

        Surface(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = longPressScale
                    scaleY = longPressScale
                }
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick,
                    onLongClick = {
                        isLongPressed = true
                    },
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BadgedBox(
                    badge = {
                        if (resultCount > 0) {
                            Badge {
                                Text(
                                    text = if (resultCount > 99) "99+" else resultCount.toString(),
                                )
                            }
                        }
                    }
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

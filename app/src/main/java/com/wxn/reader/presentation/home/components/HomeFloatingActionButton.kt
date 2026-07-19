package com.wxn.reader.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.reader.R

@Composable
fun HomeFloatingActionButton(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onImportFileClick: () -> Unit,
    onAddScanDirClick: () -> Unit,
    onOpdsClick: () -> Unit,
    showHighlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            onExpandChange(false)
                            onImportFileClick()
                        },
                    ) {
                        Text(stringResource(R.string.import_file))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandChange(false)
                            onImportFileClick()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.FileOpen,
                            contentDescription = stringResource(R.string.content_desc_import_file)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            onExpandChange(false)
                            onAddScanDirClick()
                        },
                    ) {
                        Text(stringResource(R.string.add_scan_directory))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandChange(false)
                            onAddScanDirClick()
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.content_desc_add_scan_dir)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            onExpandChange(false)
                            onOpdsClick()
                        },
                    ) {
                        Text(stringResource(R.string.online_opds_library))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandChange(false)
                            onOpdsClick()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = stringResource(R.string.content_desc_opds_library)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        Box(contentAlignment = Alignment.Center) {
            val fadeAlpha by animateFloatAsState(
                targetValue = if (showHighlight) 1f else 0f,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "highlightFade"
            )

            if (showHighlight || fadeAlpha > 0.01f) {
                val infiniteTransition = rememberInfiniteTransition(label = "fabHighlight")
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
            val rotation by animateFloatAsState(
                targetValue = if (isExpanded) 45f else 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "fabRotation"
            )
            val animatedCorner by animateDpAsState(
                targetValue = if (isExpanded) 28.dp else 16.dp,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "fabCorner"
            )
            FloatingActionButton(
                shape = RoundedCornerShape(animatedCorner),
                onClick = { onExpandChange(!isExpanded) }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.content_desc_fab_expand),
                    modifier = Modifier.graphicsLayer(rotationZ = rotation)
                )
            }
        }
    }
}

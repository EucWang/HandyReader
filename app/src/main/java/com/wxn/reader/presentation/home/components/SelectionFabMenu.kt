package com.wxn.reader.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
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
fun SelectionFabMenu(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    allFavorited: Boolean,
    canEdit: Boolean,
    onEditBook: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToShelf: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
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
                if (canEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                onExpandChange(false)
                                onEditBook()
                            },
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        SmallFloatingActionButton(
                            onClick = {
                                onExpandChange(false)
                                onEditBook()
                            },
                        ) {
                            Icon(
                                Icons.Default.ModeEdit,
                                contentDescription = stringResource(R.string.content_desc_edit_book)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            onExpandChange(false)
                            onToggleFavorite()
                        },
                    ) {
                        Text(stringResource(R.string.favorite))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandChange(false)
                            onToggleFavorite()
                        },
                    ) {
                        Icon(
                            imageVector = if (allFavorited) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint = if (allFavorited) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary
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
                            onMoveToShelf()
                        },
                    ) {
                        Text(stringResource(R.string.cd_add_book_to_shelf))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandChange(false)
                            onMoveToShelf()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.AddToPhotos,
                            contentDescription = stringResource(R.string.cd_add_book_to_shelf)
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
                            onDelete()
                        },
                    ) {
                        Text(
                            stringResource(R.string.cd_delete_book),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandChange(false)
                            onDelete()
                        },
                    ) {
                        Icon(
                            tint = MaterialTheme.colorScheme.error,
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.cd_delete_book)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 90f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "fabRotation"
        )
        val animatedCorner by animateDpAsState(
            targetValue = if (isExpanded) 28.dp else 16.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "fabCorner"
        )
        FloatingActionButton(
            onClick = {
                if (isExpanded) {
                    onClose()
                } else {
                    onExpandChange(true)
                }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(animatedCorner),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_exit_selection_mode),
                modifier = Modifier.graphicsLayer(rotationZ = rotation)
            )
        }
    }
}

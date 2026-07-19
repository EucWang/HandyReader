package com.wxn.reader.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.Layout
import com.wxn.reader.presentation.home.HomeViewModel
import com.wxn.reader.presentation.sharedComponents.BookCover
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    openBook: (Book) -> Unit,
    updateLastOpened: (Book) -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    toggleSelection: (Book) -> Unit,
    isLoading: Boolean,
    appPreferences: AppPreferences,
    viewModel: HomeViewModel
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent
    var isClicked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (isClicked) 1.05f else 1f,
        animationSpec = tween(durationMillis = 100), label = ""
    )

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp


    // Calculate card dimensions based on screen size and grid count
    val cardWidth = (screenWidth / appPreferences.gridCount) - (20.dp / appPreferences.gridCount)
    val cardHeight = (cardWidth * 1.65f).coerceAtMost(screenHeight * 0.4f)


    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top

    ) {

        AnimatedVisibility(visible = appPreferences.showRating) {
            VerticalStarRating(
                book = book,
                onRatingChanged = {
                    viewModel.updateRating(book.id, it)
                },
                modifier = Modifier
                    .height(cardHeight * 0.5f)
            )
        }



        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
        ) {


            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .border(2.dp, borderColor, shape = RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = {
                            scope.launch {
                                if (selectionMode) {
                                    scope.launch {
                                        isClicked = true
                                        delay(100L)
                                        toggleSelection(book)
                                        isClicked = false
                                    }
                                } else if (!isLoading) {
                                    book.let {
                                        isClicked = true
                                        delay(100L)
                                        updateLastOpened(it)
                                        openBook(it)
                                    }
                                }
                            }
                        },
                        onLongClick = {
                            scope.launch {
                                isClicked = true
                                delay(100L)
                                toggleSelection(book)
                                isClicked = false
                            }
                        }
                    ),
                elevation = CardDefaults.cardElevation(if (selected) 20.dp else 8.dp)
            ) {

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            BookCover(
                                coverImage = book.coverImage,
                                title = book.title,
                                author = book.author,
                                isAudiobook = stringToFileType(book.fileType) == FileType.AUDIOBOOK,
                                modifier = Modifier.fillMaxSize(),
                                bookSource = book.source,
                                shape = RectangleShape,
                            )
                        }
                        AnimatedVisibility(visible = appPreferences.homeLayout != Layout.CoverOnly) {
                            Text(
                                text = book.title,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }



                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x80000000)) // 50% transparent gray
                        )
                    }
                }
            }


            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
            ) {
                AnimatedVisibility(
                    visible = appPreferences.showFileTypeLabel, //stringToFileType(book.fileType) == FileType.PDF &&
                ) {
                    FileTypeLabel(stringToFileType(book.fileType))
                }
            }


            // Favorite indicator
            if (book.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-3).dp, y = (3).dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .zIndex(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun FileTypeLabel(fileType: FileType) {
    if (fileType.showName().isNotEmpty()) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.secondary,
                    RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                )
                .padding(4.dp)

        ) {
            Text(
                text = fileType.showName(),
                color = MaterialTheme.colorScheme.onSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
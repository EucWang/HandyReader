package com.wxn.reader.presentation.bookReader.components.drawers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.Bookmark
import com.wxn.base.ext.toComposeColor
import com.wxn.base.ext.toStringColor
import com.wxn.reader.R
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Note
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.SafeScrollableTabRow
import com.wxn.reader.util.consumeClick
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReaderDrawer(
    viewModel: MainReadViewModel,
    isOpen: Boolean,
    onClose: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onUpdateNote: (Note) -> Unit,
    onRemoveNote: (Note) -> Unit,
    onChapterSelect: (BookChapter) -> Unit,

    onBookmarkClick: (Bookmark) -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,

    onRemoveAnnotation: (BookAnnotation) -> Unit,
    onUpdateAnnotation: (BookAnnotation) -> Unit,
    onClickAnnotation: (BookAnnotation) -> Unit,
) {
    val tabTitles = listOf(
        stringResource(R.string.chapters),
        stringResource(R.string.notes),
        stringResource(R.string.bookmarks),
        stringResource(R.string.highlights),
        stringResource(R.string.underlines)
    )
    val state = rememberPagerState(0) { 5 }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable {
                    onClose.invoke()
                }
        ) {
            ModalDrawerSheet(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                    .consumeClick()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.88f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tabTitles.get(state.currentPage),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close Notes")
                        }
                    }

                    SafeScrollableTabRow(
                        selectedTabIndex = state.currentPage,
                        totalTabCount = tabTitles.size,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = state.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        state.animateScrollToPage(index)
                                    }
                                },
                                text = { Text(title) }
                            )
                        }
                    }

                    HorizontalPager(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) { index ->
                        when (index) {
                            0 -> {
                                ChaptersPager(viewModel, onChapterSelect)
                            }

                            1 -> {
                                NotesPager(viewModel, onNoteClick, onUpdateNote, onRemoveNote)
                            }

                            2 -> {
                                BookmarksPager(viewModel, onBookmarkClick, onRemoveBookmark)
                            }

                            3 -> {
                                HighlightsPager(
                                    viewModel,
                                    0,
                                    onRemoveAnnotation,
                                    onUpdateAnnotation,
                                    onClickAnnotation
                                )
                            }

                            4 -> {
                                HighlightsPager(
                                    viewModel,
                                    1,
                                    onRemoveAnnotation,
                                    onUpdateAnnotation,
                                    onClickAnnotation
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightsPager(
    viewModel: MainReadViewModel,
    type: Int,
    onRemoveAnnotation: (BookAnnotation) -> Unit,
    onUpdateAnnotation: (BookAnnotation) -> Unit,
    onClickAnnotation: (BookAnnotation) -> Unit,
) {
    val filteredAnnotations by when (type) {
        0 ->  viewModel.highlights.collectAsStateWithLifecycle()
        1 ->  viewModel.underlines.collectAsStateWithLifecycle()
        else ->  viewModel.highlights.collectAsStateWithLifecycle()
    }
    viewModel.annotations.collectAsStateWithLifecycle()
    // Annotation List
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        items(filteredAnnotations.size) { index ->
            AnnotationItem(
                annotation = filteredAnnotations[index],
                onRemoveAnnotation = onRemoveAnnotation,
                onUpdateAnnotation = onUpdateAnnotation,
                onClickAnnotation = onClickAnnotation,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun BookmarksPager(
    viewModel: MainReadViewModel,
    onBookmarkClick: (Bookmark) -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        items(bookmarks) { bookmark ->
            BookmarkItem(
                bookmark = bookmark,
                onClick = { onBookmarkClick(bookmark) },
                onRemoveBookmark = { onRemoveBookmark(bookmark) },
            )
            Spacer(modifier = Modifier.height(12.dp))

        }
    }
}

@Composable
fun NotesPager(
    viewModel: MainReadViewModel,
    onNoteClick: (Note) -> Unit,
    onUpdateNote: (Note) -> Unit,
    onRemoveNote: (Note) -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    LazyColumn(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        items(notes) { note ->
            NoteItem(
                note = note,
                onClick = { onNoteClick(note) },
                onUpdateNote = { updatedNote -> onUpdateNote(updatedNote) },
                onRemoveNote = { onRemoveNote(note) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ChaptersPager(
    viewModel: MainReadViewModel,
    onChapterSelect: (BookChapter) -> Unit,
) {

    val tableOfContents by viewModel.showOutChapters.collectAsStateWithLifecycle()
    val curChapterIndex by viewModel.curChapterIndex.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        items(tableOfContents) { chapter ->
            ChapterItem(
                chapter = chapter,
                isCurrentChapter = chapter.chapterIndex == curChapterIndex,
                onClick = { onChapterSelect(chapter) }
            )
        }
    }
}

@Composable
fun ChapterItem(
    chapter: BookChapter,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        headlineContent = {
            Text(
                text = chapter.chapterName ?: stringResource(R.string.untitled_chapter),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (isCurrentChapter) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = if (isCurrentChapter) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Current Chapter",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null
    )
    HorizontalDivider()
}


@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit,
    onUpdateNote: (Note) -> Unit,
    onRemoveNote: (Note) -> Unit,
) {
    var isPaletteVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(note.color.toComposeColor()) }
    val controller = rememberColorPickerController()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(8.dp),
                clip = true
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            note.color.toComposeColor()
                        )
                        .clickable(onClick = {
                            isPaletteVisible = !isPaletteVisible
                        })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = note.note,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f) // Allocate remaining space to text
                )
                IconButton(
                    onClick = { onRemoveNote(note) },
                    modifier = Modifier.size(24.dp) // Adjust size if needed
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Note")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.selectedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(
                visible = isPaletteVisible,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        HsvColorPicker(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(350.dp)
                                .padding(10.dp),
                            controller = controller,
                            initialColor = selectedColor,
                            onColorChanged = { colorEnvelope ->
                                selectedColor = colorEnvelope.color
                            }
                        )

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(selectedColor)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val updatedNote = note.copy(color = selectedColor.toStringColor())
                                onUpdateNote(updatedNote)
                                isPaletteVisible = false
                            }
                        ) {
                            Text(stringResource(R.string.select))
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun BookmarkItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate =
        remember(bookmark.dateAndTime) { dateFormat.format(Date(bookmark.dateAndTime)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(8.dp),
                clip = true
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = bookmark.locatorInfo?.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f) // Allocate remaining space to text
                )
                IconButton(
                    onClick = { onRemoveBookmark(bookmark) },
                    modifier = Modifier.size(24.dp) // Adjust size if needed
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Note")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = com.wxn.reader.ui.theme.stringResource(
                    R.string.progression_format,
                    String.format(
                        Locale.getDefault(),
                        "%.1f%%",
                        (bookmark.locatorInfo?.progression ?: 0.0) * 100
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = com.wxn.reader.ui.theme.stringResource(R.string.date_format, formattedDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun AnnotationItem(
    annotation: BookAnnotation,
    onRemoveAnnotation: (BookAnnotation) -> Unit,
    onUpdateAnnotation: (BookAnnotation) -> Unit,
    onClickAnnotation: (BookAnnotation) -> Unit,
) {
    var isPaletteVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(annotation.color.toComposeColor()) }
    val controller = rememberColorPickerController()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(8.dp),
                clip = true
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = {
                onClickAnnotation.invoke(annotation)
            })
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(annotation.color.toComposeColor())
                .clickable(onClick = {
                    isPaletteVisible = !isPaletteVisible
                })
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = annotation.locatorInfo?.text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { onRemoveAnnotation(annotation) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Annotation")
        }
    }

    AnimatedVisibility(
        visible = isPaletteVisible,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.wrapContentSize()
            ) {
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(350.dp)
                        .padding(10.dp),
                    controller = controller,
                    initialColor = selectedColor,
                    onColorChanged = { colorEnvelope ->
                        selectedColor = colorEnvelope.color
                    }
                )

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(selectedColor)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val updatedAnnotation =
                            annotation.copy(color = selectedColor.toStringColor())
                        onUpdateAnnotation(updatedAnnotation)
                        isPaletteVisible = false
                    }
                ) {
                    Text(stringResource(R.string.select))
                }
            }
        }
    }
}
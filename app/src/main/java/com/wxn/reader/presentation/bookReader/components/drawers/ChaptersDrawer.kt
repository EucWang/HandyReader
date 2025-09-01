package com.wxn.reader.presentation.bookReader.components.drawers

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.bean.BookChapter
import com.wxn.reader.R
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.OnLaunchFlow
//import org.readium.r2.shared.publication.Link

//@Composable
//fun ChaptersDrawer(
//    isOpen: Boolean,
//    currentChapter: String,
//    tableOfContents: List<Link>,
//    onChapterSelect: (Link) -> Unit,
//    onClose: () -> Unit,
//) {
//    AnimatedVisibility(
//        visible = isOpen,
//        enter = slideInHorizontally(initialOffsetX = { -it }),
//        exit = slideOutHorizontally(targetOffsetX = { -it })
//    ) {
//        ModalDrawerSheet {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(16.dp)
//            ) {
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleLarge)
//                    IconButton(onClick = onClose) {
//                        Icon(Icons.Default.Close, contentDescription = "Close Chapters")
//                    }
//                }
//                Spacer(modifier = Modifier.height(16.dp))
//                LazyColumn {
//                    items(tableOfContents) { chapter ->
//                        ChapterItem(
//                            chapter = chapter,
//                            isCurrentChapter = chapter.title == currentChapter,
//                            onClick = { onChapterSelect(chapter) }
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun ChapterItem(
//    chapter: Link,
//    isCurrentChapter: Boolean,
//    onClick: () -> Unit
//) {
//    ListItem(
//        colors = ListItemDefaults.colors(
//            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
//        ) ,
//        headlineContent = {
//            Text(
//                text = chapter.title ?: stringResource(R.string.untitled_chapter),
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis,
//                style = if (isCurrentChapter) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
//                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
//            )
//        },
//        modifier = Modifier.clickable(onClick = onClick),
//        leadingContent = if (isCurrentChapter) {
//            {
//                Icon(
//                    Icons.Default.Check,
//                    contentDescription = "Current Chapter",
//                    tint = MaterialTheme.colorScheme.primary
//                )
//            }
//        } else null
//    )
//    HorizontalDivider()
//}



@Composable
fun ChaptersDrawer2(
    isOpen: Boolean,
    viewModel: MainReadViewModel,
    tableOfContents: List<BookChapter>,
    onChapterSelect: (BookChapter) -> Unit,
    onClose: () -> Unit,
) {
    val curChapterIndex by viewModel.curChapterIndex.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        ModalDrawerSheet {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close Chapters")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(tableOfContents) { chapter ->
                        ChapterItem(
                            chapter = chapter,
                            isCurrentChapter = chapter.chapterIndex == curChapterIndex,
                            onClick = { onChapterSelect(chapter) }
                        )
                    }
                }
            }
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
        ) ,
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
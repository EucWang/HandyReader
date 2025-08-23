package com.wxn.reader.presentation.bookReader.components.toolbars

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wxn.base.bean.Book
import com.wxn.reader.navigation.Screens
import com.wxn.reader.util.consumeClick

@Composable
fun TopToolbar(
    isBookmarked: Boolean,
    navController: NavController,
    book: Book?,
    bookTitle: String?,
    currentChapter: String,
    onChaptersClick: () -> Unit,
    onNotesDrawerToggle: () -> Unit,
    onBookmarkDrawerToggle: () -> Unit,
    onHighlightsDrawerToggle: () -> Unit,
    bookmark: () -> Unit,
    textToSpeech: () -> Unit,
    enableTts: Boolean,
    isTtsOn: Boolean,
) {

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    var isTtsToggled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp)
            .background(DrawerDefaults.modalContainerColor)
            .padding(bottom = 12.dp)
            .consumeClick()
//            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button
            IconButton(
                onClick = {
                    backDispatcher?.onBackPressed()
                }) {
                Icon(Icons.AutoMirrored.Sharp.ArrowBack,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "Back")
            }

            // Toolbar actions
            Row {
                IconButton(onClick = { onChaptersClick() }) {
                    Icon(Icons.AutoMirrored.Filled.List,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Chapters")
                }
                IconButton(onClick = { onNotesDrawerToggle() }) {
                    Icon(Icons.AutoMirrored.Outlined.StickyNote2,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Notes")
                }
                IconButton(onClick = { onHighlightsDrawerToggle() }) {
                    Icon(Icons.Outlined.BorderColor,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Highlights")
                }
                IconButton(onClick = { onBookmarkDrawerToggle() }) {
                    Icon(Icons.Outlined.BookmarkBorder,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Bookmarks")
                }
                IconButton(onClick = {
                    if (book != null) {
                        val encodedUri = Uri.encode(book.filePath)
                        navController.navigate(
                            Screens.BookDetailsScreen.route + "/${book.id}/${encodedUri}"
                        )
                    }
                }) {
                    Icon(Icons.Outlined.Info,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "About")
                }
            }
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (enableTts) {
                IconButton(
                    onClick = {
                        isTtsToggled = !isTtsOn
                        textToSpeech()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTtsToggled && !isTtsOn) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isTtsOn)
                                Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "text to speech"
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f).height(2.dp))
            }
            Column(
                modifier = Modifier
                    .weight(6f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                bookTitle?.let {
                    Text(
                        maxLines = 1,
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                        )
                }
                Text(
                    text = currentChapter,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    bookmark()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isBookmarked) Icons.Default.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "Bookmarks",
                )
            }
        }
    }
}
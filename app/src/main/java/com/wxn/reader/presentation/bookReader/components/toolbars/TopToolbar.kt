package com.wxn.reader.presentation.bookReader.components.toolbars

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    bookmark: () -> Unit,
) {

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(bottom = 12.dp, top = 6.dp)
            .consumeClick()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {}

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
                Icon(
                    Icons.AutoMirrored.Sharp.ArrowBack,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "Back"
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
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

            // Toolbar actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(onClick = {
                    if (book != null) {
                        val encodedUri = Uri.encode(book.filePath)
                        navController.navigate(
                            Screens.BookDetailsScreen.route + "/${book.id}/${encodedUri}"
                        )
                    }
                }) {
                    Icon(
                        Icons.Outlined.Info,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "About"
                    )
                }

                IconButton(
                    onClick = {
                        bookmark()
                    },
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
}
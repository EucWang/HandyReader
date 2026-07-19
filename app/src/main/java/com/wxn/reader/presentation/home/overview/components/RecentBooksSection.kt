package com.wxn.reader.presentation.home.overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxn.base.bean.Book
import com.wxn.reader.R
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.data.model.RecentToggle
import com.wxn.reader.presentation.sharedComponents.BookCover

@Composable
fun RecentBooksSection(
    books: List<Book>,
    currentToggle: RecentToggle,
    onToggleSelected: (RecentToggle) -> Unit,
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(currentToggle.labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Icon(
                Icons.Default.Menu,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "toggle menu",
                modifier = Modifier.clickable {
                    onToggleSelected(nextRecentToggle(currentToggle))
                })
        }

        Spacer(Modifier.height(8.dp))

        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .height(158.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(currentToggle.emptyStateRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = books, key = { it.id }) { book ->
                    RecentBookCover(book = book, onClick = { onOpenBook(book) })
                }
            }
        }
    }
}

@Composable
private fun RecentBookCover(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(158.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            BookCover(
                coverImage = book.coverImage,
                title = book.title,
                author = book.author,
                isAudiobook = stringToFileType(book.fileType) == FileType.AUDIOBOOK,
                modifier = Modifier.fillMaxSize(),
                bookSource = book.source,
                shape = RoundedCornerShape(8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val RecentToggle.labelRes: Int
    get() = when (this) {
        RecentToggle.READ -> R.string.recently_read
        RecentToggle.ADDED -> R.string.recently_added
        RecentToggle.FAVORITE -> R.string.recently_favorited
    }

private val RecentToggle.emptyStateRes: Int
    get() = when (this) {
        RecentToggle.READ -> R.string.no_recent_read_books
        RecentToggle.ADDED -> R.string.no_recent_added_books
        RecentToggle.FAVORITE -> R.string.no_favorite_books
    }

private fun nextRecentToggle(current: RecentToggle): RecentToggle {
    return when (current) {
        RecentToggle.READ -> RecentToggle.ADDED
        RecentToggle.ADDED -> RecentToggle.FAVORITE
        RecentToggle.FAVORITE -> RecentToggle.READ
    }
}
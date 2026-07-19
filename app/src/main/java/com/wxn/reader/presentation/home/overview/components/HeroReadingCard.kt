package com.wxn.reader.presentation.home.overview.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.wxn.base.bean.Book
import com.wxn.reader.R
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.presentation.sharedComponents.BookCover
import com.wxn.reader.presentation.statistics.components.formatReadingTimeShort

@Composable
fun HeroReadingCard(
    book: Book?,
    onOpenBook: (Book) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (book == null) {
            HeroEmptyState(onImportClick = onImportClick)
        } else {
            HeroContent(book = book, onOpenBook = onOpenBook)
        }
    }
}

@Composable
private fun HeroEmptyState(onImportClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_first_book_desc),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onImportClick) {
            Text(stringResource(R.string.open_first_book))
        }
    }
}

@Composable
private fun HeroContent(book: Book, onOpenBook: (Book) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            coverImage = book.coverImage,
            title = book.title,
            author = book.author,
            isAudiobook = stringToFileType(book.fileType) == FileType.AUDIOBOOK,
            modifier = Modifier
                .width(96.dp)
                .height(158.dp),
            bookSource = book.source,
            shape = RoundedCornerShape(8.dp),
            showText = true,
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            HeroProgressSection(book = book)

            Spacer(Modifier.height(12.dp))

            HeroActionButton(book = book, onOpenBook = onOpenBook)
        }
    }
}

@Composable
private fun HeroProgressSection(book: Book) {
    val isFinished = book.readingStatus == ReadingStatus.FINISHED.ordinal
    if (isFinished) {
        Text(
            text = stringResource(R.string.finished),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }

    val isAudiobook = stringToFileType(book.fileType) == FileType.AUDIOBOOK
    if (isAudiobook) {
        val readingTimeText = formatReadingTimeShort(book.readingTime)
        val durationText = book.duration?.let { formatReadingTimeShort(it) }
        val progressText = if (durationText != null) "$readingTimeText / $durationText" else readingTimeText
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (book.progress / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            text = "${book.progress.toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (book.progress / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HeroActionButton(book: Book, onOpenBook: (Book) -> Unit) {
    val isFinished = book.readingStatus == ReadingStatus.FINISHED.ordinal
    val isAudiobook = stringToFileType(book.fileType) == FileType.AUDIOBOOK
    val lastOpenedExists = book.lastOpened != null

    val buttonText = when {
        isFinished -> if (isAudiobook) R.string.listen_again else R.string.reread
        lastOpenedExists -> if (isAudiobook) R.string.continue_listening else R.string.continue_reading
        else -> if (isAudiobook) R.string.continue_listening else R.string.continue_reading
    }

    Button(onClick = { onOpenBook(book) }) {
        Text(stringResource(buttonText))
    }
}

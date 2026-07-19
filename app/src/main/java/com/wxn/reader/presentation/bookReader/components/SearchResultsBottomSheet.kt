package com.wxn.reader.presentation.bookReader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wxn.base.bean.Locator
import com.wxn.reader.R
import com.wxn.reader.domain.use_case.search.SearchResultItem
import com.wxn.reader.domain.use_case.search.SearchInBookUseCase.SearchProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsBottomSheet(
    query: String,
    progress: SearchProgress,
    returnLocator: Locator?,
    returnChapterName: String,
    onResultClick: (SearchResultItem) -> Unit,
    onReturnClick: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onMinimize,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            SearchSheetHeader(
                query = query,
                progress = progress,
                returnLocator = returnLocator,
                returnChapterName = returnChapterName,
                onReturnClick = onReturnClick,
                onClose = onClose,
            )

            if (progress.results.isEmpty() && !progress.isComplete) {
                SearchingEmptyState(progress = progress)
            } else if (progress.results.isEmpty() && progress.isComplete) {
                NoResultsState(query = query)
            } else {
                ResultsList(
                    results = progress.results,
                    isTruncated = progress.isTruncated,
                    onItemClick = onResultClick,
                )
            }
        }
    }
}

@Composable
private fun SearchSheetHeader(
    query: String,
    progress: SearchProgress,
    returnLocator: Locator?,
    returnChapterName: String,
    onReturnClick: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (returnLocator != null) {
            TextButton(
                onClick = onReturnClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = returnChapterName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "\"${query}\"",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (progress.results.isNotEmpty()) {
            Text(
                text = stringResource(R.string.search_results_count, progress.results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (!progress.isComplete && progress.totalChapters > 0) {
        LinearProgressIndicator(
            progress = { progress.searchedChapters.toFloat() / progress.totalChapters },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            text = stringResource(
                R.string.searching_chapters,
                progress.searchedChapters,
                progress.totalChapters
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    if (progress.isTruncated) {
        Text(
            text = stringResource(R.string.search_results_too_many),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SearchingEmptyState(progress: SearchProgress) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.searching_chapters,
                    progress.searchedChapters,
                    progress.totalChapters
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.search_no_results_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.search_no_results, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsList(
    results: List<SearchResultItem>,
    isTruncated: Boolean,
    onItemClick: (SearchResultItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(
            items = results,
            key = {
                "${it.locator.chapterIndex}-${it.locator.startParagraphIndex}-${it.locator.startTextOffset}-${it.locator.endTextOffset}"
            },
        ) { result ->
            SearchResultRow(
                result = result,
                onClick = { onItemClick(result) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResultItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text(
                text = result.chapterName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (result.matchRanges.size > 1) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.search_match_count, result.matchRanges.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildMatchAnnotatedString(result),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
        )
    }
}

@Composable
private fun buildMatchAnnotatedString(result: SearchResultItem) = buildAnnotatedString {
    val normalStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val highlightStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.primaryContainer,
    )

    var lastEnd = 0
    for (range in result.matchRanges) {
        if (range.start > lastEnd) {
            withStyle(normalStyle) {
                append(result.contextText.substring(lastEnd, range.start))
            }
        }
        withStyle(highlightStyle) {
            append(result.contextText.substring(range.start, range.end))
        }
        lastEnd = range.end
    }
    if (lastEnd < result.contextText.length) {
        withStyle(normalStyle) {
            append(result.contextText.substring(lastEnd))
        }
    }
}

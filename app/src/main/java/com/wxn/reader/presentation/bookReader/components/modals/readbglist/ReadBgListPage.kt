package com.wxn.reader.presentation.bookReader.components.modals.readbglist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.domain.model.ReadBgData
import com.wxn.reader.presentation.mainReader.MainReadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadBgListPage(
    readViewModel: MainReadViewModel,
    bgListViewModel: ReadBgListViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {}
) {

    val uiState by bgListViewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates by bgListViewModel.downloadStates.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()
    val readPrefs by bgListViewModel.readerPreferences.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded }
    )

    fun onBgImageClick(item: ReadBgData) {
        Logger.d("onBgImageClick::${item.id}")
        readPrefs?.let { prefs ->
            val newPrefs = prefs.copy(backgroundImage = item.id)
            readViewModel.updateReaderPreferences(newPrefs)
            bgListViewModel.setAsBackground(newPrefs, item)
        }
    }

    fun onDownloadClick(item: ReadBgData) {
        Logger.d("onDownloadClick::${item.id}")
        readPrefs?.let { prefs ->
            bgListViewModel.downloadBgImage(item) {
                val newPrefs = prefs.copy(backgroundImage = item.id)
                readViewModel.updateReaderPreferences(newPrefs)
                bgListViewModel.setAsBackground(newPrefs, item)
            }
        }
    }

    // Load more when reaching end
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= uiState.bgList.size - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            bgListViewModel.loadMoreImages()
        }
    }

    ModalBottomSheet(
        shape = BottomSheetDefaults.HiddenShape,
        onDismissRequest = onDismiss,
        dragHandle = null,
        modifier = Modifier.padding(top = 32.dp),
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.change_reading_background),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.bgList.isEmpty() && uiState.error.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.bgList.toList(),
                        key = { item -> item.id },
                        contentType = { item -> "ReadBg" }) { item ->
                        TextureItemCard(
                            readBg = item,
                            isDownloaded = uiState.downloadedIds.contains(item.id),
                            isCurrentBackground = uiState.currentBgTextureId == item.id,
                            downloadState = downloadStates[item.id],
                            onClick = {
                                if (uiState.downloadedIds.contains(item.id)) {
                                    onBgImageClick(item)
                                } else {
                                    onDownloadClick(item)
                                }
                            },
                            onDownloadClick = { onDownloadClick(item) }
                        )
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        when {
                            uiState.isLoadingMore -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }

                            uiState.currentPage >= uiState.totalPages && uiState.bgList.isNotEmpty() -> {
                                Text(
                                    text = stringResource(R.string.no_more_data),  // 或直接用 "没有更多了"
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
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
private fun TextureItemCard(
    readBg: ReadBgData,
    isDownloaded: Boolean,
    isCurrentBackground: Boolean,
    downloadState: DownloadState?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            AsyncImage(
                model = readBg.thumbnailUrl,
                contentDescription = readBg.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7072f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            // Download indicator
            if (downloadState?.isDownloading == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Downloaded badge
            if (isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(
                            if (isCurrentBackground)
                            MaterialTheme.colorScheme.onPrimary
                            else
                            MaterialTheme.colorScheme.onSurface,
                            CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentBackground) Icons.Filled.Check else Icons.Filled.FileDownloadDone,
                        contentDescription = "Downloaded",
                        tint = if (isCurrentBackground) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Download button (show when not downloaded and not downloading)
            if (!isDownloaded && downloadState?.isDownloading != true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable(onClick = onDownloadClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
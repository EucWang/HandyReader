package com.wxn.reader.presentation.opds

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.wxn.reader.util.NetImage
import com.wxn.reader.R
import com.wxn.reader.data.model.opds.EntryAction
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.model.opds.OpdsLink
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.opds.viewmodels.OpdsBookViewModel
import com.wxn.reader.presentation.opds.viewmodels.OpdsBrowseViewModel
import com.wxn.reader.util.WidthSpace
import com.wxn.reader.util.download.DownloadState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsBookDetailSheet(
    entry: OpdsEntry,
    browseViewModel: OpdsBrowseViewModel,
    onDismiss: () -> Unit,
    bookViewModel: OpdsBookViewModel = hiltViewModel()
) {
    val uiState by bookViewModel.uiState.collectAsState()
    val navController = LocalNavController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current

    val safTreePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            bookViewModel.onFirstDownloadChoiceSafTree(it.toString())
        }
    }

    LaunchedEffect(entry.id) {
        bookViewModel.setEntry(entry)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (uiState.showCorruptedDialog) {
            CorruptedFileDialog(
                onRedownload = { bookViewModel.retryAfterCorruption() },
                onDismiss = { bookViewModel.dismissCorruptedDialog() }
            )
        }
        if (uiState.showFirstDownloadDialog) {
            FirstDownloadLocationDialog(
                onAppInternal = {
                    bookViewModel.onFirstDownloadChoiceAppInternal()
                },
                onPickSafTree = {
                    safTreePickerLauncher.launch(null)
                },
                onDismiss = {
                    bookViewModel.dismissFirstDownloadDialog()
                }
            )
        }
        uiState.entry?.let { detailEntry ->
            Box(
                modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CoverImage(detailEntry.fullCoverUrl, modifier = Modifier.weight(0.4f))
                            WidthSpace(6.dp)
                            TitleAndAuthors(detailEntry, modifier = Modifier.weight(0.6f))
                        }
                    }
                    item { MetaInfo(detailEntry) }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(6.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            HtmlContent(detailEntry)
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .height(60.dp)
                                .fillMaxWidth()
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.surface)
                ) {
                    ActionBottomBar(
                        uiState = uiState,
                        entry = detailEntry,
                        bookViewModel = bookViewModel,
                        browseViewModel = browseViewModel,
                        onReadClick = {
                            bookViewModel.navigateToReader()?.let {
                                navController.navigate(it)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBottomBar(
    uiState: OpdsBookUiState,
    entry: OpdsEntry,
    bookViewModel: OpdsBookViewModel,
    browseViewModel: OpdsBrowseViewModel,
    onReadClick: () -> Unit
) {
    val action = entry.primaryAction
    val downloadState = uiState.downloadState
    val isDownloading =
        downloadState?.isDownloading == true || downloadState?.isPendingDownload == true
    val isImporting = uiState.isImporting
    val importError = uiState.error

    when (action) {
        is EntryAction.Download -> {
            val isBorrow =
                entry.borrowLink != null && entry.freeAcquisitionLinks.isEmpty() && entry.buyLink == null
            DownloadBar(
                uiState = uiState,
                entry = entry,
                isDownloading = isDownloading,
                isImporting = isImporting,
                downloadState = downloadState,
                onDownloadClick = { if (isBorrow) bookViewModel.borrowBook() else bookViewModel.downloadBook() },
                onFormatSelected = { bookViewModel.selectFormat(it) },
                onReadClick = onReadClick,
                onCancelClick = { bookViewModel.cancelDownload() },
                buttonText = if (isBorrow) stringResource(R.string.opds_borrow) else stringResource(
                    R.string.opds_download
                )
            )
        }

        is EntryAction.Buy -> {
            BuyBar(
                entry = entry,
                isDownloading = isDownloading,
                isImporting = isImporting,
                downloadState = downloadState,
                importError = importError,
                onBuyClick = { browseViewModel.requestBuyConfirmation(action.link.href) },
                onReadClick = onReadClick,
                onCancelClick = { bookViewModel.cancelDownload() }
            )
        }

        is EntryAction.DownloadSample -> {
            SampleBar(
                isDownloading = isDownloading,
                isImporting = isImporting,
                downloadState = downloadState,
                importError = importError,
                onDownloadClick = { bookViewModel.downloadSample() },
                onReadClick = onReadClick,
                onCancelClick = { bookViewModel.cancelDownload() }
            )
        }

        is EntryAction.OpenInBrowser -> {
            OpenInBrowserBar(
                onOpenClick = { browseViewModel.openHtmlAcquisitionLink(action.link.href) }
            )
        }

        is EntryAction.Unavailable -> {}
    }
}

@Composable
private fun DownloadBar(
    uiState: OpdsBookUiState,
    entry: OpdsEntry,
    isDownloading: Boolean,
    isImporting: Boolean,
    downloadState: DownloadState?,
    onDownloadClick: () -> Unit,
    onFormatSelected: (Int) -> Unit,
    onReadClick: () -> Unit,
    onCancelClick: () -> Unit,
    buttonText: String
) {
    val acquisitionLinks = entry.acquisitionLinks
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var expanded by remember { mutableStateOf(false) }
        val selectedIndex = uiState.selectedFormatIndex.coerceIn(0, acquisitionLinks.lastIndex)

        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(45.dp),
                enabled = !isDownloading && !isImporting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = formatLabel(acquisitionLinks[selectedIndex]),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                acquisitionLinks.forEachIndexed { index, link ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                formatLabel(link),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        },
                        onClick = {
                            expanded = false
                            onFormatSelected(index)
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            ActionButton(
                isDownloading = isDownloading,
                isImporting = isImporting,
                downloadState = downloadState,
                importedBookId = uiState.importedBookId,
                importError = uiState.error,
                onActionClick = onDownloadClick,
                onReadClick = onReadClick,
                actionText = buttonText,
                onCancelClick = onCancelClick
            )
        }
    }
}

@Composable
private fun BuyBar(
    entry: OpdsEntry,
    isDownloading: Boolean,
    isImporting: Boolean,
    downloadState: DownloadState?,
    importError: String?,
    onBuyClick: () -> Unit,
    onReadClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ActionButton(
                isDownloading = isDownloading,
                isImporting = isImporting,
                downloadState = downloadState,
                importedBookId = 0L,
                importError = importError,
                onActionClick = onBuyClick,
                onReadClick = onReadClick,
                actionText = entry.price?.let { stringResource(R.string.opds_buy, it) }
                    ?: stringResource(R.string.opds_buy, ""),
                onCancelClick = onCancelClick
            )
        }
    }
}

@Composable
private fun SampleBar(
    isDownloading: Boolean,
    isImporting: Boolean,
    downloadState: DownloadState?,
    importError: String?,
    onDownloadClick: () -> Unit,
    onReadClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ActionButton(
                isDownloading = isDownloading,
                isImporting = isImporting,
                downloadState = downloadState,
                importedBookId = 0L,
                importError = importError,
                onActionClick = onDownloadClick,
                onReadClick = onReadClick,
                actionText = stringResource(R.string.opds_download_sample),
                onCancelClick = onCancelClick
            )
        }
    }
}

@Composable
private fun OpenInBrowserBar(
    onOpenClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = onOpenClick,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opds_open_in_browser))
            }
        }
    }
}

@Composable
private fun ActionButton(
    isDownloading: Boolean,
    isImporting: Boolean,
    downloadState: DownloadState?,
    importedBookId: Long,
    importError: String?,
    onActionClick: () -> Unit,
    onReadClick: () -> Unit,
    actionText: String,
    onCancelClick: () -> Unit = {}
) {
    when {
        downloadState?.isDownloading == true -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f).height(45.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                    )

                    Spacer(Modifier.width(2.dp))
                    Text("${(downloadState.progress * 100).toInt()}%",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)

                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.opds_cancel_download),
                        modifier = Modifier.size(32.dp).clickable{
                            onCancelClick.invoke()
                        }.padding(6.dp)
                    )
                }
            }
        }

        downloadState?.isPendingDownload == true -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f).height(45.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.opds_waiting),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)

                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.opds_cancel_download),
                        modifier = Modifier.size(32.dp).clickable{
                            onCancelClick.invoke()
                        }.padding(6.dp)
                    )
                }
            }
        }

        isImporting -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opds_importing))
            }
        }

        importedBookId > 0 -> {
            Button(
                onClick = onReadClick,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opds_read_book))
            }
        }

        downloadState?.error != null -> {
            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opds_retry))
            }
        }

        importError != null -> {
            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.opds_retry))
            }
        }

        else -> {
            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun CoverImage(coverUrl: String?, modifier: Modifier = Modifier) {
    if (coverUrl != null) {
        NetImage(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            url = coverUrl,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TitleAndAuthors(entry: OpdsEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.authors.isNotEmpty()) {
            Text(
                text = entry.authors.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetaInfo(entry: OpdsEntry) {
    val metaParts = mutableListOf<String>()
    if (!entry.language.isNullOrBlank()) metaParts.add(entry.language!!)
    if (!entry.published.isNullOrBlank()) metaParts.add(entry.published!!)
    if (entry.categories.isNotEmpty()) metaParts.add(entry.categories.take(3).joinToString(", "))

    if (metaParts.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            metaParts.forEach { meta ->
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlContent(entry: OpdsEntry) {
    val htmlText = entry.content ?: entry.summary
    if (htmlText != null) {
        val textColor = MaterialTheme.colorScheme.onSurface
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    setTextColor(textColor.toArgb())
                    textSize = 14f
                    movementMethod = LinkMovementMethod.getInstance()
                }
            },
            update = { textView ->
                textView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    android.text.Html.fromHtml(htmlText)
                }
            }
        )
    } else {
        Text(
            text = stringResource(R.string.opds_no_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatLabel(link: OpdsLink, full: Boolean = false): String {
    val formatName = when {
        link.type?.contains("epub") == true -> "EPUB"
        link.type?.contains("pdf") == true -> "PDF"
        link.type?.contains("mobipocket") == true -> "MOBI"
        link.type?.contains("azw") == true -> "AZW3"
        link.type?.contains("fb2") == true -> "FB2"
        link.type?.contains("mp3") == true -> "MP3"
        else -> link.type?.substringAfter("/")?.uppercase() ?: "Unknown"
    }
    val details = if (!full) {
        link.title.orEmpty().trim().ifEmpty { formatFileSize(link.length) }
    } else {
        val substring = StringBuffer(link.title.orEmpty().trim())
        val sizeInfo = formatFileSize(link.length)
        if (substring.isNotEmpty() && sizeInfo.isNotEmpty()) {
            substring.append(" - ")
        }
        substring.append(sizeInfo)
        substring.toString()
    }
    return if (details.isNotEmpty()) {
        if (details.contains(formatName, ignoreCase = true)) {
            details
        } else {
            "$formatName ($details)"
        }
    } else formatName
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return ""
    return when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1fMB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.1fKB", bytes / 1_000.0)
        else -> "${bytes}B"
    }
}

@Composable
private fun FirstDownloadLocationDialog(
    onAppInternal: () -> Unit,
    onPickSafTree: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Storage, contentDescription = null)
        },
        title = { Text(stringResource(R.string.opds_first_download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.opds_first_download_message))
                TextButton(
                    onClick = onAppInternal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.opds_location_app_internal),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.opds_location_app_internal_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onPickSafTree,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.opds_location_saf_tree),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.opds_location_saf_tree_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.opds_cancel))
            }
        }
    )
}

@Composable
private fun CorruptedFileDialog(
    onRedownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.opds_download_corrupted_title)) },
        text = { Text(stringResource(R.string.opds_download_corrupted_message)) },
        confirmButton = {
            TextButton(onClick = onRedownload) {
                Text(stringResource(R.string.opds_redownload))
            }
        }
    )
}
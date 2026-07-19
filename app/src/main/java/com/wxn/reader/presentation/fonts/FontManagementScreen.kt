package com.wxn.reader.presentation.fonts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.reader.R
import com.wxn.reader.domain.use_case.font.FontListItem
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.fonts.viewmodels.FontManagementViewModel
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.util.download.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontManagementScreen(
    viewModel: FontManagementViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()

    var fontToDelete by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    if (fontToDelete != null) {
        val fontItem = uiState.fonts.find { it.catalogItem.id == fontToDelete }
        AlertDialog(
            onDismissRequest = { fontToDelete = null },
            title = { Text(stringResource(R.string.delete_font)) },
            text = { Text(stringResource(R.string.delete_font_confirm, fontItem?.catalogItem?.displayName.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    fontToDelete?.let { viewModel.deleteFont(it) }
                    fontToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fontToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(R.string.font_management)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(
                    items = uiState.fonts,
                    key = { it.catalogItem.id }
                ) { fontItem ->
                    FontCard(
                        fontItem = fontItem,
                        isExpanded = uiState.expandedFontId == fontItem.catalogItem.id,
                        isSelected = viewModel.isFontSelected(fontItem.catalogItem.id),
                        currentVariant = if (viewModel.isFontSelected(fontItem.catalogItem.id)) uiState.currentVariant else null,
                        downloadState = downloadStates[fontItem.catalogItem.id],
                        onDownload = { viewModel.downloadFont(fontItem.catalogItem.id) },
                        onDelete = { fontToDelete = fontItem.catalogItem.id },
                        onToggleExpand = { viewModel.toggleExpand(fontItem.catalogItem.id) },
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun FontCard(
    fontItem: FontListItem,
    isExpanded: Boolean,
    isSelected: Boolean,
    currentVariant: String?,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
//    onSelect: (String) -> Unit,
    onToggleExpand: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
//                    .clickable(enabled = fontItem.isDownloaded) { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fontItem.catalogItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = fontItem.catalogItem.category,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = fontItem.catalogItem.language,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    when {
                        downloadState?.isPendingDownload == true -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = ProgressIndicatorDefaults.circularDeterminateTrackColor
                            )
                        }
                        downloadState?.isDownloading == true -> {
                            downloadState.progress.let { progress ->
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = ProgressIndicatorDefaults.circularDeterminateTrackColor
                                )
                            }
                        }

                        downloadState?.error != null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.download_failed_status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = onDownload) {
                                    Icon(
                                        Icons.Filled.CloudDownload,
                                        contentDescription = stringResource(R.string.cd_retry),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        fontItem.isDownloaded -> {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                        }
                        else -> {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    Icons.Filled.CloudDownload,
                                    contentDescription = stringResource(R.string.cd_download),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (fontItem.totalVariants > 1) {
                        IconButton(onClick = onToggleExpand) {
                            Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand)
                            )
                        }
                    }
                }
            }

//            if (fontItem.isDownloaded && fontItem.totalVariants > 1) {
            if (fontItem.totalVariants > 1) {
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            fontItem.catalogItem.variants.forEach { variantItem ->
                                val variantSelected = currentVariant == variantItem.variant
                                FilterChip(
                                    selected = variantSelected,
                                    onClick = {},
                                    label = { Text(variantItem.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
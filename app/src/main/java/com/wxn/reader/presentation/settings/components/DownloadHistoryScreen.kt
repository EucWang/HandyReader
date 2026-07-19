package com.wxn.reader.presentation.settings.components


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wxn.reader.R
import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.domain.model.DownloadStatus
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.settings.viewmodels.DownloadHistoryViewModel
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    viewModel: DownloadHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(R.string.download_history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.error_loading_history))
                    Text(uiState.error ?: stringResource(R.string.unknown_error))
                    Button(onClick = { viewModel.loadDownloadHistory() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else if (uiState.downloadHistory.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.no_download_history))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(uiState.downloadHistory) { item ->
                        DownloadHistoryItem(item)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadHistoryItem(history: DownloadHistoryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = history.fileName ?: history.fileId,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = getDownloadStatusRes(history.status),
                style = MaterialTheme.typography.bodySmall
            )
            if (history.fileSize > 0) {
                Text(
                    text = stringResource(R.string.downloaded_size, "${history.fileSize / 1024}kB"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun getDownloadStatusRes(status: DownloadStatus): String {
    return when (status) {
        DownloadStatus.INIT -> stringResource(R.string.download_status_init)
        DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
        DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
        DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
        DownloadStatus.DELETED -> stringResource(R.string.download_status_deleted)
        else -> ""
    }
}

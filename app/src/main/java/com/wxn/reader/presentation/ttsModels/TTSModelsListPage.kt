package com.wxn.reader.presentation.ttsModels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.sharp.Face
import androidx.compose.material.icons.sharp.Face2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.util.Logger
import com.wxn.base.util.toLocale
import com.wxn.reader.R
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.ttsModels.viewmodels.ModelCardShowType
import com.wxn.reader.presentation.ttsModels.viewmodels.TTSModelsListViewModel
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.util.HeartRatingBar
import com.wxn.reader.util.HeightSpace
import com.wxn.reader.util.download.DownloadState
import com.wxn.reader.util.tts.displayLocales
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TTSModelsListPage(
    viewModel: TTSModelsListViewModel = hiltViewModel(),
) {
    val navNavigator = LocalNavController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val showBaseModelDownloadDailog by viewModel.showBaseModelDownloadDailog.collectAsStateWithLifecycle()
    val showBaseModelDownloadMission by viewModel.showBaseModelDownloadMission.collectAsStateWithLifecycle()
    val showModelCard by viewModel.showModelCard.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteModel by remember { mutableStateOf<TTSModelData?>(null) }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= uiState.modelList.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !uiState.isLoadingMore) {
            viewModel.loadMoreModels()
        }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(R.string.tts_model_management)) },
                navigationIcon = {
                    IconButton(onClick = { navNavigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(visible = showBaseModelDownloadMission) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column() {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(start = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    stringResource(R.string.base_model),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Spacer(modifier = Modifier.padding(vertical = 4.dp))

                            uiState.downloadBaseModels.forEach { (name, url, status) ->

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp, 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {

                                        Text(text = name)

                                        Spacer(modifier = Modifier.weight(1f))

                                        if (status.isCompleted && status.isExtracting) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            downloadStates[name]?.let { downloadState ->
                                                if (downloadState.isDownloading) {
                                                    if (downloadState.progress <= 0f) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Text("${(downloadState.progress * 100).toInt()}%")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                when {
                    uiState.isLoading && uiState.modelList.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.modelList.isEmpty() && uiState.error == null -> {
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
                    }

                    !uiState.isLoading && uiState.modelList.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (uiState.error.isNullOrEmpty()) stringResource(R.string.unknown_error) else uiState.error.orEmpty(),
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(onClick = {
                                    viewModel.loadModels()
                                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Text(stringResource(R.string.refresh))
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.modelList,
                                key = { model -> model.name }
                            ) { model ->
                                val isDownloaded = uiState.downloadedModels.contains(model.name)
                                if (isDownloaded) {
                                    val needsHint = model.name in uiState.newlyDownloadedModels
                                    val isHintAnimating = remember { mutableStateOf(false) }
                                    val hintOffset = remember { Animatable(0f) }
                                    val density = LocalDensity.current


                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (!isHintAnimating.value &&
                                                dismissValue == SwipeToDismissBoxValue.EndToStart
                                            ) {
                                                pendingDeleteModel = model
                                                showDeleteDialog = true
                                                false
                                            } else {
                                                false
                                            }
                                        },
                                        positionalThreshold = { it * 0.4f }
                                    )

                                    // 等待条件满足后执行动画, 第一次下载时显示左滑可以删除的滑动动画
                                    LaunchedEffect(needsHint) {
                                        if (!needsHint) return@LaunchedEffect
                                        // 等待：列表空闲 + 条目可见
                                        withTimeoutOrNull(30_000L) {
                                            snapshotFlow {
                                                listState.isScrollInProgress to
                                                        listState.layoutInfo.visibleItemsInfo.any { it.key == model.name }
                                            }.first { (scrolling, visible) ->
                                                !scrolling && visible
                                            }

                                            isHintAnimating.value = true
                                            val targetPx = with(density) { (-80.dp).toPx() }
                                            delay(500)
                                            hintOffset.animateTo(targetPx, tween(300))
                                            delay(1000)
                                            hintOffset.animateTo(0f, tween(300))
                                            isHintAnimating.value = false
                                            viewModel.clearHintAnimation(model.name, true)
                                        } ?: run {
                                            viewModel.clearHintAnimation(model.name, false)
                                            return@LaunchedEffect
                                        }
                                    }

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                                        enableDismissFromStartToEnd = false,
                                        enableDismissFromEndToStart = !isHintAnimating.value,  // 动画期间禁止滑动
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 8.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.errorContainer)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .offset {
                                                    IntOffset(hintOffset.value.roundToInt(), 0)
                                                }) {
                                            TTSModelItem(
                                                model = model,
                                                isDownloaded = true,
                                                isExpanded = uiState.expandedModels.contains(model.name),
                                                downloadState = downloadStates[model.name],
                                                isPlayingSpeaker = uiState.currentlyPlayingSpeaker,
                                                onToggleExpansion = {
                                                    if (!isHintAnimating.value) {
                                                        viewModel.toggleModelExpansion(model.name)
                                                    }
                                                },
                                                onDownloadClick = {
                                                    if (!isHintAnimating.value) {
                                                        viewModel.downloadModel(model)
                                                    }
                                                },
                                                onPlayClick = { url ->
                                                    viewModel.playSampleVoice(url)
                                                },
                                                onPauseClick = {
                                                    viewModel.pauseSampleVoice()
                                                },
                                                onInfoClick = {
                                                    viewModel.showModelCard(model)
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    TTSModelItem(
                                        model = model,
                                        isDownloaded = false,
                                        isExpanded = uiState.expandedModels.contains(model.name),
                                        downloadState = downloadStates[model.name],
                                        isPlayingSpeaker = uiState.currentlyPlayingSpeaker,
                                        onToggleExpansion = {

                                            viewModel.toggleModelExpansion(model.name)
                                        },
                                        onDownloadClick = {
                                            viewModel.downloadModel(model)
                                        },
                                        onPlayClick = { url ->
                                            viewModel.playSampleVoice(url)
                                        },
                                        onPauseClick = {
                                            viewModel.pauseSampleVoice()
                                        },
                                        onInfoClick = {
                                            viewModel.showModelCard(model)
                                        }
                                    )
                                }
                            }
                            // Loading more indicator
                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                            // End of list indicator
                            if (uiState.currentPage >= uiState.totalPages && uiState.modelList.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.no_more_data),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.modelList.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (showModelCard != ModelCardShowType.ModelCardHidden) {
                ModelCardLayer(viewModel)
            }

            if (showBaseModelDownloadDailog) {
                AlertDialog(
                    onDismissRequest = {
                        viewModel.dismissBaseModelDownloadDialog()
                    },
                    title = {
//                    Text(stringResource(R.string.))
                    },
                    text = {
                        Text(stringResource(R.string.download_base_model_hints))
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            onClick = {
                                viewModel.startBaseModelDownloadMission()
                                viewModel.dismissBaseModelDownloadDialog()
                            }
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.cancelBaseModelMission()
                                viewModel.dismissBaseModelDownloadDialog()
                            }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showDeleteDialog && pendingDeleteModel != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteDialog = false
                        pendingDeleteModel = null
                    },
                    title = {
                        Text(text = stringResource(R.string.delete_model))
                    },
                    text = {
                        Text(
                            text = stringResource(
                                R.string.confirm_delete_model,
                                pendingDeleteModel?.name ?: ""
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            onClick = {
                                pendingDeleteModel?.let { viewModel.deleteModel(it) }
                                showDeleteDialog = false
                                pendingDeleteModel = null
                            }
                        ) {
                            Text(text = stringResource(R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                pendingDeleteModel = null
                            }
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TTSModelItem(
    model: TTSModelData,
    isDownloaded: Boolean,
    isExpanded: Boolean,
    downloadState: DownloadState?,
    isPlayingSpeaker: String?,
    onToggleExpansion: () -> Unit,
    onDownloadClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    onPauseClick: () -> Unit,
    onInfoClick: () -> Unit
) {

    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp)
        ) {
            // Model header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        onToggleExpansion()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Icon(
                            imageVector = Icons.Default.Info,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp)
                                .clickable {
                                    onInfoClick()
                                    Logger.d("TTSModelsListPage::Item.info.click:${model.license},${model.licenseUrl}")
                                },
                            contentDescription = "model info"
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.displayLocales(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text =
                                stringResource(R.string.num_speakers, model.speakers_num),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.process_speed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HeartRatingBar(rating = model.processSpeed)
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.quality),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HeartRatingBar(rating = model.quality)
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.size)}: ${model.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Download/Expand button
                    if (downloadState?.isDownloading == true) { //downloading
                        if (downloadState.progress <= 0f) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "${(downloadState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(27.dp)
                            )
                        }
                    } else if (downloadState?.isExtracting == true) {  //解压缩中
                        //                    Text(stringResource(R.string.extracting))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = ProgressIndicatorDefaults.circularDeterminateTrackColor
                        )
                    } else if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.FileDownloadDone,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                            contentDescription = "Downloaded"
                        )
                    } else if (downloadState != null) {  //pending downloading
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                modifier = Modifier.size(24.dp),
                                contentDescription = stringResource(R.string.download)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Icon(
                        imageVector =
                            if (isExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        modifier = Modifier.size(24.dp),
                        contentDescription = "expand or close"
                    )
                }
            }

            // Expanded speakers section
            AnimatedVisibility(isExpanded) {
                Column {
                    model.speakers.forEach { speaker ->
                        SpeakerItem(
                            speaker = speaker,
                            isPlaying = isPlayingSpeaker == speaker.sampleVoice,
                            onPlayClick = { onPlayClick(speaker.sampleVoice) },
                            onPauseClick = onPauseClick
                        )
                    }
                }
            }
        }

        if (!model.license.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp))
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    model.license,
//                    "license:${model.license.orEmpty()}",
                    modifier = Modifier.padding(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SpeakerItem(
    speaker: com.wxn.reader.util.tts.data.Speaker,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = {
                if (isPlaying) {
                    onPauseClick()
                } else {
                    onPlayClick()
                }
            })
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (speaker.gender == "female") Icons.Sharp.Face2 else Icons.Sharp.Face,
            contentDescription = "face",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(12.dp))
        // Speaker info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = speaker.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = if (speaker.gender == "female") stringResource(R.string.female) else stringResource(
                        R.string.male
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = speaker.locale.toLocale()?.displayName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Play/Pause button
        IconButton(
            onClick = {
                if (isPlaying) {
                    onPauseClick()
                } else {
                    onPlayClick()
                }
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play"
            )
        }
    }
}

@Composable
fun ModelCardLayer(viewModel: TTSModelsListViewModel) {
    val showModelCardShowType by viewModel.showModelCard.collectAsStateWithLifecycle()
    val cardInfo by viewModel.modelCardInfo.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f))
            .clickable { viewModel.hiddenModelCard() },
        contentAlignment = Alignment.Center
    ) {
        // 半透明背景
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(360.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp, 24.dp),
            contentAlignment = Alignment.Center,
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                when (showModelCardShowType) {
                    ModelCardShowType.ModelCardError -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "error",
                                tint = MaterialTheme.colorScheme.error
                            )

                            Text(
                                text = cardInfo,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ModelCardShowType.ModelCardLoadding -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {

                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    ModelCardShowType.ModelCardShow -> {

                        Text(
                            text = stringResource(R.string.model_card),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HeightSpace(12.dp)

                        Text(
                            text = cardInfo,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                    }
                }
            }
        }
    }
}
package com.wxn.reader.presentation.settings.components


import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FileDownloadDone

import androidx.compose.material.icons.outlined.Folder

import androidx.compose.material.icons.outlined.FolderCopy

import androidx.compose.material.icons.outlined.MenuBook

import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.base.bean.TTSEngineType
import com.wxn.base.util.SherpaOnnxDeviceChecker
import com.wxn.base.util.ToastUtil
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.settings.SettingsViewModel
import com.wxn.reader.presentation.settings.DeleteDirectoryState
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.util.HeightSpace
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettings(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val navController: NavHostController = LocalNavController.current
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val ttsPrefs by viewModel.ttsPreferences.collectAsStateWithLifecycle()
    val opdsDownloadPrefs by viewModel.opdsDownloadPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deleteState by viewModel.deleteDirectoryState.collectAsStateWithLifecycle()
    var showSelectDirectoryDialog by remember { mutableStateOf(false) }
    var isDirectorySectionExpanded by remember { mutableStateOf(false) }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }
    var showOpdsDirectoryPicker by remember { mutableStateOf(false) }

    var showTTSEngineSelection by remember { mutableStateOf(false) }
    var showAiTTSHintDialog by remember { mutableStateOf(false) }

    val getDirectoryPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                viewModel.addScanDirectory(it)
            }
        }

    val opdsDirectoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.updateOpdsSafTreeUri(it.toString())
            }
        }

    if (appPreferences != null) {
        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = { Text(stringResource(R.string.general_settings)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {

                //Pdf support
                //            ListItem(
                //                modifier = Modifier.padding(vertical = 8.dp),
                //                leadingContent = {
                //                    Icon(
                //                        Icons.Outlined.PictureAsPdf,
                //                        contentDescription = "Enable pdf support"
                //                    )
                //                },
                //                headlineContent = { Text(stringResource(R.string.enable_pdf_support)) },
                //                supportingContent = { Text(stringResource(R.string.pdf_files_do_not_support_features_such_as_highlighting_annotations)) },
                //                trailingContent = {
                //                    Switch(
                //                        checked = appPreferences.enablePdfSupport,
                //                        onCheckedChange = { viewModel.updatePdfSupport(it) }
                //                    )
                //                }
                //            )
                //
                //            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                // scan directories
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Outlined.FolderCopy,
                            contentDescription = "scan directories"
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.scan_directories)) },
                    modifier = Modifier.clickable{
                        isDirectorySectionExpanded = !isDirectorySectionExpanded
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            isDirectorySectionExpanded = !isDirectorySectionExpanded
                        }) {
                            Icon(
                                if (isDirectorySectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isDirectorySectionExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                )
                AnimatedVisibility(
                    visible = isDirectorySectionExpanded
                ) {
                    Column {
                        appPreferences!!.scanDirectories.forEach { directory ->
                            val uri = Uri.parse(directory)
                            val directoryName =
                                uri.lastPathSegment?.substringAfter(":") ?: directory
                            ListItem(
                                modifier = Modifier.padding(start = 16.dp),
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = "directory"
                                    )
                                },
                                headlineContent = { Text(directoryName) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.prepareDeleteDirectory(directory) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Remove directory"
                                        )
                                    }
                                }
                            )
                        }
//                        Button(
//                            onClick = { showSelectDirectoryDialog = true },
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(horizontal = 16.dp, vertical = 8.dp)
//                        ) {
//                            Text(stringResource(R.string.add_scan_directory))
//                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // OPDS download directory
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Outlined.DownloadForOffline,
                            contentDescription = "OPDS download"
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.opds_download_location)) },
                    supportingContent = {
                        Text(
                            when (opdsDownloadPrefs.opdsDownloadLocation) {
                                "saf_tree" -> {
                                    val uri = opdsDownloadPrefs.opdsSafTreeUri
                                    val dirName = Uri.parse(uri).lastPathSegment?.substringAfter(":")
                                        ?: stringResource(R.string.opds_location_external)
                                    stringResource(R.string.opds_location_external_with_path, dirName)
                                }
                                else -> stringResource(R.string.opds_location_app_internal)
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        if (opdsDownloadPrefs.opdsDownloadLocation == "saf_tree") {
                            showOpdsDirectoryPicker = !showOpdsDirectoryPicker
                        } else {
                            try {
                                opdsDirectoryPickerLauncher.launch(null)
                            } catch (_: ActivityNotFoundException) {
                                ToastUtil.show(R.string.no_file_manager_found)
                            }
                        }
                    },
                    trailingContent = {
                        if (opdsDownloadPrefs.opdsDownloadLocation == "saf_tree") {
                            IconButton(onClick = {
                                showOpdsDirectoryPicker = !showOpdsDirectoryPicker
                            }) {
                                Icon(
                                    if (showOpdsDirectoryPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Change OPDS directory"
                                )
                            }
                        }
                    }
                )

                AnimatedVisibility(visible = showOpdsDirectoryPicker) {
                    Column {
                        ListItem(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable {
                                    showOpdsDirectoryPicker = false
                                    viewModel.updateOpdsDownloadPrefs("app_internal")
                                },
                            headlineContent = { Text(stringResource(R.string.opds_location_app_internal)) },
                            supportingContent = { Text(stringResource(R.string.opds_location_app_internal_desc)) }
                        )
                        ListItem(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable {
                                    showOpdsDirectoryPicker = false
                                    try {
                                        opdsDirectoryPickerLauncher.launch(null)
                                    } catch (_: ActivityNotFoundException) {
                                        ToastUtil.show(R.string.no_file_manager_found)
                                    }
                                },
                            headlineContent = { Text(stringResource(R.string.opds_location_pick_other)) },
                            supportingContent = { Text(stringResource(R.string.opds_location_pick_other_desc)) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.language)) },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Translate,
                            contentDescription = "directory"
                        )
                    },
                    trailingContent = {
                        Column {
                            Text(
                                text = LanguageInfo.fromCode(appPreferences!!.language)?.displayName.orEmpty(),
                                modifier = Modifier.clickable { isLanguageDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = isLanguageDropdownExpanded,
                                onDismissRequest = { isLanguageDropdownExpanded = false }
                            ) {
                                LanguageUtil.languageMaps.entries.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(entry.value.displayName) },
                                        onClick = {
                                            isLanguageDropdownExpanded = false
                                            viewModel.updateLanguage(entry.value)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { isLanguageDropdownExpanded = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.auto_open_last_read_file)) },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = "Auto Open Last Read file"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = appPreferences!!.autoOpenLastRead,
                            onCheckedChange = { viewModel.updateAutoOpenLastRead(it) }
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Outlined.CloudSync,
                            contentDescription = "backup and restore"
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.backup_restore_title)) },
                    modifier = Modifier.clickable {
                        navController.navigate(Screens.BackupSettingsScreen.route)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Outlined.FolderCopy,
                            contentDescription = "Shelves"
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.shelves)) },
                    modifier = Modifier.clickable {
                        navController.navigate(Screens.ShelvesScreen.route)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.tts_engine)) },
                    supportingContent = {
                        Text(
                            when (ttsPrefs?.ttsEngineType) {
                                TTSEngineType.OFFLINE_NEURAL_AI -> {
                                    if (ttsPrefs?.selectedTTSModel != null) {
                                        "${stringResource(R.string.tts_engine_offline_ai)}: ${ttsPrefs?.selectedTTSModel}"
                                    } else {
                                        stringResource(R.string.tts_engine_offline_ai)
                                    }
                                }

                                TTSEngineType.SYSTEM -> stringResource(R.string.tts_engine_system)
                                else -> stringResource(R.string.tts_engine_system)
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        showTTSEngineSelection = !showTTSEngineSelection
                    },
                    trailingContent = {
                        IconButton(onClick = { showTTSEngineSelection = !showTTSEngineSelection }) {
                            Icon(
                                if (showTTSEngineSelection) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = "Change TTS Engine"
                            )
                        }
                    }
                )

                AnimatedVisibility(visible = showTTSEngineSelection) {
                    val ctx = LocalContext.current
                    Column {
                        TTSEngineType.entries.forEach { engineType ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (engineType == TTSEngineType.OFFLINE_NEURAL_AI
                                            && !SherpaOnnxDeviceChecker.isDeviceSupported(ctx)
                                        ) {
                                            ToastUtil.show(R.string.err_device_not_support_ai_tts)
                                        } else {
                                            if (engineType == TTSEngineType.OFFLINE_NEURAL_AI
                                                && ttsPrefs?.isFirstAiTtsSelection == true
                                            ) {
                                                showAiTTSHintDialog = true
                                            }
                                            viewModel.updateTTSEngineType(engineType)
                                        }
                                    }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = ttsPrefs?.ttsEngineType == engineType,
                                    onClick = {
                                        if (engineType == TTSEngineType.OFFLINE_NEURAL_AI
                                            && !SherpaOnnxDeviceChecker.isDeviceSupported(ctx)
                                        ) {
                                            ToastUtil.show(R.string.err_device_not_support_ai_tts)
                                        } else {
                                            if (engineType == TTSEngineType.OFFLINE_NEURAL_AI
                                                && ttsPrefs?.isFirstAiTtsSelection == true
                                            ) {
                                                showAiTTSHintDialog = true
                                            }
                                            viewModel.updateTTSEngineType(engineType)
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (engineType) {
                                        TTSEngineType.SYSTEM -> stringResource(R.string.tts_engine_system)
                                        TTSEngineType.OFFLINE_NEURAL_AI -> stringResource(R.string.tts_engine_offline_ai)
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = ttsPrefs?.ttsEngineType == TTSEngineType.OFFLINE_NEURAL_AI
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Outlined.DownloadForOffline,
                                contentDescription = "scan directories"
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.download_tts_model)) },
                        modifier = Modifier.clickable {
                            navController.navigate(Screens.TTSModelsListPageScreen.route)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Outlined.DownloadForOffline,
                            contentDescription = "scan directories"
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.download_fonts)) },
                    modifier = Modifier.clickable {
                        navController.navigate(Screens.FontManagementScreen.route)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.download_history)) },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.FileDownloadDone,
                            contentDescription = "download history"
                        )
                    },
                    trailingContent = {
                    },
                    modifier = Modifier.clickable(onClick = {
                        navController.navigate(Screens.DownloadHistoryScreen.route)
                    })
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Outlined.MenuBook,
                            contentDescription = stringResource(R.string.lookup_history)
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.lookup_history)) },
                    modifier = Modifier.clickable {
                        navController.navigate(Screens.LookupHistoryScreen.route)
                    }
                )

                //            ListItem(
                //                headlineContent = { Text(stringResource(R.string.tts_set))},
                //                leadingContent = { Icon(Icons.Outlined.SmartToy, contentDescription = "tts") },
                //                trailingContent = {},
                //                modifier = Modifier.clickable {
                //                    navController.navigate(Screens.TtsSetScreen.route)
                //                }
                //            )
                //TODO 采用Edge TTS
            }
        }

        if (showSelectDirectoryDialog) {
            AlertDialog(
                onDismissRequest = { showSelectDirectoryDialog = false },
                title = { Text(stringResource(R.string.select_directory)) },
                text = { Text(stringResource(R.string.choose_a_directory_to_add_to_the_scan_list)) },
                confirmButton = {
                    Button(onClick = {
                        showSelectDirectoryDialog = false
                        try {
                            getDirectoryPermissionLauncher.launch(null)
                        } catch (_: ActivityNotFoundException) {
                            ToastUtil.show(R.string.no_file_manager_found)
                        }

                    }) {
                        Text(stringResource(R.string.select))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSelectDirectoryDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (deleteState !is DeleteDirectoryState.Idle) {

            val directoryName = when (deleteState) {
                is DeleteDirectoryState.Confirming -> (deleteState as DeleteDirectoryState.Confirming).directoryName
                is DeleteDirectoryState.Deleting -> (deleteState as DeleteDirectoryState.Deleting).directoryName
                is DeleteDirectoryState.TtsBlocked -> (deleteState as DeleteDirectoryState.TtsBlocked).directoryName
                is DeleteDirectoryState.Completed -> (deleteState as DeleteDirectoryState.Completed).directoryName
                else -> ""
            }

            AlertDialog(
                onDismissRequest = {
                    when (deleteState) {
                        is DeleteDirectoryState.Confirming,
                        is DeleteDirectoryState.Deleting -> {}

                        else -> viewModel.resetDeleteDirectoryState()
                    }
                },
                title = {
                    Text(stringResource(R.string.remove_directory, directoryName))
                },
                text = {
                    Crossfade(
                        targetState = deleteState,
                        animationSpec = tween(durationMillis = 200),
                        label = "deleteDialogContent"
                    ) { state ->
                        when (state) {
                            is DeleteDirectoryState.Confirming -> {
                                Text(
                                    stringResource(
                                        R.string.remove_directory_description,
                                        state.bookCount
                                    )
                                )
                            }

                            is DeleteDirectoryState.Deleting -> {
                                Column {
                                    if (state.total > 0) {
                                        LinearProgressIndicator(
                                            progress = {
                                                state.current.toFloat() / state.total.toFloat()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            stringResource(
                                                R.string.removing_directory_books,
                                                state.current,
                                                state.total
                                            )
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(
                                                R.string.removing_book_title,
                                                state.bookTitle
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.loading))
                                    }
                                }
                            }

                            is DeleteDirectoryState.TtsBlocked -> {
                                Text(stringResource(R.string.directory_remove_tts_blocked))
                            }

                            is DeleteDirectoryState.Completed -> {
                                if (state.totalBooks > 0) {
                                    if (state.failedBooks > 0) {
                                        Text(
                                            stringResource(
                                                R.string.deletion_partial_failure,
                                                state.deletedBooks,
                                                state.failedBooks
                                            )
                                        )
                                    } else {
                                        Text(
                                            stringResource(
                                                R.string.directory_removed_success,
                                                state.deletedBooks
                                            )
                                        )
                                    }
                                } else {
                                    Text(stringResource(R.string.directory_removed_no_books))
                                }
                            }

                            else -> {}
                        }
                    }
                },
                confirmButton = {
                    Crossfade(
                        targetState = deleteState,
                        animationSpec = tween(durationMillis = 200),
                        label = "deleteDialogConfirm"
                    ) { state ->
                        when (state) {
                            is DeleteDirectoryState.Confirming -> {
                                Button(
                                    colors = ButtonDefaults.buttonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                    onClick = { viewModel.confirmDeleteDirectory() }
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }

                            is DeleteDirectoryState.Deleting -> {
                                Spacer(Modifier.width(1.dp))
                            }

                            is DeleteDirectoryState.TtsBlocked,
                            is DeleteDirectoryState.Completed -> {
                                Button(onClick = { viewModel.resetDeleteDirectoryState() }) {
                                    Text(stringResource(R.string.ok))
                                }
                            }

                            else -> {}
                        }
                    }
                },
                dismissButton = {
                    Crossfade(
                        targetState = deleteState,
                        animationSpec = tween(durationMillis = 200),
                        label = "deleteDialogDismiss"
                    ) { state ->
                        when (state) {
                            is DeleteDirectoryState.Confirming -> {
                                TextButton(onClick = { viewModel.resetDeleteDirectoryState() }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }

                            is DeleteDirectoryState.Deleting -> {
                                Spacer(Modifier.width(1.dp))
                            }

                            else -> {
                                Spacer(Modifier.width(1.dp))
                            }
                        }
                    }
                }
            )
        }

        if (showAiTTSHintDialog) {
            AlertDialog(
                onDismissRequest = { showAiTTSHintDialog = false },
                title = { },
                text = {
                    Column(
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.tts_model_download_hint_title),
                            style = MaterialTheme.typography.titleMedium)
                        HeightSpace(8.dp)
                        Text(stringResource(R.string.tts_model_download_hint_desc),
                            style = MaterialTheme.typography.bodyMedium)
                    }
               },
                confirmButton = {
                    Button(onClick = {
                        showAiTTSHintDialog = false
                        navController.navigate(Screens.TTSModelsListPageScreen.route)
                    }) {
                        Text(stringResource(R.string.go_to_download_model),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAiTTSHintDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
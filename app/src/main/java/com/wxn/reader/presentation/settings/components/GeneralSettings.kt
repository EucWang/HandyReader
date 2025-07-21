package com.wxn.reader.presentation.settings.components


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderCopy
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.settings.SettingsViewModel
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettings(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val navController: NavHostController = LocalNavController.current
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var directoryToDelete by remember { mutableStateOf("") }
    var showSelectDirectoryDialog by remember { mutableStateOf(false) }
    var showDeleteDirectoryDialog by remember { mutableStateOf(false) }
    var isDirectorySectionExpanded by remember { mutableStateOf(false) }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }

    val getDirectoryPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
//                context.contentResolver.takePersistableUriPermission(
//                    it,
//                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//                )
//                viewModel.addScanDirectory(it.toString())
                viewModel.addScanDirectory(it)
            }
        }

    if (appPreferences != null) {
        Scaffold(
            topBar = {
                TopAppBar(
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
                            val directoryName = uri.lastPathSegment?.substringAfter(":") ?: directory
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
                                    IconButton(onClick = {
                                        directoryToDelete = directory
                                        showDeleteDirectoryDialog = true
                                    }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Remove directory"
                                        )
                                    }
                                }
                            )
                        }
                        Button(
                            onClick = { showSelectDirectoryDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.add_scan_directory))
                        }
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
                        Icon(Icons.Outlined.AutoStories,
                            contentDescription = "Auto Open Last Read file")
                    },
                    trailingContent = {
                        Switch(
                            checked = appPreferences!!.autoOpenLastRead,
                            onCheckedChange = { viewModel.updateAutoOpenLastRead(it) }
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                        getDirectoryPermissionLauncher.launch(null)

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

        if (showDeleteDirectoryDialog) {
            val uri = Uri.parse(directoryToDelete)
            val directoryName = uri.lastPathSegment?.substringAfter(":") ?: directoryToDelete
            AlertDialog(
                onDismissRequest = { showDeleteDirectoryDialog = false },
                title = { Text(stringResource(R.string.delete_directory, directoryName)) },
                text = { Text(stringResource(R.string.are_you_sure_you_want_to_delete_this_directory_from_the_scan_list)) },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        onClick = {
                            viewModel.removeScanDirectory(directoryToDelete)
                            showDeleteDirectoryDialog = false
                            directoryToDelete = ""
                        }) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        directoryToDelete = ""
                        showDeleteDirectoryDialog = false
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
package com.wxn.reader.presentation.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wxn.reader.presentation.settings.viewmodels.SpeakerViewModel
import com.wxn.reader.util.tts.data.Option
import com.wxn.reader.util.tts.data.OutputFormat
import com.wxn.reader.util.tts.data.SettingsData
import com.wxn.reader.util.tts.data.Speaker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerScreen(
    viewModel: SpeakerViewModel = hiltViewModel()
) {
    SpeakerContentView(viewModel)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakerContentView(viewModel: SpeakerViewModel, openDrawer: (() -> Unit)? = null) {

    val speakers by viewModel.speakerUiState.collectAsStateWithLifecycle()
    val voices by viewModel.voicesUiState.collectAsStateWithLifecycle()
    val message by viewModel.messageUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsUiState.collectAsStateWithLifecycle()

    var openPicker by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val editItems = remember { mutableStateListOf<String>() } // id

    var openSetting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        scope.launch {
            val message = message ?: return@launch
            val msg = if (message.error) {
                "Error: ${message.description}"
            } else {
                message.description
            }
            snackBarHostState.showSnackbar(message = msg)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackBarHostState)
        },
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ), title = {
                Text(text = "Edge TSS")
            }, navigationIcon = {
                IconButton2("Menu", Icons.Default.Menu) { openDrawer?.invoke() }
            }, actions = {
                when (editMode) {
                    true -> {
                        Text("${editItems.size} selected")
                        IconButton2(
                            "Delete", Icons.Filled.Delete
                        ) {
                            viewModel.removeSpeakers(editItems)
                        }
                        IconButton2("Cancel", Icons.Filled.Close) {
                            editMode = false
                            editItems.clear()
                        }
                    }

                    false -> {
//                        IconButton2("Open Settings", Icons.Filled.Settings) {
//                            openSetting = true
//                        }
                        IconButton2("Add Items", Icons.Filled.Add) {
                            openPicker = true
                            viewModel.loadVoices()
                        }
                        IconButton2("Edit Items", Icons.Filled.Edit) {
                            editMode = true
                        }
                    }
                }
            })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(speakers) { speaker ->
                Item(
                    speaker = speaker,
                    status = if (editMode) ItemStatus.EDIT else ItemStatus.VIEW,
                    onSelected = { id ->
                        when (editMode) {
                            true -> {
                                if (editItems.contains(id)) {
                                    editItems.remove(id)
                                } else {
                                    editItems.add(id)
                                }
                            }

                            false -> {
                                viewModel.setActiveSpeaker(id)
                            }
                        }
                    })
            }
        }
    }

    if (openPicker) {
        SpeakerPicker(
            data = voices,
            onConfirm = { ids ->
                openPicker = false
                viewModel.addSpeakers(ids)
            },
            onCancel = {
                openPicker = false
            }
        )
    }

    if (openSetting) {
        Settings(
            defaultValue = settings,
            onConfirm = {
                openSetting = false
                viewModel.setAudioFormat(it.format)
            },
            onCancel = {
                openSetting = false
            }
        )
    }
}

//@Preview(showBackground = true)
//@Composable
//private fun ContentViewPreview() {
//    SpeakerContentView {}
//}

// region ListItem

private enum class ItemStatus {
    VIEW, EDIT
}

@Composable
private fun Item(speaker: Speaker, status: ItemStatus, onSelected: (id: String) -> Unit) {
    var selected by remember { mutableStateOf(false) }
    var preMode by remember { mutableStateOf(status) }

    if (preMode != status) {
        preMode = status
        selected = false
    }

    ListItem(
        modifier = Modifier.clickable {
            selected = !selected
            onSelected(speaker.id)
        },
        headlineContent = { Text(speaker.name) },
        supportingContent = { Text(speaker.description) },
        trailingContent = { Text(speaker.locale) },
        leadingContent = {
            when (status) {
                ItemStatus.VIEW -> {
                    Icon(
                        if (speaker.active) Icons.Filled.Check else if (speaker.gender == "Male") Icons.Filled.Male else Icons.Filled.Female,
                        contentDescription = "",
                        tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                ItemStatus.EDIT -> {
                    Icon(
                        if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        })
}

// endregion

// region SpeakerPicker


@Composable
private fun SpeakerPicker(
    modifier: Modifier = Modifier,
    data: List<Option>,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit = {},
) {
    var selected by remember { mutableStateOf(emptyList<String>()) }
    var search by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<Option>()) }

    if (data.isNotEmpty() && candidates.isEmpty()) {
        candidates = data
    }

    Dialog(onDismissRequest = { onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Select Speaker",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )

                HorizontalDivider()

                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    singleLine = true,
                    value = search,
                    onValueChange = {
                        search = it
                        if (search.isEmpty()) {
                            candidates = data
                            return@TextField
                        }
                        val searches = search.split(" ")
                        candidates = data.filter { v ->
                            for (search in searches) {
                                val contain = v.searchKey.contains(search, ignoreCase = true)
                                if (!contain) {
                                    return@filter false
                                }
                            }
                            return@filter true
                        }
                    },
                    leadingIcon = {
                        if (data.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        } else {
                            Image(imageVector = Icons.Filled.Search, contentDescription = "")
                        }
                    },
                )

                LazyColumn(modifier = modifier.heightIn(max = 400.dp)) {
                    items(items = candidates) { voice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = true,
                                    onClick = {
                                        selected = if (selected.contains(voice.name)) {
                                            selected - voice.name
                                        } else {
                                            selected + voice.name
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(voice.name),
                                onCheckedChange = null
                            )
                            Text(
                                text = voice.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { onCancel() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.weight(1.0f))

                    TextButton(
                        onClick = {
                            onConfirm(selected)
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}


@Composable
private fun Settings(
    modifier: Modifier = Modifier,
    defaultValue: SettingsData,
    onConfirm: (SettingsData) -> Unit,
    onCancel: () -> Unit = {},
) {
    var format by remember { mutableStateOf(defaultValue.format) }

    Dialog(onDismissRequest = { onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Setting",
                    style = MaterialTheme.typography.headlineSmall,
                )
                HorizontalDivider()

                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Text(
                        "TTS Audio Format",
                        style = MaterialTheme.typography.titleMedium
                    )


                    listOf(
                        OutputFormat.Audio24Khz48KbitrateMonoMp3,
                        OutputFormat.Audio24Khz96KbitrateMonoMp3,
                        OutputFormat.Webm24Khz16BitMonoOpus,
                    ).forEach {
                        val value = it.value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = true,
                                    onClick = {
                                        format = value
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = value == format,
                                onCheckedChange = null
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(top = 16.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column {
//                            Text(
//                                text = "Use Flow",
//                                style = MaterialTheme.typography.titleMedium,
//                            )
//                            Text(
//                                text = "Start playing audio after a while of downloading data.",
//                                style = MaterialTheme.typography.bodySmall,
//                                modifier = Modifier
//                                    .width(200.dp)
//                            )
//                        }
//                        Spacer(modifier = Modifier.weight(1.0f))
//                        Switch(
//                            modifier = Modifier
//                                .padding(start = 5.dp),
//                            checked = useFlow,
//                            onCheckedChange = { useFlow = it })
//                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.weight(1.0f))

                    TextButton(
                        onClick = { onCancel() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Cancel")
                    }

                    TextButton(
                        onClick = {
                            val data = SettingsData(
                                format = format,
                            )
                            onConfirm(data)
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
fun IconButton2(
    name: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconButton(onClick = {
        onClick()
    }) {
        Icon(
            modifier = modifier,
            imageVector = icon,
            contentDescription = name,
            tint = tint
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Picker(
    title: String,
    data: List<PickOption>,
    onSelected: (value: String) -> Unit,
    enable: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(PickOption("", "")) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (data.isNotEmpty()) {
                expanded = !expanded
            }
        }
    ) {
        TextField(
            enabled = enable,
            value = selected.title,
            onValueChange = { },
            readOnly = true,
            label = { Text(title) },
            trailingIcon = {
                if (!enable) {
                    return@TextField
                }
                if (data.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .scale(0.6f),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            data.map {
                DropdownMenuItem(
                    enabled = data.isNotEmpty(),
                    text = { Text(text = it.title) },
                    onClick = {
                        selected = it
                        expanded = false
                        onSelected(it.value)
                    }
                )
            }
        }
    }
}

data class PickOption(val title: String, val value: String)


@Preview(showBackground = true)
@Composable
private fun PickerViewPreview() {
    var data by remember { mutableStateOf(emptyList<PickOption>()) }

    LaunchedEffect(UInt) {
//        delay(1000L)
        data = (0..10).map {
            PickOption("Option $it", "$it")
        }
    }

    Box(Modifier.size(300.dp)) {
        Column {
            Picker(
                title = "Select Option",
                data = data,
                onSelected = {},
            )
            Picker(
                title = "Select Option2",
                data = data,
                onSelected = {},
            )
        }
    }
}

data class OptionItem(val name: String, val value: String, val icon: ImageVector? = null)

@Composable
fun OptionPicker(
    modifier: Modifier = Modifier,
    default: String,
    options: List<OptionItem>,
    onClick: (value: String) -> Unit
) {
    var expanded = remember { mutableStateOf(false) }
    var selected = remember { mutableStateOf<OptionItem?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded.value = false
                        selected.value = option
                        onClick(option.value)
                    },
                    leadingIcon = (if (option.icon != null) {
                        Icon(option.icon, contentDescription = null)
                    } else {
                        null
                    }) as @Composable (() -> Unit)?
                )
            }
        }
        Row(
            modifier = Modifier.clickable(onClick = { expanded.value = !expanded.value }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = selected.value?.name ?: default, fontStyle = MaterialTheme.typography.titleLarge.fontStyle)
            Icon(
                imageVector = if (expanded.value) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                contentDescription = null,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionPickerPreview() {
    Column(
        modifier = Modifier.size(300.dp)
    ) {
        OptionPicker(
            default = "Default",
            options = listOf(
                OptionItem("Option1", "value1"),
                OptionItem("Option2", "value2"),
                OptionItem("Option3", "value3"),
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingPreview() {
    Settings(defaultValue = SettingsData(""), onConfirm = {}, onCancel = {})
}

// endregion
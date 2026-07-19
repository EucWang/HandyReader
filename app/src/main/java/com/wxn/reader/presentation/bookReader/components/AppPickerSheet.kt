package com.wxn.reader.presentation.bookReader.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.data.model.TranslatorItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    title: String,
    items: List<TranslatorItem>,
    currentSelectedId: String = "",
    isSetAsDefault: Boolean = false,
    defaultHint: String,
    showInfoForItem: ((TranslatorItem) -> Boolean)? = null,
    infoTitle: String = "",
    infoContent: String = "",
    onConfirm: (id: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedId by remember(items, currentSelectedId) {
        val validId = if (currentSelectedId.isNotBlank() && items.any { it.id == currentSelectedId }) {
            currentSelectedId
        } else {
            items.firstOrNull()?.id ?: ""
        }
        mutableStateOf(validId)
    }
    var setAsDefault by remember { mutableStateOf(isSetAsDefault) }
    var showInfoPanel by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            showInfoPanel = false
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    items.forEach { item ->
                        PickerOptionItem(
                            item = item,
                            selected = selectedId == item.id,
                            onClick = { selectedId = item.id },
                            showInfo = showInfoForItem?.invoke(item) == true,
                            onInfoClick = { showInfoPanel = true }
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setAsDefault = !setAsDefault }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it },
                        modifier = Modifier.scale(0.78f)
                    )
                    Text(
                        text = defaultHint,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = { onConfirm(selectedId, setAsDefault) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    enabled = selectedId.isNotBlank()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }

    if (showInfoPanel && infoTitle.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showInfoPanel = false },
            title = {
                Text(
                    text = infoTitle,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = infoContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoPanel = false }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@Composable
fun TranslatePickerSheet(
    items: List<TranslatorItem>,
    currentSelectedId: String = "",
    isSetAsDefault: Boolean = false,
    onConfirm: (translatorId: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val infoTitle = stringResource(R.string.ai_translator_info_title)
    val infoContent = stringResource(R.string.ai_translator_info_content)

    AppPickerSheet(
        title = stringResource(R.string.select_translator),
        items = items,
        currentSelectedId = currentSelectedId,
        isSetAsDefault = isSetAsDefault,
        defaultHint = stringResource(R.string.default_translator_hint),
        showInfoForItem = { it.id == com.wxn.reader.data.remote.api.Constants.AI_TRANSILATOR },
        infoTitle = infoTitle,
        infoContent = infoContent,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun DictionaryPickerSheet(
    items: List<TranslatorItem>,
    currentSelectedId: String = "",
    isSetAsDefault: Boolean = false,
    onConfirm: (dictId: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AppPickerSheet(
        title = stringResource(R.string.select_dictionary_service),
        items = items,
        currentSelectedId = currentSelectedId,
        isSetAsDefault = isSetAsDefault,
        defaultHint = stringResource(R.string.default_dictionary_hint),
        showInfoForItem = { it.id == com.wxn.reader.data.remote.api.Constants.BUILT_IN_DICTIONARY },
        infoTitle = stringResource(R.string.ai_dictionary_info_title),
        infoContent = stringResource(R.string.ai_dictionary_info_content),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun PickerOptionItem(
    item: TranslatorItem,
    selected: Boolean,
    onClick: () -> Unit,
    showInfo: Boolean,
    onInfoClick: () -> Unit
) {
    val context = LocalContext.current
    val iconSize = 40.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp)
            .selectable(selected = selected, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconPainter = remember(item.id, item.isBuiltIn) {
                runCatching {
                    val pm = context.packageManager
                    val pkg = if (item.isBuiltIn) context.packageName else item.packageName
                    val drawable = pkg?.let { pm.getApplicationIcon(it) }
                    drawable?.toBitmapPainter()
                }.getOrNull()
            }

            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = item.name,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = item.name,
                    modifier = Modifier.size(iconSize)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (showInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp)
                                .clickable { onInfoClick() },
                            contentDescription = "info"
                        )
                    }
                }
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun Drawable.toBitmapPainter(): BitmapPainter {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return BitmapPainter(bitmap.asImageBitmap())
}

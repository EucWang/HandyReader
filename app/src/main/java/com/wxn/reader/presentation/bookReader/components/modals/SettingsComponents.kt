package com.wxn.reader.presentation.bookReader.components.modals

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.PathUtil
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.ui.theme.visibleIn

enum class ReadUiEditType {
    ColorType_TEXT,
    ColorType_BACKGROUND,
    FontType
}

@Composable
fun ReadBgSelectionDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSelect: (type: Int) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(stringResource(R.string.change_reading_background))
        },
        text = {
            Column() {
                Button(
                    onClick = {
                        onSelect(1)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.select_from_online))
                }
                Button(
                    onClick = {
                        onSelect(2)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.select_from_gallery))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun FontImportModeDialog(
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    onSelectDirectory: () -> Unit,
) {
   val navController = LocalNavController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.import_font_dialog_title))
        },
        text = {
            Column {
                Button(
                    onClick = {
                        onSelectFile()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.import_font_file))
                }
                Button(
                    onClick = {
                        onSelectDirectory()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.import_font_directory))
                }
                Button(
                    onClick = {
                        navController.navigate(Screens.FontManagementScreen.route)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.import_font_network))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ColorSection(
    title: String,
    currentColor: Color,
    predefinedColors: List<com.wxn.reader.ui.theme.PredefinedColor>,
    effectiveIsDark: Boolean,
    asBackground: Boolean,
    onColorSelected: (Color) -> Unit,
    onCustomColorClicked: () -> Unit,
) {
    // 按当前模式 + 用途过滤可见色（visibleIn 四象限：背景色明暗==模式，文本色明暗!=模式）
    val visibleColors = predefinedColors.filter {
        it.visibleIn(modeIsDark = effectiveIsDark, asBackground = asBackground)
    }
    // 色名反查：在可见色中按色值匹配，取 nameRes（响应式 stringResource，切语言刷新）
    val matchedNameRes = visibleColors.firstOrNull { it.value == currentColor }?.nameRes
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = matchedNameRes?.let { stringResource(it) } ?: "",
        style = MaterialTheme.typography.titleMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        visibleColors.forEach { pc ->
            item {
                ColorBox(
                    color = pc.value,
                    isSelected = pc.value == currentColor,
                    onClick = { onColorSelected(pc.value) }
                )
            }
        }
        item {
            // 自定义色：当前色不在任何可见预设中时选中
            ColorBox(
                color = currentColor,
                isSelected = visibleColors.none { it.value == currentColor },
                onClick = onCustomColorClicked,
                isCustomColor = true
            )
        }
    }
}

@Composable
private fun ColorBox(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    isCustomColor: Boolean = false
) {
    // luminance 自适应边框：深色 swatch 用浅边框，浅色用深边框（根治墨色 swatch 黑边框不可见 P1）
    val borderColor = if (isSelected) {
        if (color.luminance() < 0.5f) Color(0xFFFFF8DC) else Color.Black
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(shape = RoundedCornerShape(40.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(40.dp)
            )
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isCustomColor) {
            Icon(
                imageVector = Icons.Default.ColorLens,
                contentDescription = "Custom Color Picker",
                tint = if (color.luminance() < 0.5f) Color.White else Color.Black
            )
        }
    }
}

@Composable
private fun ImageBox(
    image: String,
    isSelected: Boolean,
    isCustomImage: Boolean = false,
    onClick: (String) -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(shape = RoundedCornerShape(40.dp))
            .border(
                width = 2.dp,
                color = if (isSelected) {
                    Color(0xFFFFF8DC)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(40.dp)
            )
            .background(Color(0xFF575757))
            .clickable(onClick = {
                onClick.invoke(image)
            }),
        contentAlignment = Alignment.Center
    ) {
        Image(
            if (isCustomImage) {
                if (image.startsWith("/")) {
                    rememberAsyncImagePainter(image)
                } else {
                    rememberAsyncImagePainter(PathUtil.getDownloadFilePath(context, DownloadFileType.BG_IMAGE, image, image))
                }
            } else {
                when (image) {
                    else -> painterResource(com.wxn.bookread.R.drawable.ic_bg_none)
                }
            },
            contentDescription = image,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(40.dp)),
            contentScale = ContentScale.FillBounds
        )

        if (isCustomImage) {
            Icon(
                imageVector = Icons.Default.PhotoSizeSelectActual,
                contentDescription = "Custom Image Picker",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

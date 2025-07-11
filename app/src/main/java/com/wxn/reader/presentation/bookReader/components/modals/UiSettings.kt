package com.wxn.reader.presentation.bookReader.components.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.elixer.palette.Presets
import com.elixer.palette.constraints.HorizontalAlignment
import com.elixer.palette.constraints.VerticalAlignment
import com.wxn.base.ext.toComposeColor
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.ColorPicker

enum class ColorType(val displayName: String) {
    BACKGROUND("Background"),
    TEXT("Text"),
}

//
//@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
//@Composable
//fun UiSettings(
//    navController: NavHostController,
////    purchaseHelper: PurchaseHelper,
//    appPreferences: AppPreferences,
//    viewModel: BookReaderViewModel,
//    readerPreferences: ReaderPreferences,
//    onDismiss: () -> Unit,
//) {
//    var isPaletteVisible by remember { mutableStateOf(false) }
//    var editingColorType by remember { mutableStateOf(ColorType.BACKGROUND) }
//
//    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
////    var showPremiumModal by remember { mutableStateOf(false) }
//
//
//    val predefinedColors = remember {
//        mapOf(
//            "White" to Color.White,
//            "Black" to Color.Black,
//            "Gray" to Color(0xFFD8D3D6),
//            "Blue Gray" to Color(0xFFDBE1F1),
//        )
//    }
//
//    LaunchedEffect(isPaletteVisible) {
//        if (isPaletteVisible && appPreferences.isPremium) sheetState.expand()
//    }
//
//    ModalBottomSheet(
//        onDismissRequest = onDismiss,
//        sheetState = sheetState,
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//
//            Box(
//                modifier = Modifier.fillMaxWidth(),
//                contentAlignment = Alignment.CenterEnd
//            ) {
//                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
//                    Text(
//                        text = stringResource(R.string.color_settings),
//                        style = MaterialTheme.typography.titleMedium,
//                    )
//                }
//                TextButton(
//                    onClick = {
//                        viewModel.resetUiPreferences()
//                    },
//                    colors = ButtonDefaults.textButtonColors(
//                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
//                    ),
//                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
//                ) {
//                    Text(
//                        text = stringResource(R.string.reset),
//                        style = MaterialTheme.typography.labelSmall
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            ColorSection(
//                title = stringResource(R.string.background_color),
//                currentColor = readerPreferences.backgroundColor.toComposeColor(),
//                predefinedColors = predefinedColors,
//                onColorSelected = { color ->
//                    viewModel.updateReaderPreferences(
//                        readerPreferences.copy(
//                            backgroundColor = color.toArgb(),
//                            textColor = if (color == Color.Black) Color.White.toArgb() else Color.Black.toArgb()
//                        )
//                    )
//                },
//                onCustomColorClicked = {
//                    if (appPreferences.isPremium) {
//                        editingColorType = ColorType.BACKGROUND
//                        isPaletteVisible = true
//                    } else {
//                        navController.navigate(Screens.PremiumScreen.route);
////                        viewModel.purchasePremium(purchaseHelper)
////                        showPremiumModal = true
//                    }
//
//                },
//            )
//
//            Spacer(modifier = Modifier.height(20.dp))
//            HorizontalDivider()
//            Spacer(modifier = Modifier.height(20.dp))
//
//            ColorSection(
//                title = stringResource(R.string.text_color),
//                currentColor = readerPreferences.textColor.toComposeColor(),
//                predefinedColors = predefinedColors,
//                onColorSelected = { color ->
//                    viewModel.updateReaderPreferences(readerPreferences.copy(textColor = color.toArgb()))
//                },
//                onCustomColorClicked = {
//                    if (appPreferences.isPremium) {
//                        editingColorType = ColorType.TEXT
//                        isPaletteVisible = true
//                    } else {
//                        navController.navigate(Screens.PremiumScreen.route);
////                        viewModel.purchasePremium(purchaseHelper)
////                        showPremiumModal = true
//                    }
//
//                },
//            )
//
//            Spacer(modifier = Modifier.height(20.dp))
//            HorizontalDivider()
//            Spacer(modifier = Modifier.height(20.dp))
//
//
//            AnimatedVisibility(
//                visible = isPaletteVisible && appPreferences.isPremium
//            ) {
//                Column(
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Spacer(modifier = Modifier.height(0.dp))
//                    Text(
//                        stringResource(R.string.select_color, editingColorType.displayName),
//                        style = MaterialTheme.typography.titleMedium,
//                        textAlign = TextAlign.Center
//                    )
//                    ColorPicker(
//                        isVisible = isPaletteVisible,
//                        defaultColor = when (editingColorType) {
//                            ColorType.BACKGROUND -> readerPreferences.backgroundColor.toComposeColor()
//                            ColorType.TEXT -> readerPreferences.textColor.toComposeColor()
//                        },
//                        buttonSize = 70.dp,
//                        swatches = Presets.material(),
//                        innerRadius = 200f,
//                        strokeWidth = 80f,
//                        spacerRotation = 0f,
//                        spacerOutward = 3f,
//                        verticalAlignment = VerticalAlignment.Bottom,
//                        horizontalAlignment = HorizontalAlignment.Center,
//                        onColorSelected = { color ->
//                            isPaletteVisible = false
//                            viewModel.updateReaderPreferences(
//                                when (editingColorType) {
//                                    ColorType.BACKGROUND -> readerPreferences.copy(
//                                        backgroundColor = color.toArgb(),
//                                        textColor = if (color == Color.Black) Color.White.toArgb() else Color.Black.toArgb()
//                                    )
//
//                                    ColorType.TEXT -> readerPreferences.copy(textColor = color.toArgb())
//                                }
//                            )
//                        }
//                    )
//                }
//            }
//        }
//    }
//
////    if (showPremiumModal) {
////        PremiumModal(
////            purchaseHelper = purchaseHelper,
////            hidePremiumModal = { showPremiumModal = false }
////        )
////    }
//
//}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiSettings(
    navController: NavHostController,
    appPreferences: AppPreferences,
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    onDismiss: () -> Unit,
) {
    var isPaletteVisible by remember { mutableStateOf(false) }
    var editingColorType by remember { mutableStateOf(ColorType.BACKGROUND) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//    var showPremiumModal by remember { mutableStateOf(false) }


    val predefinedColors = remember {
        mapOf(
            com.wxn.reader.ui.theme.stringResource(R.string.white) to Color.White,
            com.wxn.reader.ui.theme.stringResource(R.string.black) to Color.Black,
            com.wxn.reader.ui.theme.stringResource(R.string.gray) to Color(0xFFEBEBE4),
            com.wxn.reader.ui.theme.stringResource(R.string.light_yellow) to Color(0xFFFAF9DE),
            com.wxn.reader.ui.theme.stringResource(R.string.pale_brown) to Color(0xFFFFF2E2),
        )
    }

    val predefinedImages = remember {
        mapOf(
            com.wxn.reader.ui.theme.stringResource(R.string.none) to "",
            com.wxn.reader.ui.theme.stringResource(R.string.parchment) to "ic_read_bg1",            //羊皮纸
            com.wxn.reader.ui.theme.stringResource(R.string.old_paper) to "ic_read_bg2",            //旧纸张
            com.wxn.reader.ui.theme.stringResource(R.string.leather) to "ic_read_bg3",              //皮革纸
            com.wxn.reader.ui.theme.stringResource(R.string.broken_parchment) to "ic_read_bg4",    //破羊皮纸
        )
    }

    LaunchedEffect(isPaletteVisible) {
        if (isPaletteVisible && appPreferences.isPremium) sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.color_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.resetUiPreferences()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reset),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ColorSection(
                title = stringResource(R.string.background_color),
                currentColor = readerPreferences.backgroundColor.toComposeColor(),
                predefinedColors = predefinedColors,
                onColorSelected = { color ->
                    viewModel.updateReaderPreferences(
                        readerPreferences.copy(
                            backgroundColor = color.toArgb(),
                            textColor = if (color == Color.Black) Color.White.toArgb() else Color.Black.toArgb()
                        )
                    )
                },
                onCustomColorClicked = {
                    if (appPreferences.isPremium) {
                        editingColorType = ColorType.BACKGROUND
                        isPaletteVisible = true
                    } else {
                        navController.navigate(Screens.PremiumScreen.route);
//                        viewModel.purchasePremium(purchaseHelper)
//                        showPremiumModal = true
                    }

                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            ImageSection(
                title = stringResource(R.string.background_image),
                currentImage = readerPreferences.backgroundImage,
                predefinedImages = predefinedImages,
                onImageSelected = { image ->
                    viewModel.updateReaderPreferences(
                        readerPreferences.copy(backgroundImage =image)
                    )
                },
//                onCustomColorClicked = {
//                    if (appPreferences.isPremium) {
//                        editingColorType = ColorType.BACKGROUND
//                        isPaletteVisible = true
//                    } else {
//                        navController.navigate(Screens.PremiumScreen.route);
////                        viewModel.purchasePremium(purchaseHelper)
////                        showPremiumModal = true
//                    }
//
//                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))


            ColorSection(
                title = stringResource(R.string.text_color),
                currentColor = readerPreferences.textColor.toComposeColor(),
                predefinedColors = predefinedColors,
                onColorSelected = { color ->
                    viewModel.updateReaderPreferences(readerPreferences.copy(textColor = color.toArgb()))
                },
                onCustomColorClicked = {
                    if (appPreferences.isPremium) {
                        editingColorType = ColorType.TEXT
                        isPaletteVisible = true
                    } else {
                        navController.navigate(Screens.PremiumScreen.route);
//                        viewModel.purchasePremium(purchaseHelper)
//                        showPremiumModal = true
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = isPaletteVisible && appPreferences.isPremium
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(
                        stringResource(R.string.select_color, editingColorType.displayName),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    ColorPicker(
                        isVisible = isPaletteVisible,
                        defaultColor = when (editingColorType) {
                            ColorType.BACKGROUND -> readerPreferences.backgroundColor.toComposeColor()
                            ColorType.TEXT -> readerPreferences.textColor.toComposeColor()
                        },
                        buttonSize = 70.dp,
                        swatches = Presets.material(),
                        innerRadius = 200f,
                        strokeWidth = 80f,
                        spacerRotation = 0f,
                        spacerOutward = 3f,
                        verticalAlignment = VerticalAlignment.Bottom,
                        horizontalAlignment = HorizontalAlignment.Center,
                        onColorSelected = { color ->
                            isPaletteVisible = false
                            viewModel.updateReaderPreferences(
                                when (editingColorType) {
                                    ColorType.BACKGROUND -> readerPreferences.copy(
                                        backgroundColor = color.toArgb(),
                                        textColor = if (color == Color.Black) Color.White.toArgb() else Color.Black.toArgb()
                                    )

                                    ColorType.TEXT -> readerPreferences.copy(textColor = color.toArgb())
                                }
                            )
                        }
                    )
                }
            }
        }
    }

//    if (showPremiumModal) {
//        PremiumModal(
//            purchaseHelper = purchaseHelper,
//            hidePremiumModal = { showPremiumModal = false }
//        )
//    }

}



@Composable
private fun ImageSection(
    title: String,
    currentImage: String,
    predefinedImages: Map<String, String>,
    onImageSelected: (String) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = predefinedImages.entries.find { it.value == currentImage }?.key.orEmpty(), // ?: "Custom Color",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        predefinedImages.forEach { (_, image) ->
            ImageBox(
                image = image,
                isSelected = image == currentImage,
                onClick = { onImageSelected(image) }
            )
        }
    }
}




@Composable
private fun ColorSection(
    title: String,
    currentColor: Color,
    predefinedColors: Map<String, Color>,
    onColorSelected: (Color) -> Unit,
    onCustomColorClicked: () -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = predefinedColors.entries.find { it.value == currentColor }?.key ?: "Custom Color",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        predefinedColors.forEach { (_, color) ->
            ColorBox(
                color = color,
                isSelected = color == currentColor,
                onClick = { onColorSelected(color) }
            )
        }
        // Custom Color Picker
        ColorBox(
            color = currentColor,
            isSelected = !predefinedColors.containsValue(currentColor),
            onClick = onCustomColorClicked,
            isCustomColor = true
        )
    }
}

@Composable
private fun ColorBox(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    isCustomColor: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(shape = RoundedCornerShape(40.dp))
            .border(
                width = 2.dp,
                color = if (isSelected) {
                    if (color == Color.Black) Color(0xFFFFF8DC) else Color.Black
                } else {
                    Color.Transparent
                },
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
                tint = if (color == Color.Black) Color.White else Color.Black
            )
        }
    }
}


@Composable
private fun ImageBox(
    image: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
                when(image) {
                    "ic_read_bg1" -> painterResource(com.wxn.bookread.R.drawable.ic_read_bg1)
                    "ic_read_bg2" -> painterResource(com.wxn.bookread.R.drawable.ic_read_bg2)
                    "ic_read_bg3" -> painterResource(com.wxn.bookread.R.drawable.ic_read_bg3)
                    "ic_read_bg4" -> painterResource(com.wxn.bookread.R.drawable.ic_read_bg4)
                    else -> painterResource(com.wxn.bookread.R.drawable.ic_bg_none)
                },
            contentDescription = image,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(40.dp)),
            contentScale = ContentScale.FillBounds
        )
    }
}


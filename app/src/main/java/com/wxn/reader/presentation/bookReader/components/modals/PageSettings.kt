package com.wxn.reader.presentation.bookReader.components.modals


import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.bookread.data.model.preference.ReaderPreferences
//import com.wxn.reader.data.model.toCompose
//import com.wxn.reader.data.model.toRedium
//import com.wxn.reader.presentation.bookReader.BookReaderViewModel
import com.wxn.reader.presentation.mainReader.MainReadViewModel
//import org.readium.r2.navigator.preferences.TextAlign
//import org.readium.r2.shared.ExperimentalReadiumApi
import java.util.Locale
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun PageSettings(
//    viewModel: BookReaderViewModel,
//    readerPreferences: ReaderPreferences,
//    onDismiss: () -> Unit,
//) {
//    val context = LocalContext.current
//    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//
//
//
//    val minPageMargins = 0.0
//    val maxPageMargins = 5.0
//
//    ModalBottomSheet(
//        onDismissRequest = onDismiss,
//        sheetState = sheetState
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 16.dp)
//                .padding(horizontal = 16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//
//
//            Box(
//                modifier = Modifier.fillMaxWidth(),
//                contentAlignment = Alignment.CenterEnd
//            ) {
//                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
//                    Text(
//                        text = stringResource(R.string.page_settings),
//                        style = MaterialTheme.typography.titleMedium,
//                    )
//                }
//                TextButton(
//                    onClick = {
//                        viewModel.resetPagePreferences()
//                    },
//                    colors = ButtonDefaults.textButtonColors(
//                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
//                    ),
//                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
//
//
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
//
//            //can be changed on publisher styles
//            SettingsRange(
//                title = stringResource(R.string.page_margins),
//                value = readerPreferences.pageHorizontalMargins,
//                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(pageHorizontalMargins = it) ) },
//
//                valueRange = minPageMargins..maxPageMargins,
//                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) }
//            )
//
//
//            //cannot be changed on publisher styles
//            SettingsRange(
//                title = stringResource(R.string.paragraph_indent),
//                value = readerPreferences.paragraphIndent,
//                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(paragraphIndent = it) ) },
//                valueRange = 0.0..3.0,
//                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
//                enabled = !readerPreferences.publisherStyles
//            )
//
//            //cannot be changed on publisher styles
//            SettingsRange(
//                title = stringResource(R.string.paragraph_spacing),
//                value = readerPreferences.paragraphSpacing,
//                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(paragraphSpacing = it) ) },
//                valueRange = 0.0..3.0,
//                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
//                enabled = !readerPreferences.publisherStyles
//            )
//
//
//            Column(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                Text(stringResource(R.string.text_align), style = MaterialTheme.typography.titleMedium)
//                Row(
//                    modifier = Modifier,
//                    horizontalArrangement = Arrangement.spacedBy(16.dp)
//                ) {
//                    listOf(
//                        TextAlign.LEFT to stringResource(R.string.left),
//                        TextAlign.JUSTIFY to stringResource(R.string.justify),
//                        TextAlign.RIGHT to stringResource(R.string.right)
//                    ).forEach { (alignment, label) ->
//                        FilledTonalButton(
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = if (readerPreferences.textAlign.toRedium() == alignment) {
//                                    MaterialTheme.colorScheme.primaryContainer
//                                } else {
//                                    MaterialTheme.colorScheme.surfaceVariant
//                                },
//                                contentColor = if (readerPreferences.textAlign.toRedium() == alignment) {
//                                    MaterialTheme.colorScheme.onPrimaryContainer
//                                } else {
//                                    MaterialTheme.colorScheme.onSurfaceVariant
//                                }
//                            ),
//                            onClick = {
//                                if (readerPreferences.publisherStyles) {
//                                    Toast.makeText(
//                                        context,
//                                        "Cannot change settings on publisher styles",
//                                        Toast.LENGTH_SHORT
//                                    ).show()
//                                } else {
//                                    viewModel.updateReaderPreferences( readerPreferences.copy(textAlign = alignment.toCompose()) )
//                                }
//
//                            }
//                        ) {
//                            Text(text = label)
//                        }
//                    }
//                }
//            }
//
//
//        }
//    }
//}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSettings(
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val minPageMargins = 0.5
    val maxPageMargins = 5.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.page_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.resetPagePreferences()
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

            Spacer(modifier = Modifier.height(16.dp))


            //can be changed on publisher styles
            SettingsRange(
                title = stringResource(R.string.page_horizontal_margins),
                value = readerPreferences.pageHorizontalMargins,
                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(pageHorizontalMargins = it) ) },

                valueRange = minPageMargins..maxPageMargins,
                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) }
            )

            //can be changed on publisher styles
            SettingsRange(
                title = stringResource(R.string.page_vertial_margins),
                value = readerPreferences.pageVerticalMargins,
                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(pageVerticalMargins = it) ) },

                valueRange = minPageMargins..maxPageMargins,
                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) }
            )


            //cannot be changed on publisher styles
            SettingsRange(
                title = stringResource(R.string.paragraph_indent),
                value = readerPreferences.paragraphIndent,
                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(paragraphIndent = it) ) },
                valueRange = 0.0..3.0,
                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                enabled = !readerPreferences.publisherStyles
            )

            //cannot be changed on publisher styles
            SettingsRange(
                title = stringResource(R.string.paragraph_spacing),
                value = readerPreferences.paragraphSpacing,
                onValueChange = { viewModel.updateReaderPreferences( readerPreferences.copy(paragraphSpacing = it) ) },
                valueRange = 0.0..3.0,
                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                enabled = !readerPreferences.publisherStyles
            )

            //TODO Text Align
//            Column(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                Text(stringResource(R.string.text_align), style = MaterialTheme.typography.titleMedium)
//                Row(
//                    modifier = Modifier,
//                    horizontalArrangement = Arrangement.spacedBy(16.dp)
//                ) {
//                    listOf(
//                        TextAlign.Left to stringResource(R.string.left),
//                        TextAlign.Justify to stringResource(R.string.justify),
//                        TextAlign.Right to stringResource(R.string.right)
//                    ).forEach { (alignment, label) ->
//                        FilledTonalButton(
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = if (readerPreferences.textAlign == alignment) {
//                                    MaterialTheme.colorScheme.primaryContainer
//                                } else {
//                                    MaterialTheme.colorScheme.surfaceVariant
//                                },
//                                contentColor = if (readerPreferences.textAlign == alignment) {
//                                    MaterialTheme.colorScheme.onPrimaryContainer
//                                } else {
//                                    MaterialTheme.colorScheme.onSurfaceVariant
//                                }
//                            ),
//                            onClick = {
//                                if (readerPreferences.publisherStyles) {
//                                    Toast.makeText(
//                                        context,
//                                        "Cannot change settings on publisher styles",
//                                        Toast.LENGTH_SHORT
//                                    ).show()
//                                } else {
//                                    viewModel.updateReaderPreferences( readerPreferences.copy(textAlign = alignment) )
//                                }
//
//                            }
//                        ) {
//                            Text(text = label)
//                        }
//                    }
//                }
//            }
        }
    }
}

//@Composable
//fun SettingsSlider(
//    title: String,
//    value: Double,
//    onValueChange: (Double) -> Unit,
//    valueRange: ClosedFloatingPointRange<Double>,
//    valueDisplay: (Double) -> String
//) {
//    Text(title, style = MaterialTheme.typography.titleMedium)
//    Slider(
//        value = value.toFloat(),
//        onValueChange = { onValueChange(it.toDouble()) },
//        valueRange = valueRange.start.toFloat()..valueRange.endInclusive.toFloat()
//    )
//    Text(valueDisplay(value), style = MaterialTheme.typography.bodyMedium)
//    Spacer(modifier = Modifier.height(16.dp))
//}

//cannot be changed on publisher styles
//            SettingsDropdown(
//                title = "Text Align",
//                options = listOf(TextAlign.LEFT, TextAlign.RIGHT, TextAlign.JUSTIFY),
//                selectedOption = updatedPreferences.textAlign,
//                onOptionSelected = { updatePreference { copy(textAlign = it) } },
//                enabled = !readerPreferences.publisherStyles
//            )

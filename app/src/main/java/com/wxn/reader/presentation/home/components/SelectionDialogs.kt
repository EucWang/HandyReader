package com.wxn.reader.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.bean.Book
import com.wxn.reader.R
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.presentation.home.HomeViewModel
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun ShelfPickerDialog(
    selectedBooks: List<Book>,
    shelves: List<Shelf>,
    viewModel: HomeViewModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onNavigateToShelves: () -> Unit,
) {
    var selectedShelves by remember { mutableStateOf(setOf<Shelf>()) }
    var unselectedShelves by remember { mutableStateOf(setOf<Shelf>()) }
    var initialShelvesState by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }

    LaunchedEffect(shelves, selectedBooks) {
        val stateMap = mutableMapOf<Long, Boolean>()
        shelves.forEach { shelf ->
            val books = viewModel.getBooksForShelfSelection(shelf.id).firstOrNull() ?: emptyList()
            stateMap[shelf.id] = selectedBooks.any { selected -> books.any { it.id == selected.id } }
        }
        initialShelvesState = stateMap
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.manage_bookshelf)) },
        text = {
            if (shelves.isEmpty()) {
                Text(stringResource(R.string.you_don_t_have_any_shelves_yet_create_a_shelf_to_add_books))
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    shelves.forEach { shelf ->
                        val isChecked = initialShelvesState[shelf.id] ?: false
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        selectedShelves += shelf
                                        unselectedShelves -= shelf
                                    } else {
                                        selectedShelves -= shelf
                                        unselectedShelves += shelf
                                    }
                                    initialShelvesState =
                                        initialShelvesState.toMutableMap().apply {
                                            this[shelf.id] = isChecked
                                        }
                                }
                            )
                            Text(shelf.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        onNavigateToShelves()
                    }
                ) {
                    Text(stringResource(R.string.shelves))
                }
                Row {
                    TextButton(
                        onClick = {
                            selectedShelves = emptySet()
                            unselectedShelves = emptySet()
                            onCancel()
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            if (selectedShelves.isNotEmpty()) {
                                viewModel.addBooksToShelves(
                                    selectedBooks.map { it.id },
                                    selectedShelves.map { it.id }
                                )
                            }
                            if (unselectedShelves.isNotEmpty()) {
                                viewModel.removeBooksFromShelves(
                                    selectedBooks.map { it.id },
                                    unselectedShelves.map { it.id }
                                )
                            }
                            selectedShelves = emptySet()
                            unselectedShelves = emptySet()
                            onConfirm()
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_books)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.are_you_sure_you_want_to_remove_the_selected_books)
                )
            }
        },
        confirmButton = {
            TextButton(
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                onClick = {
                    onConfirm()
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

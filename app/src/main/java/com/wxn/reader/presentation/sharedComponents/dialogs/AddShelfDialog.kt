package com.wxn.reader.presentation.sharedComponents.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.base.util.ToastUtil
import com.wxn.reader.R

@Composable
fun AddShelfDialog(
    newShelfName: String,
    onShelfNameChange: (String) -> Unit,
    shelves: List<String>,
    onAddShelf: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.add_new_shelf)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = onShelfNameChange,
                    label = { Text(stringResource(R.string.shelf_name)) }
                )

                Text(text = stringResource(R.string.required), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        newShelfName.isEmpty() || newShelfName.isBlank() -> {
                            ToastUtil.show(R.string.shelf_name_is_required)
                        }

                        shelves.any { it.equals(newShelfName, ignoreCase = true) } -> {
                            ToastUtil.show(R.string.shelf_name_already_exists)
                        }

                        else -> {
                            onAddShelf(newShelfName.replace("\n", " ").trim())
                            onShelfNameChange("")
                            onDismiss()
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
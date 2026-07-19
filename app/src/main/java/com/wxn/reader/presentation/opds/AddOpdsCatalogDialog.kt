package com.wxn.reader.presentation.opds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.domain.use_case.opds.ValidateOpdsUrlUseCase
import kotlinx.coroutines.launch

@Composable
fun AddOpdsCatalogDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, username: String?, password: String?) -> Unit,
    validateUseCase: ValidateOpdsUrlUseCase = androidx.hilt.navigation.compose.hiltViewModel<ValidateOpdsViewModel>().validateUseCase
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<String?>(null) }
    var isValid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.opds_add_catalog)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isValid = false
                        validationResult = null
                    },
                    label = { Text(stringResource(R.string.opds_catalog_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.opds_catalog_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.opds_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.opds_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isValidating = true
                            validationResult = null
                            val result = validateUseCase(
                                url,
                                username.ifBlank { null },
                                password.ifBlank { null }
                            )
                            isValidating = false
                            when (result) {
                                is ValidateOpdsUrlUseCase.ValidationResult.Success -> {
                                    validationResult = context.getString(R.string.opds_validation_success)
                                    isValid = true
                                    if (name.isBlank()) {
                                        name = result.feed.title
                                    }
                                }
                                is ValidateOpdsUrlUseCase.ValidationResult.AuthRequired -> {
                                    validationResult = context.getString(R.string.opds_auth_required)
                                }
                                is ValidateOpdsUrlUseCase.ValidationResult.Error -> {
                                    validationResult = result.message
                                }
                            }
                        }
                    },
                    enabled = url.isNotBlank() && !isValidating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.CenterVertically),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(stringResource(R.string.opds_test_connection))
                }

                validationResult?.let { message ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.ifBlank { url },
                        url,
                        username.ifBlank { null },
                        password.ifBlank { null }
                    )
                },
                enabled = url.isNotBlank() && (isValid || name.isNotBlank())
            ) {
                Text(stringResource(R.string.opds_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.opds_cancel))
            }
        }
    )
}

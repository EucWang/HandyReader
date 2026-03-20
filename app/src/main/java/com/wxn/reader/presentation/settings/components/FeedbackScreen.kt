package com.wxn.reader.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.settings.viewmodels.FeedbackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    FeedbackScreenView(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackScreenView(viewModel: FeedbackViewModel) {
    val navController: NavHostController = LocalNavController.current

    val emailError by viewModel.emailError.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val submitState by viewModel.submitState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // 处理提交状态变化
    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is FeedbackViewModel.SubmitState.Success -> {
                snackbarHostState.showSnackbar(
                    message = state.message.ifEmpty { "Feedback submitted successfully" },
                    duration = SnackbarDuration.Short
                )
//                delay(1000)
//                viewModel.resetForm()
            }
            is FeedbackViewModel.SubmitState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = { Text(stringResource(R.string.feedback)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        viewModel.setName(it)
                    },
                    label = { Text(stringResource(R.string.feedback_label_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Enter your nickname (optional)"
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        viewModel.setEmail(it)
                    },
                    label = { Text(stringResource(R.string.feedback_label_email)) },
                    isError = emailError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Enter your email address (required)"
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )

                // 更新错误提示，只在有输入且错误时显示
                if (emailError && email.isNotEmpty()) {
                    Text(
                        stringResource(R.string.feedback_email_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Feedback type selection
                Text(
                    stringResource(R.string.feedback_type),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 第一行：Bug 和 Feature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedbackTypeChip(
                        label = stringResource(R.string.feedback_type_bug),
                        selected = type == 0,
                        onClick = { viewModel.setType(0) },
                        modifier = Modifier.weight(1f)
                    )

                    FeedbackTypeChip(
                        label = stringResource(R.string.feedback_type_more_feature),
                        selected = type == 1,
                        onClick = { viewModel.setType(1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 第二行：Other（单独一行）
                FeedbackTypeChip(
                    label = stringResource(R.string.feedback_type_other),
                    selected = type == 2,
                    onClick = { viewModel.setType(2) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    label = {
                        Text(stringResource(R.string.description))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Enter your feedback content (10-500 characters)"
                        },
                    minLines = 5,
                    maxLines = 5,
                    onValueChange = {
                        if (it.length <= 500) {
                            viewModel.setContent(it)
                        }
                    },
                    isError = content.isNotEmpty() && content.length < 10,
                    supportingText = {
                        Text(
                            "${content.length}/500",
                            color = when {
                                content.length < 10 && content.isNotEmpty() ->
                                    MaterialTheme.colorScheme.error
                                content.length > 450 ->
                                    MaterialTheme.colorScheme.tertiary
                                else ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )

                // 添加验证提示
                if (content.isNotEmpty() && content.length < 10) {
                    Text(
                        stringResource(R.string.feedback_content_too_short),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val isLoading = submitState is FeedbackViewModel.SubmitState.Loading

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Submit Button
                    Button(
                        onClick = {
                            if (!isLoading) {
                                viewModel.submit()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Submit feedback"
                            },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.submit))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
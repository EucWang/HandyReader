package com.wxn.reader.presentation.opds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.wxn.reader.R
import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.opds.viewmodels.OpdsCatalogListViewModel
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsCatalogListScreen(
    viewModel: OpdsCatalogListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var catalogToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val catalogToDelete = catalogToDeleteId?.let { id ->
        uiState.predefinedCatalogs.find { it.id == id }
            ?: uiState.customCatalogs.find { it.id == id }
    }
    val navController = LocalNavController.current
    val syncErrorMessage = stringResource(R.string.opds_sync_error)

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(uiState.syncError) {
        if (uiState.syncError) {
            snackbarHostState.showSnackbar(syncErrorMessage)
            viewModel.clearSyncError()
        }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(R.string.opds_title)) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.opds_add_catalog))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.opds_browse_back))
                    }
                },
            )
        },

        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading && uiState.predefinedCatalogs.isEmpty()
                    && uiState.customCatalogs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp, start = 0.dp, end = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.predefinedCatalogs.isNotEmpty()) {
                        item(key = "header_predefined") {
                            SectionHeader(title = stringResource(R.string.opds_recommended_library))
                        }
                        items(
                            uiState.predefinedCatalogs,
                            key = { "predefined_${it.id}" }
                        ) { catalog ->
                            CatalogCard(
                                catalog = catalog,
                                onClick = {
                                    navController.navigate(Screens.OpdsBrowseScreen.createRoute(catalog.id))
                                },
                                onDelete = {
                                    catalogToDeleteId = catalog.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    item(key = "header_custom") {
                        SectionHeader(title = stringResource(R.string.opds_custom_library))
                    }

                    if (uiState.customCatalogs.isNotEmpty()) {
                        items(
                            uiState.customCatalogs,
                            key = { "custom_${it.id}" }
                        ) { catalog ->
                            CatalogCard(
                                catalog = catalog,
                                onClick = {
                                    navController.navigate(Screens.OpdsBrowseScreen.createRoute(catalog.id))
                                },
                                onDelete = {
                                    catalogToDeleteId = catalog.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    item(key = "add_custom_card") {
                        AddCatalogCard(
                            onClick = { showAddDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddOpdsCatalogDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, username, password ->
                viewModel.addCatalog(name, url, username, password)
                showAddDialog = false
            }
        )
    }

    catalogToDelete?.let { catalog ->
        AlertDialog(
            onDismissRequest = { catalogToDeleteId = null },
            title = { Text(stringResource(R.string.opds_delete_catalog)) },
            text = {
                if (catalog.isPredefined) {
                    Text(stringResource(R.string.opds_delete_predefined_confirm, catalog.name))
                } else {
                    Text(stringResource(R.string.opds_delete_confirm))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCatalog(catalog.id)
                        catalogToDeleteId = null
                    }
                ) {
                    Text(stringResource(R.string.opds_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { catalogToDeleteId = null }) {
                    Text(stringResource(R.string.opds_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 6.dp, horizontal = 24.dp)
    )
}

@Composable
private fun AddCatalogCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.opds_add_custom_catalog),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CatalogCard(
    catalog: OpdsCatalogEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (catalog.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (catalog.iconUrl != null) {
                AsyncImage(
                    model = catalog.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = catalog.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (catalog.authType != "NONE") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!catalog.description.isNullOrBlank()) {
                    Text(
                        text = catalog.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.opds_delete_catalog),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

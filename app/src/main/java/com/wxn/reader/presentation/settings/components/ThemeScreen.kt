package com.wxn.reader.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.R
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.settings.viewmodels.ThemeViewModel
import com.wxn.reader.presentation.settings.viewmodels.ThemeUpdateEvent
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.ui.theme.ColorSchemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: ThemeViewModel = hiltViewModel()
) {
    val navController: NavHostController = LocalNavController.current
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (themePreferences != null) {
        val isDarkTheme = when (themePreferences!!.appTheme) {
            AppTheme.SYSTEM -> isSystemInDarkTheme()
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
        }
        val context = LocalContext.current

        // Single source of truth: the enum entries. All schemes are shown regardless of mode
        // (neutral ids) — the dark/light variant is resolved per-preview via option.resolve.
        val options = remember { ColorSchemeOption.entries }

        LaunchedEffect(Unit) {
            viewModel.updateEvent.collect { event ->
                val message = messageFor(event, context)
                if (message != null) {
                    snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
                }
            }
        }

        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = { Text(stringResource(R.string.theme)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
            ) {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    item {
                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        SegmentedThemeControl(
                            selectedTheme = themePreferences!!.appTheme,
                            onThemeSelected = { theme ->
                                viewModel.updateAppThemePreferences(theme)
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.color_scheme), style = MaterialTheme.typography.titleMedium)
                    }

                    items(options) { option ->
                        ColorSchemePreviewCard(
                            name = stringResource(option.displayNameRes),
                            colorScheme = option.resolve(isDark = isDarkTheme, context = context),
                            isSelected = themePreferences!!.colorScheme == option,
                            onSelect = {
                                viewModel.updateColorSchemePreferences(option)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun messageFor(event: ThemeUpdateEvent, context: android.content.Context): String? = when (event) {
    is ThemeUpdateEvent.ThemeUpdated -> context.getString(R.string.theme_updated)
    is ThemeUpdateEvent.ColorSchemeUpdated -> context.getString(
        R.string.color_scheme_updated,
        context.getString(event.colorScheme.displayNameRes),
    )
    is ThemeUpdateEvent.AppThemeUpdated -> context.getString(
        R.string.app_theme_updated,
        context.getString(event.appTheme.displayNameRes),
    )
    is ThemeUpdateEvent.ColorSchemeUpdateFailed -> context.getString(R.string.theme_update_failed)
}

@Composable
fun ColorSchemePreviewCard(
    name: String,
    colorScheme: ColorScheme?,
    isSelected: Boolean,
    onSelect: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) colorScheme?.primary
                    ?: MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            // K5: M3 1.4.0 cards use surfaceContainer, not surface.
            containerColor = colorScheme?.surfaceContainer ?: MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onSelect,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme?.onSurface ?: MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            ColorPreviewRow(colorScheme)
            Spacer(modifier = Modifier.height(16.dp))
            ColorPreviewPalette(colorScheme)
        }
    }
}

@Composable
fun ColorPreviewRow(colorScheme: ColorScheme?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ColorPreviewBox(colorScheme?.primary ?: MaterialTheme.colorScheme.primary, "Primary")
        ColorPreviewBox(colorScheme?.secondary ?: MaterialTheme.colorScheme.secondary, "Secondary")
        ColorPreviewBox(colorScheme?.tertiary ?: MaterialTheme.colorScheme.tertiary, "Tertiary")
    }
}

@Composable
fun ColorPreviewPalette(colorScheme: ColorScheme?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colorScheme?.primary ?: MaterialTheme.colorScheme.primary)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colorScheme?.secondary ?: MaterialTheme.colorScheme.secondary)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colorScheme?.tertiary ?: MaterialTheme.colorScheme.tertiary)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colorScheme?.background ?: MaterialTheme.colorScheme.background)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colorScheme?.surface ?: MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
fun ColorPreviewBox(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}




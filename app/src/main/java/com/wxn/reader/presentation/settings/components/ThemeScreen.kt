package com.wxn.reader.presentation.settings.components

import android.os.Build
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import com.wxn.reader.ui.theme.DarkBlueScheme
import com.wxn.reader.ui.theme.DarkColorScheme
import com.wxn.reader.ui.theme.DarkGreenScheme
import com.wxn.reader.ui.theme.DarkGreyScheme
import com.wxn.reader.ui.theme.DarkParchmentScheme
import com.wxn.reader.ui.theme.DarkPinkScheme
import com.wxn.reader.ui.theme.DarkPurpleScheme
import com.wxn.reader.ui.theme.DarkRedScheme
import com.wxn.reader.ui.theme.DarkSepiaScheme
import com.wxn.reader.ui.theme.DarkTealScheme
import com.wxn.reader.ui.theme.DarkYellowScheme
import com.wxn.reader.ui.theme.LightBlueScheme
import com.wxn.reader.ui.theme.LightColorScheme
import com.wxn.reader.ui.theme.LightGreenScheme
import com.wxn.reader.ui.theme.LightGreyScheme
import com.wxn.reader.ui.theme.LightParchmentScheme
import com.wxn.reader.ui.theme.LightPinkScheme
import com.wxn.reader.ui.theme.LightPurpleScheme
import com.wxn.reader.ui.theme.LightRedScheme
import com.wxn.reader.ui.theme.LightSepiaScheme
import com.wxn.reader.ui.theme.LightTealScheme
import com.wxn.reader.ui.theme.LightYellowScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: ThemeViewModel = hiltViewModel()
) {
    val navController: NavHostController = LocalNavController.current
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val updateEvent by viewModel.updateEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (themePreferences != null) {
        val isDarkTheme = when (themePreferences!!.appTheme) {
            AppTheme.SYSTEM -> isSystemInDarkTheme()
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
        }

        val context = LocalContext.current
        val dynamicColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null


        val colorSchemes = listOf(
            "Dynamic" to dynamicColorScheme,
            "Light Default" to LightColorScheme,
            "Dark Default" to DarkColorScheme,
            "Light Grey" to LightGreyScheme,
            "Dark Grey" to DarkGreyScheme,
            "Light Sepia" to LightSepiaScheme,
            "Dark Sepia" to DarkSepiaScheme,
            "Light Parchment" to LightParchmentScheme,
            "Dark Parchment" to DarkParchmentScheme,
            "Light Yellow" to LightYellowScheme,
            "Dark Yellow" to DarkYellowScheme,
            "Light Teal" to LightTealScheme,
            "Dark Teal" to DarkTealScheme,
            "Light Blue" to LightBlueScheme,
            "Dark Blue" to DarkBlueScheme,
            "Light Pink" to LightPinkScheme,
            "Dark Pink" to DarkPinkScheme,
            "Light Purple" to LightPurpleScheme,
            "Dark Purple" to DarkPurpleScheme,
            "Light Red" to LightRedScheme,
            "Dark Red" to DarkRedScheme,
            "Light Green" to LightGreenScheme,
            "Dark Green" to DarkGreenScheme,
            ).filter { (name, _) ->
            when {
                name == "Dynamic" -> true
                isDarkTheme -> name.startsWith("Dark")
                else -> name.startsWith("Light")
            }
        }

        val displayNameMapping = mapOf(
            "Light Default" to "Monochrome",
            "Dark Default" to "Monochrome",
            "Light Grey" to "Twilight",
            "Dark Grey" to "Twilight",
            "Light Sepia" to "Sepia",
            "Dark Sepia" to "Sepia",
            "Light Parchment" to "Parchment",
            "Dark Parchment" to "Parchment",
            "Light Yellow" to "Pastel Yellow",
            "Dark Yellow" to "Pastel Yellow",
            "Light Teal" to "Teal",
            "Dark Teal" to "Teal",
            "Light Purple" to "Violet",
            "Dark Purple" to "Violet",
            "Light Pink" to "Pastel Pink",
            "Dark Pink" to "Pastel Pink",
            "Light Red" to "Crimson Red",
            "Dark Red" to "Crimson Red",
            "Light Green" to "Emerald Green",
            "Dark Green" to "Emerald Green",
            "Light Blue" to "Lavender Blue",
            "Dark Blue" to "Lavender Blue",
        )


        LaunchedEffect(isDarkTheme) {
            val currentScheme = themePreferences!!.colorScheme
            val newScheme = when {
                currentScheme == "Dynamic" -> currentScheme
                isDarkTheme && currentScheme.startsWith("Light") -> "Dark ${
                    currentScheme.split(" ").last()
                }"

                !isDarkTheme && currentScheme.startsWith("Dark") -> "Light ${
                    currentScheme.split(" ").last()
                }"

                else -> currentScheme
            }
            if (currentScheme != newScheme) {
                viewModel.updateColorSchemePreferences(newScheme)
            }
        }

        LaunchedEffect(updateEvent) {
            updateEvent?.let { event ->
                val message = when (event) {
                    is ThemeUpdateEvent.ThemeUpdated ->
                        stringResource(R.string.theme_updated)
                    is ThemeUpdateEvent.ColorSchemeUpdated -> {
                        val displayName = displayNameMapping[event.colorScheme] ?: event.colorScheme
                        stringResource(R.string.color_scheme_updated, displayName)
                    }
                    is ThemeUpdateEvent.AppThemeUpdated -> {
                        val themeName = when (event.appTheme) {
                            AppTheme.SYSTEM -> stringResource(R.string.system_default)
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                        }
                        stringResource(R.string.app_theme_updated, themeName)
                    }
                }
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearUpdateEvent()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
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
                ){
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

                    items(colorSchemes) { (name, scheme) ->
                        val displayName = displayNameMapping[name] ?: name
                        ColorSchemePreviewCard(
                            name = displayName,
                            colorScheme = scheme,
                            isSelected = themePreferences!!.colorScheme == name,
                            onSelect = {
                                viewModel.updateColorSchemePreferences(name)
                            }
                        )
                    }
                }
            }
        }
    }
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
            containerColor = colorScheme?.surface ?: MaterialTheme.colorScheme.surface
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



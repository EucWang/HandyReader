package com.wxn.reader.presentation.gettingStarted

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wxn.reader.BuildConfig
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.ui.theme.M3Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GettingStartedScreen(
    viewModel: GettingStartedViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val isDark = isSystemInDarkTheme()

    val splashBgColor = colorResource(
        if (isDark) R.color.splash_background_dark else R.color.splash_background_light
    )

    val iconSize = remember { Animatable(75f) }
    val contentAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBgColor)
            .graphicsLayer { alpha = exitAlpha.value }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_logo_content_desc),
                modifier = Modifier.size(iconSize.value.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.welcome_to_uread),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_slogan),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            text = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = contentAlpha.value }
        )
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                iconSize.animateTo(
                    targetValue = 108f,
                    animationSpec = tween(
                        durationMillis = M3Motion.Duration.LONG,
                        easing = M3Motion.EmphasizedDecelerate
                    )
                )
            }
            launch {
                contentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = M3Motion.Duration.LONG,
                        delayMillis = 200,
                        easing = M3Motion.EmphasizedDecelerate
                    )
                )
            }
        }

        delay(1500)

        exitAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = M3Motion.Duration.MEDIUM,
                easing = M3Motion.EmphasizedAccelerate
            )
        )

        viewModel.skipGettingStarted()
        navController.navigate(Screens.HomeScreen.route) {
            popUpTo(Screens.GettingStartedScreen.route) { inclusive = true }
        }
    }
}

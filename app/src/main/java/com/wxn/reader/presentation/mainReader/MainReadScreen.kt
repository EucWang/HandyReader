package com.wxn.reader.presentation.mainReader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.wxn.base.util.Logger
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.presentation.bookReader.EpubReaderView
import com.wxn.reader.util.KeepScreenOn
import com.wxn.reader.util.PurchaseHelper
import com.wxn.reader.util.SetFullScreen
import kotlinx.coroutines.delay
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun MainReadScreen(
    viewModel: MainReadViewModel = hiltViewModel()
) {
    val purchaseHelper: PurchaseHelper = PurchaseHelperController.current
    val navController = LocalNavController.current
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()

    val book by viewModel.book.collectAsStateWithLifecycle()

    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    var areToolbarsVisible by remember { mutableStateOf(false) }

    var showReader by remember { mutableStateOf(false) }
    var coverAlpha by remember { mutableFloatStateOf(1f) }
    var readerAlpha by remember { mutableFloatStateOf(0f) }

    KeepScreenOn(readerPreferences.keepScreenOn)

    LaunchedEffect(uiState) {
        viewModel.fetchInitialLocator()
        if (uiState is BookReaderUiState.LOAD_SUCCESS) {
            delay(2000)
            showReader = true
            Logger.d("MainReadScreen::showReader=$showReader")
            // Animate the transition
            animate(1f, 0f, animationSpec = tween(durationMillis = 500)) { value, _ ->
                coverAlpha = value
            }
            animate(0f, 1f, animationSpec = tween(durationMillis = 500)) { value, _ ->
                readerAlpha = value
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetReadingSession()
        }
    }

    SetFullScreen(context, showSystemBars = areToolbarsVisible)

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
//        Logger.d("MainReadScreen::uiState=$uiState")
        when (uiState) {
            is BookReaderUiState.Loading, is BookReaderUiState.LOAD_SUCCESS -> {
                // Book cover
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(coverAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    val request = ImageRequest.Builder(LocalContext.current)
                        .data(book?.coverImage)
                        .size(300)
                        .scale(Scale.FIT)
                        .build()
                    AsyncImage(
                        model = request,
                        contentDescription = "Book cover",
                        modifier = Modifier
                            .fillMaxSize(0.7f)
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // reader
                if (showReader && uiState is BookReaderUiState.LOAD_SUCCESS) {
//                    val successState = uiState as BookReaderUiState.Success
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(readerAlpha)
                    ) {
                        ReaderView(
                            book = book,
                            purchaseHelper = purchaseHelper,
                            navController = navController,
                            onLocatorChange = { locator ->
//                                viewModel.updateCurrentLocator(locator)
                            },
//                            initialLocator =  initialLocator,
                            readerPreferences = readerPreferences,
                            appPreferences = appPreferences,
                            areToolbarsVisible = areToolbarsVisible,
                            viewModel = viewModel,
                            onToolbarsVisibilityChanged = {
                                areToolbarsVisible = !areToolbarsVisible
                            }
                        )
                    }
                }
            }

            is BookReaderUiState.Error -> Text((uiState as BookReaderUiState.Error).message)

            is BookReaderUiState.Success -> {} //nothing
        }
    }
}
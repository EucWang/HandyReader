package com.wxn.reader.presentation.mainReader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.util.KeepScreenOn
import com.wxn.reader.util.LogCompositions
import com.wxn.reader.util.OnFirstLaunch
import com.wxn.reader.util.SetFullScreen
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun MainReadScreen(
    viewModel: MainReadViewModel = hiltViewModel()
) {
    LogCompositions("Composition:MainReadScreen")
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()

    var areToolbarsVisible by remember { mutableStateOf(false) }

    var readerAlpha by remember { mutableFloatStateOf(0f) }

    KeepScreenOn(readerPreferences.keepScreenOn)

    LaunchedEffect(uiState) {
//        viewModel.fetchInitialLocator()
        if (uiState is BookReaderUiState.LOAD_SUCCESS) {
            // Animate the transition
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
        modifier = Modifier.fillMaxSize().background(color = Color(readerPreferences.backgroundColor)), //.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is BookReaderUiState.Loading-> {
                // Book cover
                BookCoverPanel(viewModel)
            }

            is BookReaderUiState.LOAD_SUCCESS -> {
                // reader
                Box(modifier = Modifier.fillMaxSize().alpha(readerAlpha)) {
                    ReaderView(
                        readerPreferences = readerPreferences,
                        viewModel = viewModel
                    )
                }
            }

            is BookReaderUiState.Error -> Text((uiState as BookReaderUiState.Error).message)

            is BookReaderUiState.Success -> {} //nothing
        }
    }
}

@Composable fun BookCoverPanel(viewModel: MainReadViewModel) {
    LogCompositions("BookCoverPanel")
    val bookCover by viewModel.bookCover.collectAsStateWithLifecycle()
    var coverAlpha by remember { mutableFloatStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(coverAlpha),
        contentAlignment = Alignment.Center
    ) {
        val request = ImageRequest.Builder(LocalContext.current)
            .data(bookCover)
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

    OnFirstLaunch {
        animate(1f, 0f, animationSpec = tween(durationMillis = 1000)) { value, _ ->
            coverAlpha = value
        }
    }
}
package com.wxn.reader.presentation.mainReader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.util.ImagePanel
import com.wxn.reader.util.KeepScreenOn
import com.wxn.reader.util.SetFullScreen
import com.wxn.reader.util.Space
import com.wxn.reader.util.consumeClick

@Composable
fun MainReadScreen(
    viewModel: MainReadViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    var areToolbarsVisible by remember { mutableStateOf(false) }
    var showState by remember { mutableStateOf(0) }

    KeepScreenOn(readerPreferences.keepScreenOn)
    var loadTimemillis by remember { mutableStateOf(0L) }

    LaunchedEffect(uiState) {
//        viewModel.fetchInitialLocator()
        if (uiState is BookReaderUiState.Loading) {
            loadTimemillis = System.currentTimeMillis()
        } else if (uiState is BookReaderUiState.LOAD_BOOK_SUCCESS) {
            showState = 1
            val curMillis = System.currentTimeMillis()
            Logger.d("MainReadScreen::show cover, load book spend:${curMillis - loadTimemillis}")
            loadTimemillis = curMillis
        } else if (uiState is BookReaderUiState.LOAD_CHAPTER_SUCCESS) {
            showState = 2
            val curMillis = System.currentTimeMillis()
            Logger.d("MainReadScreen::show reader, load book chapter spend:${curMillis - loadTimemillis}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetReadingSession()
        }
    }

    SetFullScreen(context, showSystemBars = areToolbarsVisible)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // reader
        AnimatedVisibility(visible = (showState == 1 || showState == 2)) {
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderView(readerPreferences = readerPreferences, viewModel = viewModel)
            }
        }

        // Book cover
        AnimatedVisibility(visible = (showState == 1), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().consumeClick(),
                contentAlignment = Alignment.Center) {
                if (!book?.coverImage.isNullOrEmpty()) {
                    ImagePanel(modifier = Modifier.fillMaxSize(0.75f).padding(8.dp), data = book?.coverImage)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.75f).padding(8.dp)
                            .background(Color.LightGray, RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painterResource(R.drawable.ic_default_book_cover),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize().align(Alignment.Center).clip(RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)),

                            contentDescription = "", )
                        Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = book?.title.orEmpty(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                            Space(height = 12.dp)
                            Text(
                                text = book?.author.orEmpty(), modifier = Modifier.padding(horizontal = 6.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }

                    }
                }
            }
        }
    }
}

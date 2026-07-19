package com.wxn.reader.presentation.pdfReader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.navigateToHome
import com.wxn.reader.presentation.pdfReader.components.PdfReaderBottomBar
import com.wxn.reader.presentation.pdfReader.components.PdfReaderTopBar
import com.wxn.reader.presentation.sharedComponents.BookCover
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.FullScreenManager
import com.wxn.reader.util.KeepScreenOn
import com.wxn.reader.util.SetFullScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PdfReaderScreen(
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    val navController: NavHostController = LocalNavController.current
    KeepScreenOn(true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var areToolbarsVisible by remember { mutableStateOf(false) }


    val book by viewModel.book.collectAsStateWithLifecycle()
    val pdfPages by viewModel.pdfPages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val backgroundColor by viewModel.backgroundColor.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val pageOffset by viewModel.pageOffset.collectAsStateWithLifecycle()
    val failedPages by viewModel.failedPages.collectAsStateWithLifecycle()

    var currentPage by remember { mutableIntStateOf(initialPage) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var showReader by remember { mutableStateOf(false) }
    var coverAlpha by remember { mutableFloatStateOf(1f) }
    var readerAlpha by remember { mutableFloatStateOf(0f) }

    var pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    LaunchedEffect(Unit) {
        viewModel.loadInitialPages()
        delay(1000) // Delay to show the cover
        showReader = true
        // Animate the transition
        animate(1f, 0f, animationSpec = tween(durationMillis = 500)) { value, _ ->
            coverAlpha = value
        }
        animate(0f, 1f, animationSpec = tween(durationMillis = 500)) { value, _ ->
            readerAlpha = value
        }
    }

    DisposableEffect(Unit) {
        FullScreenManager.registerReadPage()
        onDispose {
            FullScreenManager.unregisterReadPage()
        }
    }

    DisposableEffect(currentPage) {
        onDispose {
            viewModel.saveReadingProgress(currentPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val screenWidth = size.width
                    val screenHeight = size.height
                    val middleThirdWidth = screenWidth / 3f
                    val middleThirdHeight = screenHeight / 3f

                    val middleThirdRect = Rect(
                        left = middleThirdWidth,
                        top = middleThirdHeight,
                        right = (2 * middleThirdWidth),
                        bottom = (2 * middleThirdHeight)
                    )

                    if (middleThirdRect.contains(offset)) {
                        areToolbarsVisible = !areToolbarsVisible
                    }
                }
            }
    ) {
        // Book cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .alpha(coverAlpha),
            contentAlignment = Alignment.Center
        ) {
            BookCover(
                coverImage = book?.coverImage,
                title = book?.title.orEmpty(),
                author = book?.author ?: "",
                isAudiobook = false,
                modifier = Modifier
                    .fillMaxSize(0.7f)
                    .padding(16.dp),
                shape = RoundedCornerShape(0.dp),
                contentScale = ContentScale.Fit,
            )
        }

        // PDF reader content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(readerAlpha)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                errorMessage != null -> {
                    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = errorMessage,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: stringResource(R.string.book_file_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.removeCurrentBook()
                                        navigateToHome(navController)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                enabled = !isDeleting
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.remove_from_library))
                            }
                            FilledTonalButton(
                                onClick = { navigateToHome(navController) }
                            ) {
                                Text(stringResource(R.string.ignore))
                            }
                        }
                    }
                }

                else -> {
                    pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

                    LaunchedEffect(pageOffset) {
                        if (pageOffset != 0) {
                            if (pagerState.currentPage > 0 && pageOffset < 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                            if (pagerState.currentPage < pagerState.pageCount - 1 && pageOffset > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                            viewModel.resetPageOffset()
                        }
                    }

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            currentPage = page
                            viewModel.onCurrentPageChanged(page)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 3f)
                                    if (newScale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                    scale = newScale
                                }
                            }
                    ) { page ->
                        LaunchedEffect(page) {
                            viewModel.loadPage(page)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor),
                            contentAlignment = Alignment.Center
                        ) {
                            val pageBitmap = pdfPages.getOrElse(page) { null }
                            when {
                                pageBitmap != null -> AsyncImage(
                                    model = pageBitmap,
                                    contentDescription = "PDF page ${page + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                            translationY = offsetY
                                        ),
                                    contentScale = ContentScale.Fit
                                )
                                page in failedPages -> Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.pdf_page_load_failed),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    FilledTonalButton(onClick = { viewModel.loadPage(page) }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                                else -> CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.TopCenter),
            visible = areToolbarsVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it })
        ) {
            PdfReaderTopBar(
                book = book,
                onBackClick = {
                    navController.navigateUp()
                }
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter),
            visible = areToolbarsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            PdfReaderBottomBar(
                pageCount = pageCount,
                currentPage = currentPage,
                onPageChange = { newPage ->
                    currentPage = newPage
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(newPage - 1)
                    }
                },
            )
        }
    }

    SetFullScreen(context, showSystemBars = areToolbarsVisible)
}






package com.wxn.reader.presentation.mainReader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.navigateToHome
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.presentation.sharedComponents.BookCover
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.FullScreenManager
import com.wxn.reader.util.KeepScreenOn
import com.wxn.reader.util.SetFullScreen
import com.wxn.reader.util.consumeClick
import kotlinx.coroutines.launch

@Composable
fun MainReadScreen(viewModel: MainReadViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val displayCover by viewModel.displayCover.collectAsStateWithLifecycle()
    val displayTitle by viewModel.displayTitle.collectAsStateWithLifecycle()
    val displayAuthor by viewModel.displayAuthor.collectAsStateWithLifecycle()
    // 系统栏跟随工具栏/设置面板可见性同步：任一为 true 即显示。
    // 替换原先永远为 false 的失效局部变量（Bug：工具栏显示时系统栏仍隐藏）。
    val showMenu by viewModel.showMenu.collectAsStateWithLifecycle()
    val showReaderUISettings by viewModel.showReaderUISettings.collectAsStateWithLifecycle()
    val showReaderSettings by viewModel.showReaderSettings.collectAsStateWithLifecycle()
    val showSystemBars by remember {
        derivedStateOf { showMenu || showReaderUISettings || showReaderSettings }
    }

    KeepScreenOn(readerPreferences.keepScreenOn)

    DisposableEffect(Unit) {
        FullScreenManager.registerReadPage()
        viewModel.viewModelScope.launch {
            viewModel.updateReadingTime()
        }
        onDispose {
            viewModel.viewModelScope.launch {
                viewModel.updateReadingTime(true)
                viewModel.resetReadingSession()
            }
            FullScreenManager.unregisterReadPage()
            // 离开阅读器返回书架等非全屏页面：确保系统栏恢复显示。
            // MainActivity 为 singleInstance，popBackStack 不会触发 onResume，故在 onDispose 兜底。
            (context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isLoading = uiState is BookReaderUiState.Loading
    val isError = uiState is BookReaderUiState.Error

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        ReaderView(viewModel = viewModel)

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeClick(),
                contentAlignment = Alignment.Center
            ) {
                BookCover(
                    coverImage = displayCover,
                    title = displayTitle.orEmpty(),
                    author = displayAuthor.orEmpty(),
                    isAudiobook = false,
                    modifier = Modifier
                        .fillMaxWidth(0.81f)
                        .fillMaxHeight(0.75f)
                        .padding(8.dp),
                    shape = RectangleShape,
                    contentScale = ContentScale.FillWidth,
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 42.dp)
                )
            }
        }

        if (isError) {
            val errorState = uiState as? BookReaderUiState.Error
            val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = errorState?.message,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorState?.message ?: stringResource(R.string.book_file_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
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
    }

    SetFullScreen(context, showSystemBars = showSystemBars)
}

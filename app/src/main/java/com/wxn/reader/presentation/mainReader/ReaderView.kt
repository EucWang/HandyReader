package com.wxn.reader.presentation.mainReader

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.util.LogCompositions


@Composable
fun ReaderView(
    readerPreferences: ReaderPreferences,
    viewModel: MainReadViewModel,
) {
    LogCompositions("Composition:ReaderView")
    val book by viewModel.book.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(readerPreferences.backgroundColor))
    ) {
        AndroidView(
            factory = { context ->
                PageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    viewModel.pageController.pageFactory = TextPageFactory(this, viewModel.pageController)
                    this.dataProvider = viewModel.pageController
                    viewModel.pageController.callBack = this
                    setSelectTextCallback(viewModel.pageController)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.dataProvider?.book = book
                view.upStyle()
                view.upTipStyle()
                view.upBg()
                view.upStatusBar()
                view.dataProvider?.loadContent(true)
            }
        )
    }
}
package com.wxn.reader.presentation.mainReader

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.util.PurchaseHelper
import org.readium.r2.shared.publication.Locator

@Composable
fun ReaderView(
    book: Book?,
    purchaseHelper: PurchaseHelper,
    navController: NavHostController,
//    initialLocator: Locator?,
    onLocatorChange: (Locator) -> Unit,
    readerPreferences: ReaderPreferences,
    viewModel: MainReadViewModel,
    appPreferences: AppPreferences,
    areToolbarsVisible: Boolean,
    onToolbarsVisibilityChanged: () -> Unit,
) {
    Logger.i("ReaderView::load")
    Box(
        modifier = Modifier//.navigationBarsPadding().systemBarsPadding()
            .fillMaxSize()
            .background(color = Color(readerPreferences.backgroundColor))
    ) {
        AndroidView(
            factory = { context ->
                PageView(context).apply{
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    viewModel.pageController.pageFactory = TextPageFactory(this , viewModel.pageController)
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
//                view.upTime()
                view.dataProvider?.loadContent(true)
            }
        )
    }
}
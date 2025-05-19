package com.wxn.reader.presentation.mainReader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.wxn.base.bean.Book
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.presentation.bookReader.BookReaderViewModel
import com.wxn.reader.util.PurchaseHelper
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(readerPreferences.backgroundColor))
    ) {
        AndroidView(
            factory = { context ->
                PageView(context).apply{
                    viewModel.pageController.pageFactory = TextPageFactory(this)
                    this.dataProvider = viewModel.pageController
                    setSelectTextCallback(viewModel.pageController)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 28.dp, top = 48.dp),
            update = { view ->
                view.dataProvider?.book = book
                view.dataProvider?.loadContent(true)
            }
        )
    }
}
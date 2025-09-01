package com.wxn.reader.presentation.bookShelf


import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.paging.compose.LazyPagingItems
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.Layout
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.home.HomeViewModel
import com.wxn.reader.presentation.home.components.GridLayout
import com.wxn.reader.presentation.home.components.ListLayout
import kotlin.random.Random

@Composable
fun BookShelfScreen(
    clearSearch: () -> Unit,
    shelf: Shelf,
    books: LazyPagingItems<Book>,
    homeViewModel: HomeViewModel,
    selectedBooks: List<Book>,
    selectionMode: Boolean,
    toggleSelection: (Book) -> Unit,
    isLoading: Boolean,
    appPreferences: AppPreferences,
) {

    var isBookOpen by remember { mutableStateOf(false) }
    val navController: NavHostController = LocalNavController.current

    fun openBook(openedBook: Book) {
        if (selectionMode) {
            toggleSelection(openedBook)
        } else if (!isBookOpen) {  // Only open a book if no book is currently open
            clearSearch()
//            val shouldShowAd =
//                !appPreferences.isPremium && Random.nextFloat() < 0.25f // 25% chance to show ad
            val navigateToBook = {
                val encodedUri = Uri.encode(openedBook.filePath)
                isBookOpen = true  // Set the state to indicate a book is open
                val route = when (stringToFileType(openedBook.fileType)) {
//                    FileType.EPUB -> Screens.BookReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    FileType.PDF -> Screens.PdfReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    FileType.AUDIOBOOK -> Screens.AudiobookReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    else -> {
                        Screens.MainReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    }
                }
                Logger.d("OpenBook::isBookOpen=$isBookOpen,book.fileType=${openedBook.fileType},encodedUri=${encodedUri},id=${openedBook.id},route=$route")
                if (route.isNotEmpty()) {
                    navController.navigate(route = route)
                }
            }
//            if (shouldShowAd) {
//                navigateToBook()
//                showInterstitialAd(navigateToBook)
//            } else {
                navigateToBook()
//            }
        }
    }

    when {
        books.itemCount == 0 -> {
            EmptyShelfContent(shelf.name)
        }

        appPreferences.homeLayout == Layout.Grid || appPreferences.homeLayout == Layout.CoverOnly -> {
            GridLayout(
                clearSearch =  { clearSearch() },
                books = books,
                selectedBooks = selectedBooks,
                selectionMode = selectionMode,
                toggleSelection = toggleSelection,
                viewModel = homeViewModel,
                isLoading = isLoading,
                appPreferences = appPreferences,
                ::openBook
                )
        }

        else -> {
            ListLayout(
                clearSearch = { clearSearch() },
                books = books,
                selectedBooks = selectedBooks,
                selectionMode = selectionMode,
                toggleSelection = toggleSelection,
                viewModel = homeViewModel,
                isLoading = isLoading,
                appPreferences = appPreferences,
                ::openBook
            )
        }
    }
}




@Composable
fun EmptyShelfContent(shelf: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ImportContacts,
                contentDescription = "No books in this shelf",
                modifier = Modifier.size(48.dp)
            )
            Text(stringResource(R.string.no_books_in, shelf))

        }
    }
}


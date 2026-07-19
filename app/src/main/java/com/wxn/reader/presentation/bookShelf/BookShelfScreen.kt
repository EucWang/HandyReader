package com.wxn.reader.presentation.bookShelf


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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.Layout
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.buildReaderRoute
import com.wxn.reader.presentation.home.HomeViewModel
import com.wxn.reader.presentation.home.components.GridLayout
import com.wxn.reader.presentation.home.components.ListLayout

@Composable
fun BookShelfScreen(
    clearSearch: () -> Unit,
    shelf: Shelf,
    homeViewModel: HomeViewModel,
    selectionMode: Boolean,
    toggleSelection: (Book) -> Unit,
    isLoading: Boolean,
    appPreferences: AppPreferences,
) {

    var isBookOpen by remember { mutableStateOf(false) }
    val navController: NavHostController = LocalNavController.current
    val books by homeViewModel.books.collectAsStateWithLifecycle()

    fun openBook(openedBook: Book) {
        if (selectionMode) {
            toggleSelection(openedBook)
        } else if (!isBookOpen) {
            if (!homeViewModel.openBookWithAccessCheck(openedBook)) {
                return
            }
            clearSearch()
            val navigateToBook = {
                isBookOpen = true
                val route = buildReaderRoute(openedBook.id,
                    openedBook.fileType,
                    openedBook.filePath,
                    openedBook.coverImage,
                    title = openedBook.title,
                    author = openedBook.author,)
                Logger.d("OpenBook::isBookOpen=$isBookOpen,book.fileType=${openedBook.fileType},id=${openedBook.id},route=$route")
                if (route.isNotEmpty()) {
                    navController.navigate(route = route)
                }
            }
            navigateToBook()
        }
    }

    when {
        books.size == 0 -> {
            EmptyShelfContent(shelf.name)
        }

        appPreferences.homeLayout == Layout.Grid || appPreferences.homeLayout == Layout.CoverOnly -> {
            GridLayout(
                clearSearch =  { clearSearch() },
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


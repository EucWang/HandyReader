package com.wxn.reader.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.data.model.AppPreferences
import com.wxn.base.bean.Book
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.home.HomeViewModel

@Composable
fun ListLayout(
    clearSearch: () -> Unit,
    selectionMode: Boolean,
    toggleSelection: (Book) -> Unit,
    viewModel: HomeViewModel,
    isLoading: Boolean,
    appPreferences: AppPreferences,
    openBook: (Book) -> Unit
) {
    val navController: NavHostController = LocalNavController.current
    val context = LocalContext.current
    val isAddingBook by viewModel.isAddingBooks.collectAsState()
    val books by viewModel.books.collectAsStateWithLifecycle()

    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()

    LazyColumn(
        userScrollEnabled = !isAddingBook,
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = books.size,
            key = { index -> books.getOrNull(index)?.id ?: index }
        ) { index ->
            val book = books[index] ?: return@items
            val isSelected = selectedBooks.contains(book)

            Box(
                modifier = Modifier.animateItem()
            ) {
                BookListCard(
                    book = book,
                    openBook = openBook,
                    updateLastOpened = {
                        viewModel.updateBook(book.copy(lastOpened = System.currentTimeMillis()))
                    },
                    selected = isSelected,
                    selectionMode = selectionMode,
                    toggleSelection = {
                        toggleSelection(it)
                    },
                    isLoading = isLoading,
                    appPreferences = appPreferences,
                    viewModel = viewModel
                )
            }
        }
    }
}



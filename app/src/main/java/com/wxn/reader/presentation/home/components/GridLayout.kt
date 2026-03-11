package com.wxn.reader.presentation.home.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.reader.data.model.AppPreferences
import com.wxn.base.bean.Book
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.home.HomeViewModel

@Composable
fun GridLayout(
    clearSearch: () -> Unit,
    selectionMode: Boolean,
    toggleSelection: (Book) -> Unit,
    viewModel: HomeViewModel,
    isLoading: Boolean,
    appPreferences: AppPreferences,
    openBook: (Book) -> Unit
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    var isBookOpen by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Calculate spacing based on screen size
    val horizontalSpacing = (screenWidth * 0.02f).coerceAtLeast(4.dp).coerceAtMost(16.dp)
    val verticalSpacing = (screenHeight * 0.02f).coerceAtLeast(6.dp).coerceAtMost(24.dp)

    val isAddingBook by viewModel.isAddingBooks.collectAsState()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        userScrollEnabled = !isAddingBook,
        columns = GridCells.Fixed(appPreferences.gridCount),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = books.size,
            key =  { index -> books.getOrNull(index)?.id ?: index }
        ) { index ->
            val book = books[index] ?: return@items
            val isSelected = selectedBooks.contains(book)
            Box(
                modifier = Modifier.animateItem()
            ) {
                BookCard(
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
                    viewModel = viewModel,
                )
            }
        }
    }
}

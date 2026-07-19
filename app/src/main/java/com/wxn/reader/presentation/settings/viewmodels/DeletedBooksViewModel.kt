package com.wxn.reader.presentation.settings.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.reader.domain.use_case.books.DeleteBookUseCase
import com.wxn.reader.domain.use_case.books.GetDeletedBooksUseCase
import com.wxn.reader.domain.use_case.books.UpdateDeletedFlagUseCase
import com.wxn.reader.presentation.settings.states.DeletedBooksState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DeletedBooksViewModel @Inject constructor(
    private val getDeletedBooksUseCase: GetDeletedBooksUseCase,
    private val updateDeletedFlagUseCase: UpdateDeletedFlagUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    application: Application,
) : AndroidViewModel(application) {

    private val _deletedBooksState = MutableStateFlow<DeletedBooksState>(DeletedBooksState.Loading)
    val deletedBooksState: StateFlow<DeletedBooksState> = _deletedBooksState.asStateFlow()

    private val appContext: Context = application.applicationContext


    init {
        getDeletedBooks()
    }


    private fun getDeletedBooks() {
        viewModelScope.launch {
            try {
                getDeletedBooksUseCase().collect { books ->
                    _deletedBooksState.value = DeletedBooksState.Success(books)
                }
            } catch (e: Exception) {
                _deletedBooksState.value =
                    DeletedBooksState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }


    fun restoreBooks(selectedBooks: Set<Book>) {
        viewModelScope.launch {
            selectedBooks.forEach { book ->
                updateDeletedFlagUseCase(book.id, false)
            }
        }
    }


    fun permanentlyDeleteBooks(selectedBooks: Set<Book>) {
        viewModelScope.launch {
            selectedBooks.forEach { book ->
                val effectiveSource = book.source.ifEmpty { "scan" }
                when (effectiveSource) {
                    "scan" -> {
                        val uri = Uri.parse(book.filePath)
                        if (uri.scheme == "content") {
                            try {
                                DocumentsContract.deleteDocument(
                                    appContext.contentResolver,
                                    uri
                                )
                            } catch (e: Exception) {
                                com.wxn.base.util.Logger.e("DeletedBooksViewModel: delete failed: ${e.message}")
                            }
                        }
                        deleteBookUseCase(book)
                    }
                    "opds" -> {
                        val uri = Uri.parse(book.filePath)
                        uri.path?.let { java.io.File(it) }
                            ?.takeIf { it.exists() }?.delete()
                        deleteBookUseCase(book)
                    }
                    else -> {
                        deleteBookUseCase(book)
                    }
                }
            }
        }
    }
}
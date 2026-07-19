package com.wxn.reader.presentation.settings.states

import com.wxn.base.bean.Book

sealed class DeletedBooksState {
    data object Loading : DeletedBooksState()
    data class Error(val message: String) : DeletedBooksState()
    data class Success(val books: List<Book>) : DeletedBooksState()
}
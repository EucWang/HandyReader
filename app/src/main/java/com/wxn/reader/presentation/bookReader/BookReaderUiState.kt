package com.wxn.reader.presentation.bookReader

import com.wxn.base.bean.Book
//import org.readium.r2.shared.publication.Publication

sealed class BookReaderUiState {
    data object Loading : BookReaderUiState()
    data class Error(val message: String) : BookReaderUiState()
//    data class Success(val publication: Publication) : BookReaderUiState()
    data class LOAD_BOOK_SUCCESS(val book: Book) : BookReaderUiState()
    data class LOAD_CHAPTER_SUCCESS(val chapterIndex: Int): BookReaderUiState()
}
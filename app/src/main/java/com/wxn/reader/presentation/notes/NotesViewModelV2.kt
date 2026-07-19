package com.wxn.reader.presentation.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.use_case.books.GetAllBooksUseCase
import com.wxn.reader.domain.use_case.notes.DeleteNoteUseCase
import com.wxn.reader.domain.use_case.notes.GetAllNotesUseCase
import com.wxn.reader.domain.use_case.notes.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModelV2 @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    getAllBooksUseCase: GetAllBooksUseCase,
    private val getAllNotesUseCase: GetAllNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    application: Application,
) : AndroidViewModel(application) {

    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Long?>(null)
    val selectedBookId: StateFlow<Long?> = _selectedBookId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val booksAndNotes = getAllBooksUseCase()
        .flatMapLatest { books ->
            getAllNotesUseCase().map { notes -> Pair(books, notes) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val booksWithNotes: StateFlow<List<BookWithNotes>> = booksAndNotes
        .map { (books, notes) ->
            val notesByBook = notes.groupBy { it.bookId }
            books.map { book ->
                BookWithNotes(book, notesByBook[book.id] ?: emptyList())
            }.filter { it.notes.isNotEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookMap: StateFlow<Map<Long, Book>> = booksAndNotes
        .map { (books, _) -> books.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredNotes: StateFlow<List<Note>> = combine(
        booksAndNotes.map { (_, notes) -> notes },
        _selectedBookId
    ) { notes, bookId ->
        if (bookId == null) notes else notes.filter { it.bookId == bookId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalNoteCount: StateFlow<Int> = booksAndNotes
        .map { (_, notes) -> notes.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadAppPreferences()
        observeSelectedBookValidity()
    }

    fun selectBook(bookId: Long?) {
        _selectedBookId.value = bookId
    }

    private fun loadAppPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow.collect { preferences ->
                _appPreferences.value = preferences
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedBookValidity() {
        viewModelScope.launch {
            booksWithNotes.collect { currentBooksWithNotes ->
                val currentId = _selectedBookId.value
                if (currentId != null) {
                    val stillExists = currentBooksWithNotes.any { it.book.id == currentId }
                    if (!stillExists) {
                        _selectedBookId.value = null
                    }
                }
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            updateNoteUseCase(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            deleteNoteUseCase(note)
        }
    }
}

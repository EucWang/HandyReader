package com.wxn.reader.presentation.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.use_case.books.GetAllBooksUseCase
import com.wxn.reader.domain.use_case.notes.DeleteNoteUseCase
import com.wxn.reader.domain.use_case.notes.GetAllNotesUseCase
import com.wxn.reader.domain.use_case.notes.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    getAllBooksUseCase: GetAllBooksUseCase,
    private val getAllNotesUseCase: GetAllNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    application: Application,
) : AndroidViewModel(application) {

    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val booksWithNotes: Flow<List<BookWithNotes>> = getAllBooksUseCase()
        .flatMapLatest { books ->
            getAllNotesUseCase().map { allNotes ->
                val notesByBook = allNotes.groupBy { it.bookId }
                books.map { book ->
                    BookWithNotes(book, notesByBook[book.id] ?: emptyList())
                }.filter { it.notes.isNotEmpty() }
            }
        }

    init {
        loadAppPreferences()
    }

    private fun loadAppPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow.collect { preferences ->
                _appPreferences.value = preferences
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

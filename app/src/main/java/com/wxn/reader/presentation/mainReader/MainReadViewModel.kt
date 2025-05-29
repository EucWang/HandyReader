package com.wxn.reader.presentation.mainReader

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.parser.mobi.MobiFileParser
import com.wxn.bookparser.parser.mobi.MobiTextParser
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.toRediumEpubPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.use_case.annotations.AddAnnotationUseCase
import com.wxn.reader.domain.use_case.annotations.DeleteAnnotationUseCase
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.annotations.UpdateAnnotationUseCase
import com.wxn.reader.domain.use_case.bookmarks.AddBookmarkUseCase
import com.wxn.reader.domain.use_case.bookmarks.DeleteBookmarkUseCase
import com.wxn.reader.domain.use_case.bookmarks.GetBookmarksForBookUseCase
import com.wxn.reader.domain.use_case.bookmarks.UpdateBookmarkUseCase
import com.wxn.reader.domain.use_case.books.GetBookByIdUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChaptersByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.InsertChaptersUserCase
import com.wxn.reader.domain.use_case.notes.AddNoteUseCase
import com.wxn.reader.domain.use_case.notes.DeleteNoteUseCase
import com.wxn.reader.domain.use_case.notes.GetNotesForBookUseCase
import com.wxn.reader.domain.use_case.notes.UpdateNoteUseCase
import com.wxn.reader.domain.use_case.reading_activity.AddReadingActivityUseCase
import com.wxn.reader.domain.use_case.reading_activity.GetReadingActivityByDateUseCase
import com.wxn.reader.domain.use_case.reading_progress.GetReadingProgressUseCase
import com.wxn.reader.domain.use_case.reading_progress.SetReadingProgressUseCase
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.presentation.bookReader.BookReaderUiState.LOAD_CHAPTER_SUCCESS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalReadiumApi::class)
@HiltViewModel
@Stable
class MainReadViewModel @Inject constructor(
    context: Application,
    private val appPreferencesUtil: AppPreferencesUtil,

    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val updateBookUseCase: UpdateBookUseCase,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val setReadingProgressUseCase: SetReadingProgressUseCase,
    private val getAnnotationsUseCase: GetAnnotationsUseCase,
    private val addAnnotationUseCase: AddAnnotationUseCase,
    private val updateAnnotationUseCase: UpdateAnnotationUseCase,
    private val deleteAnnotationUseCase: DeleteAnnotationUseCase,
    private val getNotesForBookUseCase: GetNotesForBookUseCase,
    private val addNotesUseCase: AddNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,

    private val getBookmarksForBookUseCase: GetBookmarksForBookUseCase,
    private val addBookmarksUseCase: AddBookmarkUseCase,
    private val updateBookmarkUseCase: UpdateBookmarkUseCase,
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,

    private val getChapterByIdUserCase: GetChapterByIdUserCase,
    private val getChaptersByBookIdUserCase: GetChaptersByBookIdUserCase,
    private val insertChaptersUserCase: InsertChaptersUserCase,
    private val getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,

    private val addOrUpdateReadingActivityUseCase: AddReadingActivityUseCase,
    private val getReadingActivityByDateUseCase: GetReadingActivityByDateUseCase,
    private val readerPreferencesUtil: ReaderPreferencesUtil,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,

    private val textParser: TextParser,
    val pageController: PageViewController,

    savedStateHandle: SavedStateHandle,

) : AndroidViewModel(context) {
    private val _appPreferences = MutableStateFlow(AppPreferencesUtil.defaultPreferences)
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()

    private val _readerPreferences = MutableStateFlow(ReaderPreferencesUtil.defaultPreferences)
    val readerPreferences: StateFlow<ReaderPreferences> = _readerPreferences.asStateFlow()

    private val _epubPreferences =
        MutableStateFlow(ReaderPreferencesUtil.defaultPreferences.toRediumEpubPreferences())
    val epubPreferences: StateFlow<EpubPreferences> = _epubPreferences.asStateFlow()

    private val _uiState = MutableStateFlow<BookReaderUiState>(BookReaderUiState.Loading)
    val uiState: StateFlow<BookReaderUiState> = _uiState.asStateFlow()

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _bookCover = MutableStateFlow<String?>(null)
    val bookCover : StateFlow<String?> = _bookCover.asStateFlow()

    private val _currentBookId = MutableStateFlow<Long?>(null)
    val currentBookId: StateFlow<Long?> = _currentBookId.asStateFlow()

    private val _initialLocator = MutableStateFlow<Locator?>(null)
    val initialLocator: StateFlow<Locator?> = _initialLocator.asStateFlow()

    private var currentDayStartTime = 0L

    private var isReadingSessionActive = false
    private var lastLocatorChangeTime = 0L

    private suspend fun fetchBook(bookId: Long): Boolean {
        try {
            val theBook = getBookByIdUseCase(bookId)
            if (theBook != null) {
                _book.value = theBook
                _uiState.value = BookReaderUiState.LOAD_BOOK_SUCCESS(theBook)
                Logger.d("MainReadViewModel:fetchBook::_uiState.value=${_uiState.value}")
            }
            return true
        } catch (e: Exception) {
            _uiState.value = BookReaderUiState.Error(e.message ?: "An error occurred")
            Logger.d("MainReadViewModel:fetchBook::_uiState.value=${_uiState.value}")
        }
        return false
    }

    private fun resetCurrentDayStartTime() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        currentDayStartTime = calendar.timeInMillis
    }

    init {
        ChapterProvider.init(context)

        val openedBookId = savedStateHandle.get<String>("bookId")?.toLongOrNull()
        val bookUri = savedStateHandle.get<String>("bookUri")
        resetCurrentDayStartTime()
        pageController.scope = viewModelScope

        viewModelScope.launch {
            appPreferencesUtil.appPreferencesFlow.stateIn(viewModelScope).collect { initialPreferences ->
                _appPreferences.value = initialPreferences
            }

            readerPreferencesUtil.readerPreferencesFlow.stateIn(viewModelScope).collect { preferences ->
                _readerPreferences.value = preferences
                _epubPreferences.value = preferences.toRediumEpubPreferences()
            }
        }

        viewModelScope.launchIO {
            val bookId = openedBookId ?: return@launchIO
            _currentBookId.value = bookId

            Logger.i("MainReadViewModel::init::bookId=$bookId")
            var allChapters = getChaptersByBookIdUserCase.invoke(bookId).firstOrNull()
            if (allChapters.isNullOrEmpty()) {
                Logger.d("MainReaderViewModel:load all chapters from db failed:${System.currentTimeMillis()}")
                allChapters = BookHelper.getChapters(context, bookId, bookUri, textParser)
                if (allChapters.isNotEmpty()) {
                    Logger.d("MainReaderViewModel:load all chapters from book file:${System.currentTimeMillis()}")
                    insertChaptersUserCase(allChapters)
                }
            }
            if (fetchBook(bookId)){
                val newBook = _book.value ?: return@launchIO
                Logger.d("MainReaderViewModel:load reset book to pageController:${System.currentTimeMillis()}")
                pageController.resetBook(newBook){//重新加载章节数
                    _uiState.value = LOAD_CHAPTER_SUCCESS(0)
                    Logger.d("MainReaderViewModel:load current chapter success:${System.currentTimeMillis()}")
                }
            }
        }
    }

    private suspend fun getInitialLocator(bookId: Long): Locator? {
        //TODO
        return null
//        return getReadingProgressUseCase(bookId).let { progressJson ->
//            if (progressJson.isNotEmpty()) {
//                Locator.fromJSON(JSONObject(progressJson))
//            } else {
//                null
//            }
//        }
    }

    fun fetchInitialLocator() {
        viewModelScope.launch {
            currentBookId.value?.let { bookId ->
                _initialLocator.value = getInitialLocator(bookId)
            }
        }
    }

    fun resetReadingSession() {
        isReadingSessionActive = false
        lastLocatorChangeTime = 0L
    }

    override fun onCleared() {
        pageController.clear()
        currentDayStartTime = 0
        _initialLocator.value = null
        _currentBookId.value = null
        _uiState.value = BookReaderUiState.Loading
        _book.value = null
        isReadingSessionActive = false
        lastLocatorChangeTime = 0L
        super.onCleared()
        Logger.i("MainReadViewModel::onCleared")
    }
}
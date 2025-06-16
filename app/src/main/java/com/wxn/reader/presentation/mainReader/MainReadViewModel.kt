package com.wxn.reader.presentation.mainReader

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
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
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Bookmark
import com.wxn.reader.domain.model.Note
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
import kotlinx.coroutines.flow.update
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

) : AndroidViewModel(context), PageViewController.OnClickListener {
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

    //显示总的菜单弹窗
    private val _showMenu = MutableStateFlow<Boolean>(false)
    val showMenu: StateFlow<Boolean> = _showMenu.asStateFlow()

    //显示章节列表
    private val _isChaptersDrawerOpen = MutableStateFlow<Boolean>(false)
    val isChaptersDrawerOpen: StateFlow<Boolean> = _isChaptersDrawerOpen.asStateFlow()
    //显示笔记列表
    private val _isNotesDrawerOpen  = MutableStateFlow<Boolean>(false)
    val isNotesDrawerOpen: StateFlow<Boolean> = _isNotesDrawerOpen.asStateFlow()
    //显示标签列表
    private val _isBookmarksDrawerOpen = MutableStateFlow<Boolean>(false)
    val isBookmarksDrawerOpen: StateFlow<Boolean> = _isBookmarksDrawerOpen.asStateFlow()
    //显示高亮列表
    private val _isHighlightsDrawerOpen  = MutableStateFlow<Boolean>(false)
    val isHighlightsDrawerOpen: StateFlow<Boolean> = _isHighlightsDrawerOpen.asStateFlow()

    //tts
    private val _isTtsOn  = MutableStateFlow<Boolean>(false)
    val isTtsOn: StateFlow<Boolean> = _isTtsOn.asStateFlow()

    private val _isShowTextToolbar = MutableStateFlow<Boolean>(false)
    val showTextToolbar: StateFlow<Boolean> = _isShowTextToolbar.asStateFlow()

    private val _isShowColorSelectionPanel = MutableStateFlow<Boolean>(false)
    val showColorSelectionPanel: StateFlow<Boolean> = _isShowColorSelectionPanel.asStateFlow()

    private val _showUISettings = MutableStateFlow<Boolean>(false)
    val showUISettings : StateFlow<Boolean> = _showUISettings.asStateFlow()

    private val _showFontSettings = MutableStateFlow<Boolean>(false)
    val showFontSettings : StateFlow<Boolean> = _showFontSettings.asStateFlow()

    private val _showPageSettings = MutableStateFlow<Boolean>(false)
    val showPageSettings : StateFlow<Boolean> = _showPageSettings.asStateFlow()

    private val _showReaderSettings = MutableStateFlow<Boolean>(false)
    val showReaderSettings: StateFlow<Boolean> = _showReaderSettings.asStateFlow()

    //
    private val _showNoteDialog = MutableStateFlow<Boolean>(false)
    val showNoteDialog: StateFlow<Boolean> = _showNoteDialog.asStateFlow()

    //选中的笔记
    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote.asStateFlow()
    //笔记列表
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    //书签列表
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()
    //注释列表
    private val _annotations = MutableStateFlow<List<BookAnnotation>>(emptyList())
    val annotations: StateFlow<List<BookAnnotation>> = _annotations.asStateFlow()

    //选中的注释
    private val _selectedAnnotation = MutableStateFlow<BookAnnotation?>(null)
    val selectedAnnotation: StateFlow<BookAnnotation?> = _selectedAnnotation.asStateFlow()

    private var currentDayStartTime = 0L

    private var isReadingSessionActive = false
    private var lastLocatorChangeTime = 0L

    private var allChapters = arrayListOf<BookChapter>()
    val showOutChapters = arrayListOf<BookChapter>()

    private suspend fun fetchBook(bookId: Long): Boolean {
        try {
            val theBook = getBookByIdUseCase(bookId)
            if (theBook != null) {
                _book.value = theBook
                _uiState.value = BookReaderUiState.LOAD_BOOK_SUCCESS(theBook)
                Logger.d("MainReadViewModel:fetchBook::_uiState.value=${_uiState.value}")
            }
            loadAnnotations(bookId)
            loadNotes(bookId)
            loadBookmarks(bookId)

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
            allChapters.clear()
            val chapters = getChaptersByBookIdUserCase.invoke(bookId).firstOrNull().orEmpty()
            allChapters.addAll(chapters)
            if (allChapters.isEmpty()) {
                Logger.d("MainReaderViewModel:load all chapters from db failed:${System.currentTimeMillis()}")
                allChapters.clear()
                allChapters.addAll(BookHelper.getChapters(context, bookId, bookUri, textParser))
                if (allChapters.isNotEmpty()) {
                    Logger.d("MainReaderViewModel:load all chapters from book file:${System.currentTimeMillis()}")
                    insertChaptersUserCase(allChapters)
                }
            }
            showOutChapters.clear()
            showOutChapters.addAll(allChapters.filter{
                !it.chapterName.isEmpty()
            })

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

    override fun onCenterClick() {
        _showMenu.value = !_showMenu.value
    }

    /***
     * 点击link链接跳转到对应章节
     */
    override fun onLinkClick(href: String?) {
        Logger.d("MainReaderViewModel::onLinkClick:href=$href")
        if (!href.isNullOrEmpty()) {
            if (href.startsWith("http")) {
                //跳转到h5界面显示
            } else {
                allChapters.find { chapter->
                    chapter.srcName == href
                }?.let { targetChapter ->
                    pageController.changeChapter(targetChapter.chapterIndex)
                }
            }
        }
    }

    fun chaptersDrawerOpen(open:Boolean = true) {
        _isChaptersDrawerOpen.value = open
    }

    fun notesDrawerOpen(open:Boolean = true){
        _isNotesDrawerOpen.value = open
    }

    fun bookmarksDrawerOpen(open:Boolean = true) {
        _isBookmarksDrawerOpen.value = open
    }

    fun highlightsDrawerOpen(open:Boolean = true) {
        _isHighlightsDrawerOpen.value = open
    }

    fun textToolbarOpen(open:Boolean = true) {
        _isShowTextToolbar.value = open
    }

    fun showColorSelectionPanel(open:Boolean = true) {
        _isShowColorSelectionPanel.value = open
    }

    fun uiSettingsOpen(open: Boolean = true) {
        _showUISettings.value = open
    }

    fun fontSettingsOpen(open:Boolean = true) {
        _showFontSettings.value = open
    }

    fun pageSettingsOpen(open:Boolean = true) {
        _showPageSettings.value = open
    }

    fun readerSettingsOpen(open:Boolean = true) {
        _showReaderSettings.value = open
    }

    fun onToolbarsVisibilityChanged(){
        _showMenu.value = !_showMenu.value
    }

    fun noteDialogOpen(open:Boolean = true) {
        _showNoteDialog.value = open
    }

    fun clearSelectedNote() {
        _selectedNote.value = null
    }

    /***
     * 加载笔记列表
     */
    private fun loadNotes(bookId: Long) {
        viewModelScope.launch {
            getNotesForBookUseCase(bookId).collect { updatedNotes ->
                _notes.value = updatedNotes
            }
        }
    }

    /**
     * 加载书签列表
     */
    private fun loadBookmarks(bookId: Long) {
        viewModelScope.launch {
            getBookmarksForBookUseCase(bookId).collect { bookmarks ->
                _bookmarks.value = bookmarks
            }
        }
    }

    /**
     * 更新笔记
     */
    fun updateNote(note: Note) {
        viewModelScope.launch {
            updateNoteUseCase(note)
            // Update the notes list immediately
            _notes.update { currentNotes ->
                currentNotes.map { if (it.id == note.id) note else it }
            }
            // Update the selected note if it's the one being edited
            _selectedNote.update { selectedNote ->
                if (selectedNote?.id == note.id) note else selectedNote
            }
        }
    }

    /***
     * 删除笔记
     */
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            deleteNoteUseCase(note)
            currentBookId.value?.let { loadNotes(it) }
        }
    }
    /***
     * 加载注释列表
     */
    private fun loadAnnotations(bookId: Long) {
        viewModelScope.launch {
            getAnnotationsUseCase(bookId).collect { annotationsList ->
                _annotations.value = annotationsList
            }
        }
    }

    /***
     * 移除书签
     */
    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            deleteBookmarkUseCase(bookmark)
            currentBookId.value?.let { loadBookmarks(it) }
        }
    }

    /***
     * 删除注释
     */
    fun deleteAnnotation(annotation: BookAnnotation) {
        viewModelScope.launch {
            deleteAnnotationUseCase(annotation)
            _annotations.update { currentAnnotations ->
                currentAnnotations.filter { it.id != annotation.id }
            }
            _selectedAnnotation.value = null
            currentBookId.value?.let { loadAnnotations(it) }
        }
    }

    /***
     * 更新注释
     */
    fun updateAnnotation(annotation: BookAnnotation) {
        viewModelScope.launch {
            updateAnnotationUseCase(annotation)
            _annotations.value += annotation
            _selectedAnnotation.value = annotation
            currentBookId.value?.let { loadAnnotations(it) }

        }
    }

    fun resetFontPreferences() {
        viewModelScope.launch {
            readerPreferencesUtil.resetFontPreferences()
        }
    }

    fun resetPagePreferences() {
        viewModelScope.launch {
            readerPreferencesUtil.resetPagePreferences()
        }
    }

    fun resetUiPreferences() {
        viewModelScope.launch {
            readerPreferencesUtil.resetUiPreferences()
        }
    }

    fun resetReaderPreferences() {
        viewModelScope.launch {
            readerPreferencesUtil.resetReaderPreferences()
        }
    }

    fun updateReaderPreferences(newPreferences: ReaderPreferences) {
        viewModelScope.launch {
            readerPreferencesUtil.updatePreferences(newPreferences)
        }
    }
}
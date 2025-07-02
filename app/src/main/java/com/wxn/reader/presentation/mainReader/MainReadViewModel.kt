package com.wxn.reader.presentation.mainReader

import android.app.Application
import android.graphics.Rect
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.Locator
import com.wxn.base.ext.toStringColor
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.launchIO
import com.wxn.bookparser.TextParser
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.R
import com.wxn.reader.data.dto.AnnotationType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Bookmark
import com.wxn.reader.domain.model.LinkedContent
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
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
import com.wxn.reader.ui.theme.stringResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

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

    private val textParser: TextParser,
    val pageController: PageViewController,

    savedStateHandle: SavedStateHandle,

    ) : AndroidViewModel(context), PageViewController.OnClickListener {
    private val _appPreferences = MutableStateFlow(AppPreferencesUtil.defaultPreferences)
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()

    private val _readerPreferences = MutableStateFlow(ReaderPreferencesUtil.defaultPreferences)
    val readerPreferences: StateFlow<ReaderPreferences> = _readerPreferences.asStateFlow()

    private val _uiState = MutableStateFlow<BookReaderUiState>(BookReaderUiState.Loading)
    val uiState: StateFlow<BookReaderUiState> = _uiState.asStateFlow()

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _bookCover = MutableStateFlow<String?>(null)
    val bookCover: StateFlow<String?> = _bookCover.asStateFlow()

    private val _currentBookId = MutableStateFlow<Long?>(null)
    val currentBookId: StateFlow<Long?> = _currentBookId.asStateFlow()

    //显示总的菜单弹窗
    private val _showMenu = MutableStateFlow<Boolean>(false)
    val showMenu: StateFlow<Boolean> = _showMenu.asStateFlow()

    //显示章节列表
    private val _isChaptersDrawerOpen = MutableStateFlow<Boolean>(false)
    val isChaptersDrawerOpen: StateFlow<Boolean> = _isChaptersDrawerOpen.asStateFlow()

    //显示笔记列表
    private val _isNotesDrawerOpen = MutableStateFlow<Boolean>(false)
    val isNotesDrawerOpen: StateFlow<Boolean> = _isNotesDrawerOpen.asStateFlow()

    //显示标签列表
    private val _isBookmarksDrawerOpen = MutableStateFlow<Boolean>(false)
    val isBookmarksDrawerOpen: StateFlow<Boolean> = _isBookmarksDrawerOpen.asStateFlow()

    //显示高亮列表
    private val _isHighlightsDrawerOpen = MutableStateFlow<Boolean>(false)
    val isHighlightsDrawerOpen: StateFlow<Boolean> = _isHighlightsDrawerOpen.asStateFlow()

    //tts
    private val _isTtsOn = MutableStateFlow<Boolean>(false)
    val isTtsOn: StateFlow<Boolean> = _isTtsOn.asStateFlow()

    private val _isShowTextToolbar = MutableStateFlow<Boolean>(false)
    val showTextToolbar: StateFlow<Boolean> = _isShowTextToolbar.asStateFlow()

    private val _textToolbarRect = MutableStateFlow<Rect>(Rect(0,0,0,0))
    val textToolbarRect : StateFlow<Rect> = _textToolbarRect.asStateFlow()

    private val _isShowColorSelectionPanel = MutableStateFlow<Boolean>(false)
    val showColorSelectionPanel: StateFlow<Boolean> = _isShowColorSelectionPanel.asStateFlow()

    private val _showUISettings = MutableStateFlow<Boolean>(false)
    val showUISettings: StateFlow<Boolean> = _showUISettings.asStateFlow()

    private val _showFontSettings = MutableStateFlow<Boolean>(false)
    val showFontSettings: StateFlow<Boolean> = _showFontSettings.asStateFlow()

    private val _showPageSettings = MutableStateFlow<Boolean>(false)
    val showPageSettings: StateFlow<Boolean> = _showPageSettings.asStateFlow()

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

    private val _clickedLinkContent = MutableStateFlow<LinkedContent?>(null)
    val clickedLinkContent: StateFlow<LinkedContent?> = _clickedLinkContent.asStateFlow()

    private var currentDayStartTime = 0L

    private var isReadingSessionActive = false
    private var lastLocatorChangeTime = 0L

    private var allChapters = arrayListOf<BookChapter>()
    val showOutChapters = arrayListOf<BookChapter>()

    private val _readProgression = MutableStateFlow<Double>(0.0)
    val readProgression : StateFlow<Double> = _readProgression.asStateFlow()

    private val _curChapterIndex = MutableStateFlow<Int>(0)
    val curChapterIndex : StateFlow<Int> = _curChapterIndex.asStateFlow()

    private val _curChapterName = MutableStateFlow<String>("")
    val curChapterName : StateFlow<String> = _curChapterName.asStateFlow()

    private val _curChapterPageIndex = MutableStateFlow<Int>(0)
    val curChapterPageIndex : StateFlow<Int> = _curChapterPageIndex.asStateFlow()

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


        context.cacheDir
        context.applicationContext.filesDir

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
//                _epubPreferences.value = preferences.toRediumEpubPreferences()
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
            showOutChapters.addAll(allChapters.filter {
                !it.chapterName.isEmpty()
            })

            if (fetchBook(bookId)) {
                val newBook = _book.value ?: return@launchIO
                Logger.d("MainReaderViewModel:load reset book to pageController:${System.currentTimeMillis()}")
                pageController.resetBook(newBook) {//重新加载章节数
                    _uiState.value = LOAD_CHAPTER_SUCCESS(0)
                    Logger.d("MainReaderViewModel:load current chapter success:${System.currentTimeMillis()}")

                    _readProgression.value = pageController.progression

                    if (newBook.wordCount == 0L) {
                        loadChapterWords(newBook)
                    }
                }
            }
        }
    }

    private fun loadChapterWords(book: Book) {
        viewModelScope.launchIO {
            delay(1500)
            pageController.calcChaptersWords(book)
        }
    }

    fun resetReadingSession() {
        isReadingSessionActive = false
        lastLocatorChangeTime = 0L
    }

    override fun onCleared() {
        pageController.clear()
        currentDayStartTime = 0
//        _initialLocator.value = null
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
    override fun onLinkClick(href: String?, clickX: Float, clickY: Float) {
        Logger.d("MainReaderViewModel::onLinkClick:href=$href")
        if (!href.isNullOrEmpty()) {
            if (href.startsWith("http")) {
                //跳转到h5界面显示
            } else {
                val curIndex = curChapterIndex.value
                val currentChapter: BookChapter? = allChapters.firstOrNull { it.chapterIndex == curIndex }
                if (currentChapter == null) {
                    Logger.d("MainReaderViewModel::onLinkClick:currentChapter is null")
                    return
                }
                val currentChapterSrc = currentChapter.srcName
                if (currentChapterSrc.isNullOrEmpty()) {
                    Logger.e("MainReaderViewModel::onLinkClick:currentChapter src is null")
                    return
                }

                val linkContent = pageController.findLinkContent(href)
                if (!linkContent.isNullOrEmpty()) {                                                 //本章节中的注释
                    Logger.d("MainReadViewModel:onLinkClick:linkContent=${linkContent}")
                    if (!linkContent.isNullOrEmpty() && clickX >= 0 && clickY >= 0) {
                        _clickedLinkContent.value = LinkedContent(linkContent, clickX, clickY)
                    }
                } else {                                                                            //需要跳转到其他章节
                    var targetSrcName : String = ""
                    var targetAnchorId : String = ""
                    if (href.contains("#")) {
                        val hrefParts = href.split("#")
                        if (hrefParts.size == 2) {
                            targetSrcName = hrefParts[0]
                            targetAnchorId = hrefParts[1]
                        }
                    } else {
                        targetSrcName = href
                    }

                    val targetChapter = allChapters.find { chapter ->
                        chapter.srcName == href || chapter.srcName?.contains(targetSrcName) == true
                    }
                    if (targetChapter != null) {
                        pageController.changeChapter(targetChapter.chapterIndex)
                    } else {
                        Logger.d("MainReaderViewModel::onLinkClick:href=$href, no target chapter found")
                    }
                }
            }
        }
    }

    /***
     * 滑动切换界面，或者跳转切换界面时，通知进度刷新
     */
    override fun onPageChange() {
        Logger.d("MainReadViewModel:onPageChange")
        val newProgression =  pageController.progression
        _readProgression.value = newProgression
        _curChapterIndex.value = pageController.durChapterIndex
        _curChapterPageIndex.value = pageController.durPageIndex
        _curChapterName.value = pageController.curTextChapter?.title.orEmpty()

        viewModelScope.launch {
            if (isReadingSessionActive) {
                updateReadingTime()
            } else {
                isReadingSessionActive = true
                lastLocatorChangeTime = System.currentTimeMillis()
            }
            updateStartReadingDate() //尝试更新开始阅读时间

            if (newProgression >= 0.99) {  //尝试更新结束阅读时间
                updateEndReadingDate()
            }
        }
    }

    private var selectedLocator: Locator? = null

    override fun onSelectedText(startX: Float, startY: Float, endX: Float, endY: Float) {

        var rect = Rect(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt())
        _textToolbarRect.value = rect

        selectedLocator = null
        selectedLocator = this.pageController.getSelectedLocator()
        Logger.d("MainReadViewModel:onSelectedText:[$selectedLocator]")
        if (selectedLocator != null) {
            textToolbarOpen(true)
        }
    }

    fun handleHighlight(color: Color) {
        Logger.d("MainReadViewModel:handleHighlight")
        val bookid = _currentBookId.value ?: return
        Logger.d("MainReadViewModel:handleHighlight,bookid=$bookid")
        val locator = selectedLocator ?: return
        Logger.d("MainReadViewModel:handleHighlight,locator=$locator")
        val colorStr : String = color.toStringColor()
        Logger.d("MainReadViewModel:handleHighlight:color=$colorStr")
        val newAnnotation = BookAnnotation(
            bookId = bookid,
            locator = locator.toJsonString(),
            color = colorStr,
            note = null,
            type = AnnotationType.HIGHLIGHT
        )
        addAnnotation(newAnnotation)
        viewModelScope.launch {
            pageController.updateChapter(newAnnotation)
        }
    }

    fun handleUnderline(color: Color) {
        Logger.d("MainReadViewModel:handleUnderline")
        val bookid = _currentBookId.value ?: return
        val locator = selectedLocator ?: return
        val colorStr : String = color.toStringColor()
        Logger.d("MainReadViewModel:handleUnderline:bookid=$bookid, locator=${locator}, color=$colorStr")
        val newAnnotation = BookAnnotation(
            bookId = bookid,
            locator = locator.toJsonString(),
            color = colorStr,
            note = null,
            type = AnnotationType.UNDERLINE
        )
        addAnnotation(newAnnotation)
        viewModelScope.launch {
            pageController.updateChapter(newAnnotation)
        }
    }

    /****
     * 添加新的标注到数据库中, 并更新当前的_annotations数据集
     */
    fun addAnnotation(annotation: BookAnnotation) {
        viewModelScope.launch {
            val annotationId = addAnnotationUseCase(annotation)
            val newAnnotation = annotation.copy(id = annotationId)
            _annotations.value += newAnnotation
            _selectedAnnotation.value = newAnnotation
            currentBookId.value?.let { loadAnnotations(it) }
        }
    }

    override fun onSelectedCancel() {
        textToolbarOpen(false)
        _textToolbarRect.value = Rect(0,0,0,0)
    }

    private suspend fun updateReadingTime() {
        val currentTime = System.currentTimeMillis()
        if (lastLocatorChangeTime != 0L) {
            val sessionDuration = currentTime - lastLocatorChangeTime
            updateBookReadingTime(sessionDuration)
            updateReadingActivity(sessionDuration)
        }
        lastLocatorChangeTime = currentTime
    }

    private suspend fun updateStartReadingDate() {
        currentBookId.value?.let { bookId ->
            val book = getBookByIdUseCase(bookId)
            book?.let {
                if (it.startReadingDate == null) {
                    val updatedBook = it.copy(
                        startReadingDate = System.currentTimeMillis(),
                    )
                    updateBookUseCase(updatedBook)
                }
            }
        }
    }

    private suspend fun updateEndReadingDate() {
        currentBookId.value?.let { bookId ->
            val book = getBookByIdUseCase(bookId)
            book?.let {
                if (it.endReadingDate == null) {
                    val updatedBook = it.copy(
                        endReadingDate = System.currentTimeMillis(),
                        readingStatus = ReadingStatus.FINISHED.value
                    )
                    updateBookUseCase(updatedBook)
                }
            }
        }
    }

    private suspend fun updateBookReadingTime(sessionDuration: Long) {
        currentBookId.value?.let { bookId ->
            val book = getBookByIdUseCase(bookId)
            book?.let {
                val updatedBook = it.copy(readingTime = it.readingTime + sessionDuration)
                updateBookUseCase(updatedBook)
            }
        }
    }

    private suspend fun updateReadingActivity(sessionDuration: Long) {
        currentBookId.value?.let { bookId ->
            val book = getBookByIdUseCase(bookId)
            book?.let {
                val currentDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val existingActivity = getReadingActivityByDateUseCase(currentDate)
                if (existingActivity != null) {
                    val updatedActivity = existingActivity.copy(
                        readingTime = existingActivity.readingTime + sessionDuration
                    )
                    addOrUpdateReadingActivityUseCase(updatedActivity)
                } else {
                    val newActivity = ReadingActive(
                        date = currentDate,
                        readingTime = sessionDuration
                    )
                    addOrUpdateReadingActivityUseCase(newActivity)
                }
            }
        }
    }
    /***
     * 拖动阅读进度条来改变阅读位置
     */
    fun changePageByProgress(newProgress: Double):Boolean {
        var targetChapter: BookChapter? = null
        val curTextChapter = pageController.curTextChapter ?: return false
        if (curTextChapter.totalWordCount == 0L || pageController.isCalcChapterWords) {
            ToastUtil.show(stringResource(R.string.is_load_chapter_info))
            return false
        }
        for(index in 0 until allChapters.size) {
            val startProgress : Double = allChapters[index].chapterProgress.toDouble()
            val endProgress : Double = if (index < allChapters.size - 1) {
                allChapters[index + 1].chapterProgress.toDouble()
            } else {
                1.001
            }
            if (newProgress  >= startProgress && newProgress < endProgress) {
                targetChapter = allChapters[index]
            }
        }
        Logger.d("MainReadViewModel::changePageByProgress:newProgress[$newProgress],targetChapterIndex=${targetChapter?.chapterIndex}")
        targetChapter?.chapterIndex?.let { newChapterIndex ->
            pageController.changeChapter(newChapterIndex, newProgress)
        }
        return true
    }

    fun chaptersDrawerOpen(open: Boolean = true) {
        _isChaptersDrawerOpen.value = open
    }

    fun notesDrawerOpen(open: Boolean = true) {
        _isNotesDrawerOpen.value = open
    }

    fun bookmarksDrawerOpen(open: Boolean = true) {
        _isBookmarksDrawerOpen.value = open
    }

    fun highlightsDrawerOpen(open: Boolean = true) {
        _isHighlightsDrawerOpen.value = open
    }

    fun textToolbarOpen(open: Boolean = true) {
        _isShowTextToolbar.value = open
    }

    fun showColorSelectionPanel(open: Boolean = true) {
        _isShowColorSelectionPanel.value = open
    }

    fun uiSettingsOpen(open: Boolean = true) {
        _showUISettings.value = open
    }

    fun fontSettingsOpen(open: Boolean = true) {
        _showFontSettings.value = open
    }

    fun pageSettingsOpen(open: Boolean = true) {
        _showPageSettings.value = open
    }

    fun readerSettingsOpen(open: Boolean = true) {
        _showReaderSettings.value = open
    }

    fun onToolbarsVisibilityChanged() {
        _showMenu.value = !_showMenu.value
    }

    fun noteDialogOpen(open: Boolean = true) {
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

    fun getLatestAnnotations(): List<BookAnnotation> {
        return annotations.value
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

    fun clearClickedLinkContent() {
        _clickedLinkContent.value = null
    }

}
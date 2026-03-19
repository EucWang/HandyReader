package com.wxn.reader.presentation.pdfReader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.use_case.books.GetBookByIdUseCase
import com.wxn.reader.domain.use_case.books.IncrementReadingTimeUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.books.UpdatePdfProgressFieldsUseCase
import com.wxn.reader.domain.use_case.reading_activity.AddReadingActivityUseCase
import com.wxn.reader.domain.use_case.reading_activity.GetReadingActivityByDateUseCase
import com.wxn.reader.domain.use_case.reading_activity.IncrementReadingActivityTimeUseCase
import com.wxn.reader.domain.use_case.reading_progress.GetReadingProgressUseCase
import com.wxn.reader.events.VolumeEventBus
import com.wxn.reader.util.PdfBitmapConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val appPrefsUtil: AppPreferencesUtil,
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val pdfBitmapConverter: PdfBitmapConverter,
    private val updateBookUseCase: UpdateBookUseCase,
    private val updatePdfProgressFieldsUseCase: UpdatePdfProgressFieldsUseCase,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val addOrUpdateReadingActivityUseCase: AddReadingActivityUseCase,
    private val getReadingActivityByDateUseCase: GetReadingActivityByDateUseCase,
    private val incrementReadingTimeUseCase: IncrementReadingTimeUseCase,
    private val incrementReadingActivityTimeUseCase: IncrementReadingActivityTimeUseCase,
    savedStateHandle: SavedStateHandle,
    context: Application,
) : AndroidViewModel(context) {

    private val _book = MutableStateFlow<Book?>(null)
    val book = _book.asStateFlow()

    private val _pdfPages = MutableStateFlow<List<Bitmap?>>(emptyList())
    val pdfPages = _pdfPages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _backgroundColor = MutableStateFlow(Color.White)
    val backgroundColor = _backgroundColor.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount = _pageCount.asStateFlow()

    private val _pageOffset = MutableStateFlow(0)
    var pageOffset = _pageOffset.asStateFlow()


    private val _initialPage = MutableStateFlow(0)
    val initialPage = _initialPage.asStateFlow()

    private val _pdfId = MutableStateFlow<Long>(-1)
//    val pdfId = _pdfId.asStateFlow()

    private lateinit var contentUri: Uri
    private val pageCache = mutableMapOf<Int, Bitmap>()
    private var readingStartTime: Long = 0
    private var lastSaveTime: Long = 0

    // 书籍阅读时间节流更新器（6秒阈值）
//    private val bookReadingTimeUpdater = ThrottledUpdateManager<Long>(
//        updateFunction = { bookId, delta -> incrementReadingTimeUseCase(bookId, delta) },
//        timeWindowMs = 1000L,
//        maxAccumulatedMs = 6000L
//    )
//
//    // 阅读活动时长节流更新器（3秒阈值）
//    private val activityReadingTimeUpdater = ThrottledUpdateManager<Long>(
//        updateFunction = { date, delta -> incrementReadingActivityTimeUseCase(date, delta) },
//        timeWindowMs = 1000L,
//        maxAccumulatedMs = 3000L
//    )

    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    init {
        val pdfId = savedStateHandle.get<String>("bookId")?.toLongOrNull()
        val pdfUri = savedStateHandle.get<String>("bookUri")

        viewModelScope.launch {
            appPrefsUtil.appPrefsFlow.stateIn(viewModelScope).collect { pref ->
                _appPreferences.value = pref
                Logger.d("MainReadViewModel::init appPreferences[$pref]")
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            if (pdfId != null && pdfUri != null) {
                _pdfId.value = pdfId
                contentUri = Uri.parse(pdfUri)
                initializePdfInfo()
                _book.value = getBookByIdUseCase(pdfId)

                if (pdfId >= 0) {
                    _appPreferences.value?.let { pref ->
                        if (pref.lastBookId != pdfId) {
                            viewModelScope.launch {
                                appPrefsUtil.updateAppPreferences(pref.copy(lastBookId = pdfId))
                            }
                        }
                    }
                }

                startReadingSession()
            } else {
                _errorMessage.value = "Invalid PDF ID or URI"
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            VolumeEventBus.volumeUpEvents.collect {
                onVolumeUp()
            }
        }

        viewModelScope.launch {
            VolumeEventBus.volumeDownEvents.collect {
                onVolumeDown()
            }
        }
    }

    private fun onVolumeUp() {
        _pageOffset.value = -1
    }

    private fun onVolumeDown() {
        _pageOffset.value = 1
    }

    fun resetPageOffset() {
        _pageOffset.value = 0
    }

    private suspend fun initializePdfInfo() {
        try {
            _pageCount.value = pdfBitmapConverter.getPageCount(contentUri)
            _pdfPages.value = List(_pageCount.value) { null }

            val savedProgress = getReadingProgressUseCase(_pdfId.value)
            val savedPage = savedProgress.toIntOrNull() ?: 0
            _initialPage.value = savedPage

            _isLoading.value = false
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load PDF: ${e.message}"
            _isLoading.value = false
        }
    }

    fun loadInitialPages() {
        viewModelScope.launch {
            updateReadingTime()
            (0 until minOf(3, _pageCount.value)).forEach { loadPage(it) }
        }
    }

    fun loadPage(index: Int) {
        if (index < 0 || index >= _pageCount.value) return

        viewModelScope.launch {
            try {
                val bitmap = pageCache[index] ?: pdfBitmapConverter.pdfToBitmap(contentUri, index)
                bitmap.let {
                    pageCache[index] = it
                    val currentPages = _pdfPages.value.toMutableList()
                    currentPages[index] = it
                    _pdfPages.value = currentPages
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load page ${index + 1}: ${e.message}"
            }
        }
    }


    fun saveReadingProgress(currentPage: Int) {
        viewModelScope.launch {
            _book.value?.let { book ->
                val currentTime = System.currentTimeMillis()
                lastSaveTime = currentTime

                val newProgression = if (_pageCount.value != 0) {
                    ((currentPage).toFloat() / _pageCount.value.toFloat()) * 100f
                } else {
                    0f
                }

                val newReadingStatus = if (newProgression >= 98f) {
                    ReadingStatus.FINISHED
                } else {
                    ReadingStatus.IN_PROGRESS
                }

                updateReadingTime()

                // 使用选择性更新 PDF 进度字段
                updatePdfProgressFieldsUseCase(
                    bookId = book.id,
                    locator = currentPage.toString(),
                    progress = newProgression,
                    readingStatus = newReadingStatus,
                    endReadingDate = if (newReadingStatus == ReadingStatus.FINISHED) currentTime else null
                )

                // 更新内存中的 book 对象
                val updatedBook = book.copy(
                    locator = currentPage.toString(),
                    progress = newProgression,
                    readingStatus = newReadingStatus.value,
                    endReadingDate = if (newReadingStatus == ReadingStatus.FINISHED) currentTime else null
                )
                _book.value = updatedBook
            }
        }
    }

    suspend fun updateReadingTime(force:Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (lastSaveTime != 0L) {
            val sessionDuration = currentTime - lastSaveTime
            if (force || sessionDuration >= 3000) {
                updateBookReadingTime(sessionDuration)
                updateReadingActivity(sessionDuration)
                lastSaveTime = currentTime
            }
        } else {
            lastSaveTime = currentTime
        }
    }
    private suspend fun updateBookReadingTime(sessionDuration: Long) {
        _book.value?.id?.let { bookId ->
            incrementReadingTimeUseCase(bookId, sessionDuration)
        }
    }

    private suspend fun updateReadingActivity(sessionDuration: Long) {
        // 每次重新计算当前日期，避免跨天问题
        val currentDate = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        incrementReadingActivityTimeUseCase.invoke(currentDate, sessionDuration)
    }

    private fun startReadingSession() {
        readingStartTime = System.currentTimeMillis()
        lastSaveTime = readingStartTime
        _book.value?.let { book ->
            if (book.startReadingDate == null) {
                updateBook(book.copy(startReadingDate = readingStartTime))
            }
        }
    }


    private fun updateBook(updatedBook: Book) {
        viewModelScope.launch {
            var updatedBook2 = updatedBook
            if (updatedBook.progress.isFinite() && updatedBook.progress >= 98f) {
                updatedBook2 = updatedBook.copy(readingStatus = ReadingStatus.FINISHED.value)
            }
            updateBookUseCase(updatedBook2)
            _book.value = updatedBook2
        }
    }
}
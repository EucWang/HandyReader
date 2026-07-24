package com.wxn.reader.presentation.pdfReader

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.reader.R
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.use_case.books.GetBookByIdUseCase
import com.wxn.reader.domain.use_case.books.IncrementReadingTimeUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.books.UpdatePdfProgressFieldsUseCase
import com.wxn.reader.domain.use_case.books.DeleteBookUseCase
import com.wxn.reader.domain.use_case.reading_activity.AddReadingActivityUseCase
import com.wxn.reader.domain.use_case.reading_activity.GetReadingActivityByDateUseCase
import com.wxn.reader.domain.use_case.reading_activity.IncrementReadingActivityTimeUseCase
import com.wxn.reader.domain.use_case.reading_progress.GetReadingProgressUseCase
import com.wxn.reader.events.VolumeEventBus
import com.wxn.reader.util.FileAccessValidator
import com.wxn.reader.util.PdfBitmapConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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
    private val deleteBookUseCase: DeleteBookUseCase,
    private val readerPrefsUtil: ReaderPreferencesUtil,
    val savedStateHandle: SavedStateHandle,
    val context: Application,
) : AndroidViewModel(context) {


    private val _book = MutableStateFlow<Book?>(null)
    val book = _book.asStateFlow()

    private val _pdfPages = MutableStateFlow<List<Bitmap?>>(emptyList())
    val pdfPages = _pdfPages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

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

    private var contentUri: Uri? = null
    // 正在加载的页面集合（防止重复并发加载）
    private val loadingPages = mutableSetOf<Int>()
    // 加载失败的页面集合（用于 UI 显示重试）
    private val _failedPages = MutableStateFlow<Set<Int>>(emptySet())
    val failedPages = _failedPages.asStateFlow()
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

    private val _readerPreferences =
        MutableStateFlow<ReaderPreferences>(ReaderPreferencesUtil.defaultPreferences)
    val readerPreferences: StateFlow<ReaderPreferences> = _readerPreferences.asStateFlow()


    init {
        val pdfId = savedStateHandle.get<String>("bookId")?.toLongOrNull()
        val pdfUri = savedStateHandle.get<String>("bookUri")


        viewModelScope.launch {
            readerPrefsUtil.readerPrefsFlow.stateIn(viewModelScope).collect { pref ->
                _readerPreferences.value = pref
                VolumeEventBus.volumeKeyPageTurning =  pref.volumeKeyPageTurning
            }
        }

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
                _book.value = getBookByIdUseCase(pdfId)

                // ★ 兜底校验
                val src = _book.value?.source.orEmpty()
                val fileResult = FileAccessValidator.check(context, pdfUri, src)
                if (fileResult != FileAccessValidator.Result.ACCESSIBLE) {
                    _errorMessage.value = when (fileResult) {
                        FileAccessValidator.Result.FILE_NOT_FOUND ->
                            context.getString(R.string.file_deleted_externally_simple)
                        else -> context.getString(R.string.file_not_accessible)
                    }
                    _isLoading.value = false
                    return@launch
                }

                initializePdfInfo()

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
                _errorMessage.value = context.getString(R.string.pdf_invalid_id_or_uri)
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
            val uri = contentUri ?: throw IOException("PDF URI not set")
            _pageCount.value = pdfBitmapConverter.getPageCount(uri)
            _pdfPages.value = List(_pageCount.value) { null }

            val savedProgress = getReadingProgressUseCase(_pdfId.value)
            val savedPage = savedProgress.toIntOrNull() ?: 0
            _initialPage.value = savedPage

            _isLoading.value = false
        } catch (e: Exception) {
            _errorMessage.value = context.getString(R.string.pdf_failed_to_load, e.message)
            _isLoading.value = false
        }
    }

    /**
     * 从书库移除当前书籍（软删除）。返回 true 表示删除成功。
     */
    suspend fun removeCurrentBook(): Boolean = withContext(Dispatchers.IO) {
        _isDeleting.value = true
        try {
            _book.value?.let { deleteBookUseCase.invoke(it) }
            _book.value = null
            true
        } catch (e: Exception) {
            Logger.e("PdfReaderViewModel:removeCurrentBook failed", e)
            false
        } finally {
            _isDeleting.value = false
        }
    }

    fun loadInitialPages() {
        viewModelScope.launch {
            updateReadingTime()
            // 围绕恢复的初始页加载窗口，而非固定的前 3 页：
            // 否则从保存进度（如第 50 页）打开时，会解码 0..2 三个不可见页，
            // 随后又被 onCurrentPageChanged 淘汰。直接用同一套窗口逻辑。
            onCurrentPageChanged(_initialPage.value)
        }
    }

    /**
     * 加载指定页的 bitmap。
     * - 已加载 → 跳过
     * - 正在加载 → 跳过（防重复）
     * - 单页失败 → 仅日志 + 记入 failedPages，不影响全局状态
     */
    fun loadPage(index: Int) {
        if (index < 0 || index >= _pageCount.value) return
        val uri = contentUri ?: return

        // 已加载则跳过
        val currentPages = _pdfPages.value
        if (index < currentPages.size && currentPages[index] != null) return

        // 防重复并发加载
        if (index in loadingPages) return
        loadingPages.add(index)
        // 清除该页的失败标记（正在重试）
        _failedPages.value = _failedPages.value - index

        viewModelScope.launch {
            try {
                val metrics = context.resources.displayMetrics
                val bitmap = pdfBitmapConverter.pdfToBitmap(
                    uri, index, metrics.widthPixels, metrics.heightPixels
                )
                val updated = _pdfPages.value.toMutableList()
                if (index < updated.size) {
                    updated[index] = bitmap
                    _pdfPages.value = updated
                } else {
                    bitmap.recycle()  // 页面索引已超出范围（如 PDF 被替换），释放
                }
            } catch (e: Exception) {
                Logger.e("PdfReaderViewModel::loadPage($index) failed", e)
                // 单页失败仅日志 + 记入 failedPages，不设全局 errorMessage
                _failedPages.value = _failedPages.value + index
            } finally {
                loadingPages.remove(index)
            }
        }
    }

    /**
     * 由 Screen 在 pager 当前页变化时调用。
     * 执行两项操作：
     * 1. 淘汰窗口外的页面 bitmap（置 null，允许 GC）
     * 2. 预加载窗口内尚未加载的页面
     */
    fun onCurrentPageChanged(currentPage: Int) {
        if (_pageCount.value == 0) return

        val windowStart = (currentPage - CACHE_RADIUS).coerceAtLeast(0)
        val windowEnd = (currentPage + CACHE_RADIUS).coerceAtMost(_pageCount.value - 1)

        // 1. 淘汰窗口外页面（仅遍历非空索引，避免对超大 PDF 的全量拷贝）
        val currentList = _pdfPages.value
        val indicesToEvict = mutableListOf<Int>()
        for (i in currentList.indices) {
            if ((i < windowStart || i > windowEnd) && currentList[i] != null) {
                indicesToEvict.add(i)
            }
        }
        if (indicesToEvict.isNotEmpty()) {
            val updated = currentList.toMutableList()
            for (i in indicesToEvict) {
                updated[i] = null   // 仅置 null 断引用，不 recycle，让 GC 回收
            }
            _pdfPages.value = updated
        }

        // 2. 预加载窗口内未加载页面
        for (i in windowStart..windowEnd) {
            loadPage(i)
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

    override fun onCleared() {
        super.onCleared()
        // 仅断引用，不 recycle：AsyncImage 可能仍持有 bitmap 进行绘制，显式 recycle
        // 会抛 "Cannot draw a recycled Bitmap"。置空列表断引用，由 GC 统一回收，
        // 与滑动窗口淘汰策略保持一致。
        _pdfPages.value = emptyList()
        loadingPages.clear()
        _failedPages.value = emptySet()
    }

    companion object {
        /** 滑动窗口半径：当前页 ±10 = 最多缓存 21 页 */
        private const val CACHE_RADIUS = 10
    }
}
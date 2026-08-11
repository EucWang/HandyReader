package com.wxn.reader.presentation.home

import android.app.Application
import android.content.Context
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.bookparser.FileParser
import com.wxn.base.bean.Book
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.reader.R
import com.wxn.bookparser.domain.file.BookCacheManager
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.model.SortOrder
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.data.source.local.dao.DeletedBookDao
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.reader.domain.use_case.books.DeleteBookUseCase
import com.wxn.reader.domain.use_case.books.GetBookByIdUseCase
import com.wxn.reader.domain.use_case.books.GetBookUrisUseCase
import com.wxn.reader.domain.use_case.books.GetBooksUseCase
import com.wxn.reader.domain.use_case.books.InsertBookUseCase
import com.wxn.reader.data.backup.ContentHashCalculator
import com.wxn.reader.domain.use_case.shelves.AddBookToShelfUseCase
import com.wxn.reader.domain.use_case.shelves.AddShelfUseCase
import com.wxn.reader.domain.use_case.shelves.GetBooksForShelfUseCase
import com.wxn.reader.domain.use_case.shelves.GetShelvesUseCase
import com.wxn.reader.domain.use_case.shelves.RemoveBooksFromShelfUseCase
import com.wxn.reader.domain.use_case.shelves.RemoveShelfUseCase
import com.wxn.reader.navigation.buildReaderRoute
import androidx.compose.material3.SnackbarDuration
import com.wxn.reader.presentation.home.states.ImportProgressState
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.DocumentUtil
import com.wxn.reader.util.FileAccessValidator
import com.wxn.reader.util.UriResolutionResult
import com.wxn.base.util.launchIO
import com.wxn.base.util.Logger
import com.wxn.base.ext.goShop
import com.wxn.base.util.retry
import com.google.firebase.analytics.FirebaseAnalytics
import android.os.Bundle
import com.wxn.base.util.supportedExtensions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import androidx.core.net.toUri
import com.wxn.reader.data.model.ThemePreferences
import com.wxn.reader.data.source.local.GuidePrefUtil
import com.wxn.reader.data.source.local.ThemePreferencesUtil
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import android.database.sqlite.SQLiteFullException
import android.net.Uri

@HiltViewModel
class HomeViewModel
@Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val getBookUrisUseCase: GetBookUrisUseCase,
    private val insertBookUseCase: InsertBookUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val getBookByIdUseCase : GetBookByIdUseCase,
    private val booksRepository: BooksRepository,

    private val addShelfUseCase: AddShelfUseCase,
    private val removeShelfUseCase: RemoveShelfUseCase,
    private val getShelvesUseCase: GetShelvesUseCase,
    private val addBookToShelfUseCase: AddBookToShelfUseCase,
    private val removeBooksFromShelfUseCase: RemoveBooksFromShelfUseCase,
    private val getBooksForShelfUseCase: GetBooksForShelfUseCase,
    private val appPreferencesUtil: AppPreferencesUtil,
    private val themePreferencesUtil: ThemePreferencesUtil,
    private val guidePrefUtil: GuidePrefUtil,
    private val fileParser: FileParser,
    private val permissionRepository: PermissionRepository,
    private val deletedBookDao: DeletedBookDao,
    private val externalIntentBridge: com.wxn.reader.data.source.local.ExternalIntentBridge,
    private val externalFileResolver: com.wxn.reader.util.ExternalFileResolver,
    private val reviewPromptManager: com.wxn.reader.domain.ReviewPromptManager,
    private val reviewPrefsUtil: com.wxn.reader.data.source.local.ReviewPrefsUtil,
    private val contentHashCalculator: ContentHashCalculator,
    application: Application,
) : AndroidViewModel(application) {

    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()


    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    private val _themePreferences = MutableStateFlow<ThemePreferences?>(null)
    val themePreferences: StateFlow<ThemePreferences?> = _themePreferences.asStateFlow()

    private val _isAddingBooks = MutableStateFlow(false)
    val isAddingBooks: StateFlow<Boolean> = _isAddingBooks.asStateFlow()

    private val _isImportingFile = MutableStateFlow(false)
    val isImportingFile: StateFlow<Boolean> = _isImportingFile.asStateFlow()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _selectedBooks = MutableStateFlow<List<Book>>(emptyList())
    val selectedBooks: StateFlow<List<Book>> = _selectedBooks.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _booksInShelfSet = MutableStateFlow<Set<Long>>(emptySet())
    val booksInShelfSet: StateFlow<Set<Long>> = _booksInShelfSet.asStateFlow()

    private val _currentShelf = MutableStateFlow<Shelf?>(null)
    private val currentShelf: StateFlow<Shelf?> = _currentShelf.asStateFlow()


    private val _selectedTabRow = MutableStateFlow(-1)
    val selectedTabRow: StateFlow<Int> = _selectedTabRow.asStateFlow()

    var selectedTab = mutableStateOf(0)
    private var tabInitialized = false

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private @Volatile var hasOpenedLastBook: Boolean = false

    private var refreshJob: Job? = null
    private var silentScanJob: Job? = null
    private val _isSilentScanning = MutableStateFlow(false)

    private val _importProgressState = MutableStateFlow<ImportProgressState>(ImportProgressState.Idle)
    val importProgressState: StateFlow<ImportProgressState> = _importProgressState.asStateFlow()

    public data class SnackbarEvent(
        val message: String,
        val duration: SnackbarDuration
    )

    private val _snackbarMessage = MutableStateFlow<SnackbarEvent?>(null)
    val snackbarMessage: StateFlow<SnackbarEvent?> = _snackbarMessage.asStateFlow()

    private val _openLastBookRoute = MutableStateFlow<String>("")
    val openLastBookRoute :StateFlow<String> = _openLastBookRoute.asStateFlow()

    private val _showFabGuide = MutableStateFlow<Boolean?>(null)
    val showFabGuide: StateFlow<Boolean?> = _showFabGuide.asStateFlow()

    var showLayoutModal = mutableStateOf(false)
    var showSortModal = mutableStateOf(false)
    var showMetadataModal = mutableStateOf(false)

    private val _reselectBookId = MutableStateFlow<Long?>(null)
    val reselectBookId: StateFlow<Long?> = _reselectBookId.asStateFlow()

    private val _reselectBookFileType = MutableStateFlow<String?>(null)
    val reselectBookFileType: StateFlow<String?> = _reselectBookFileType.asStateFlow()

    /** ★ orphan 提升弹窗的"导入中"加载态(顺带修复 external 重选路径的 fire-and-forget 竞态)。 */
    private val _reselectInProgress = MutableStateFlow(false)
    val reselectInProgress: StateFlow<Boolean> = _reselectInProgress.asStateFlow()

    /** ★ 文件不可访问弹窗（scan/import/opds/external_import 来源，文件被外部删除/移动）。 */
    private val _fileMissingBookId = MutableStateFlow<Long?>(null)
    val fileMissingBookId: StateFlow<Long?> = _fileMissingBookId.asStateFlow()

    private val _fileMissingDeleteInProgress = MutableStateFlow(false)
    val fileMissingDeleteInProgress: StateFlow<Boolean> = _fileMissingDeleteInProgress.asStateFlow()

    /** ★ 文件重定位(Relocate):用户为改名/移动后的书重新指定文件位置。 */
    private val _relocateInProgress = MutableStateFlow(false)
    val relocateInProgress: StateFlow<Boolean> = _relocateInProgress.asStateFlow()

    /** 重定位时 contentHash/fileType 不匹配状态;null 表示无不匹配弹窗。 */
    private val _relocateMismatch = MutableStateFlow<RelocateMismatchState?>(null)
    val relocateMismatch: StateFlow<RelocateMismatchState?> = _relocateMismatch.asStateFlow()

    /**
     * ★ P1-1:orphan 文件不匹配状态。
     * 用户选的文件与 orphan 的 contentHash/fileType 不一致时,弹窗让用户选择:
     * ① 重新选择(回到文件选择器) ② 作为一本新书导入。
     * null 表示无不匹配弹窗。
     */
    private val _orphanMismatch = MutableStateFlow<OrphanMismatchState?>(null)
    val orphanMismatch: StateFlow<OrphanMismatchState?> = _orphanMismatch.asStateFlow()

    private var booksForShelfJob: Job? = null

    // ============ 好评引导弹窗（v5） ============
    /** O1：弹窗 UI 状态（Boolean，枚举仅2值已简化）。 */
    private val _showReviewPrompt = MutableStateFlow(false)
    val showReviewPrompt: StateFlow<Boolean> = _showReviewPrompt.asStateFlow()

    /** D2：系统评分调用防抖标志（放 ViewModel，跨配置变更存活）。 */
    private val reviewInFlight = MutableStateFlow(false)

    /** I1：本次弹窗是否已记数。
     *  E3 回退：onShown 不再在弹窗显示时调用，推迟到用户明确点"好评/反馈/稍后"才写 showCount。
     *  CAS 保证一次弹窗生命周期内只记一次（防多按钮并发）；点外部不触碰本标志。 */
    private val committedThisShow = java.util.concurrent.atomic.AtomicBoolean(false)

    /** P2-1：防延迟触发 + 外部关闭竞态。onDismissClick 设 true，collector delay 后消费。 */
    private val recentlyDismissed = java.util.concurrent.atomic.AtomicBoolean(false)

    /** P2-4：Firebase Analytics 埋点。 */
    private val firebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    /** H4：CAS 式置位，保证并发下只有一个触发源能弹窗。
     *  I1：弹窗显示时重置 committedThisShow=false，等待用户明确交互才记数。
     *  S4：弹窗成功后由调用方在协程内顺序调用 [markDailyCheckedIfWon] 标记当天，
     *  覆盖条件1/条件2，防止当天条件2 重复弹。 */
    private fun tryShow(): Boolean {
        val won = _showReviewPrompt.compareAndSet(false, true)
        if (won) committedThisShow.set(false)
        return won
    }

    /** S4：弹窗成功后顺序标记当天（必须在调用 tryShow 的同一协程内调用，保证原子顺序）。 */
    private suspend fun markDailyCheckedIfWon(won: Boolean) {
        if (won) reviewPromptManager.markDailyChecked()
    }

    init {
        observeThemePreferences()
        initializeApp()
        observeBooksFlow()
        observeExternalIntents()
        observeBookFinishedEvents()
        observeReadingSessionEnded()
    }

    /** 条件1：收集 MainReadViewModel 发来的"读完书"事件，delay 500ms 后闸门判断。 */
    private fun observeBookFinishedEvents() {
        viewModelScope.launch {
            reviewPromptManager.bookFinishedEvents.collect {
                try {
                    delay(500)
                    // P2-1：delay 期间用户可能已关闭弹窗，跳过本次触发
                    if (!recentlyDismissed.compareAndSet(true, false) &&
                        reviewPromptManager.shouldShow()) {
                        val won = tryShow()
                        markDailyCheckedIfWon(won)
                        if (won) logReviewShown("book_finished")
                    }
                } catch (e: Exception) {
                    Logger.e("observeBookFinishedEvents: error", e)
                }
            }
        }
    }

    /** 触发2：收集退出阅读页事件，delay 500ms 后评估连续天数（K4/K5：替代 ON_RESUME + tab 评估）。 */
    private fun observeReadingSessionEnded() {
        viewModelScope.launch {
            reviewPromptManager.readingSessionEnded.collect {
                try {
                    delay(500)
                    // P2-1：delay 期间用户可能已关闭弹窗，跳过本次触发
                    if (!recentlyDismissed.compareAndSet(true, false) &&
                        reviewPromptManager.checkConsecutiveDaysTrigger() &&
                        reviewPromptManager.shouldShow()) {
                        val won = tryShow()
                        markDailyCheckedIfWon(won)
                        if (won) logReviewShown("reading_session")
                    }
                } catch (e: Exception) {
                    Logger.e("observeReadingSessionEnded: error", e)
                }
            }
        }
    }

    /** D8/E4：ON_RESUME 仅重置标志（触发2 由 onCleared→SharedFlow 驱动，不再在此检查）。 */
    fun onAppForeground() {
        reviewInFlight.value = false
        committedThisShow.set(false)
        recentlyDismissed.set(false)
    }

    /** K7：点"给好评" → 立即永久禁弹 + 调系统 In-App Review（不管API成功）。 */
    fun onRateClick(activity: androidx.activity.ComponentActivity) {
        logReviewAction("rate_clicked")
        viewModelScope.launch { reviewPromptManager.onManualReviewClicked() }  // K7：永久禁弹
        _showReviewPrompt.value = false  // C7：立即关闭防重叠
        if (!reviewInFlight.compareAndSet(false, true)) return  // D2：防抖
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            // 365 天配额预判：超期直接 goShop 兜底，不浪费系统 API 调用
            val state = reviewPrefsUtil.getState()
            if (state.lastSystemReviewShownDate != 0L &&
                now - state.lastSystemReviewShownDate < 365L * com.wxn.base.util.DateUtil.DAY_MS
            ) {
                activity.goShop()
                reviewInFlight.value = false
                return@launch
            }
            // D1：全用 activity 生命周期绑定，绝无独立 scope（避免泄漏与回调错乱）
            val manager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                        .addOnCompleteListener(activity) {
                            viewModelScope.launch { reviewPrefsUtil.recordSystemReviewShown() }
                            firebaseAnalytics.logEvent("review_system_api", Bundle().apply { putString("result", "success") })
                            reviewInFlight.value = false
                        }
                } else {
                    // 失败兜底：打开商店详情页，保证用户永远有出口
                    firebaseAnalytics.logEvent("review_system_api", Bundle().apply { putString("result", "failed") })
                    activity.goShop()
                    reviewInFlight.value = false
                }
            }
        }
    }

    /** K7：点"提建议" → 立即永久禁弹（跳转由 HomeScreen 用 navController 处理）。 */
    fun onFeedbackClick() {
        logReviewAction("feedback_clicked")
        viewModelScope.launch { reviewPromptManager.onManualReviewClicked() }  // K7：永久禁弹
        _showReviewPrompt.value = false
    }

    /** 点"稍后"：关闭弹窗，进入 90 天冷却（commitShownOnce 已记数）。 */
    fun onLaterClick() {
        logReviewAction("later_clicked")
        commitShownOnce()  // I1：明确交互才记数（showCount+1、90天冷却）
        _showReviewPrompt.value = false
    }

    /** I1/K3：点弹窗外部/返回键关闭 → 分级冷却（14天短期/90天软退订），不消耗 showCount。 */
    fun onDismissClick() {
        logReviewAction("dismissed")
        recentlyDismissed.set(true)  // P2-1：防延迟触发竞态
        _showReviewPrompt.value = false
        viewModelScope.launch { reviewPromptManager.recordExternalDismiss() }
    }

    /** I1：一次弹窗生命周期内只记一次 showCount（CAS 防多按钮并发）。 */
    private fun commitShownOnce() {
        if (committedThisShow.compareAndSet(false, true)) {
            viewModelScope.launch { reviewPromptManager.onShown() }
        }
    }

    /** 用户手动点击"评价应用"入口 → 永久禁用自动弹窗。 */
    fun onManualReviewClicked() {
        logReviewAction("manual_clicked")
        viewModelScope.launch { reviewPromptManager.onManualReviewClicked() }
    }

    /** P2-4：弹窗展示埋点。 */
    private fun logReviewShown(triggerSource: String) {
        firebaseAnalytics.logEvent("review_prompt_shown", Bundle().apply {
            putString("trigger_source", triggerSource)
        })
    }

    /** P2-4：用户交互埋点。 */
    private fun logReviewAction(action: String) {
        firebaseAnalytics.logEvent("review_prompt_action", Bundle().apply {
            putString("action", action)
        })
    }

    private fun observeExternalIntents() {
        viewModelScope.launch {
            externalIntentBridge.pendingUri.collect { uri ->
                externalIntentBridge.clear()
                handleExternalFile(uri)
            }
        }
    }

    private fun observeThemePreferences() {
        viewModelScope.launch {
            themePreferencesUtil.themePrefsFlow.collect { preferences ->
                _themePreferences.value = preferences
            }
        }
    }

    private fun initializeApp() {
        viewModelScope.launch {
            val preferences = appPreferencesUtil.appPrefsFlow.first()
            coroutineScope {
                launch {
                    val lastTabIndex = preferences.lastOpenShelfTabIndex
                    loadShelves(lastTabIndex)
                }
                launch { observeBooks(preferences, silent = true) }
                launch { observeAppPreferences() }
                launch {
                    viewModelScope.launch {
                        _showFabGuide.value = !guidePrefUtil.isHomeFabGuideShown()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBooksFlow() {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow
                .flatMapLatest { preferences ->
                    val sortBy = preferences.sortBy
                    val isAscending = preferences.sortOrder == SortOrder.ASCENDING
                    val readingStatus = preferences.readingStatus
                    val fileType = preferences.fileTypes

                    val autoLoadLastBook = preferences.autoOpenLastRead
                    val lastOpenBookId = preferences.lastBookId

                    Logger.d(
                        "HomeViewModel::observeBooksFlow::hasOpenedLastBook[$hasOpenedLastBook]," +
                            "lastOpenBookId=[$lastOpenBookId]:autoLoadLastBook[$autoLoadLastBook]"
                    )

                    if (!hasOpenedLastBook) {
                        hasOpenedLastBook = true
                        if (autoLoadLastBook && lastOpenBookId > 0) {
                            launch {
                                openLastOpenBook(lastOpenBookId) { route ->
                                    _openLastBookRoute.value = route
                                }
                            }
                        }
                    }

                    if (!tabInitialized) {
                        tabInitialized = true
                        _selectedTabRow.value = if (lastOpenBookId > 0) 0 else 1
                    }

                    combine(
                        getBooksUseCase.getSortedBooks(sortBy, isAscending, readingStatus, fileType),
                        searchQuery,
                        currentShelf,
                        booksInShelfSet,
                    ) { books, query, shelf, shelfBookIds ->
                        books.filter { book ->
                            val matchesSearch =
                                query.isBlank() || book.title.contains(query, ignoreCase = true)
                            val matchesShelf = shelf == null || book.id in shelfBookIds
                            matchesSearch && matchesShelf
                        }
                    }
                }
                .collect { data ->
                    _books.value = data
                }
        }
    }

    fun resetLastBookOpenRoute() {
        _openLastBookRoute.value = ""
    }

    private fun loadShelves(lastOpenShelfTabIndex: Int = 0) {
        Logger.i("HomeViewModel::loadShelves::lastOpenShelfTabIndex=$lastOpenShelfTabIndex")
        viewModelScope.launchIO {
            var isFirstEmission = true
            getShelvesUseCase().collect { shelves ->
                _shelves.value = shelves

                if (isFirstEmission && shelves.isNotEmpty()) {
                    isFirstEmission = false
                    val safeIndex = lastOpenShelfTabIndex.coerceIn(0, shelves.size)
                    if (safeIndex > 0) {
                        updateCurrentShelf(shelves[safeIndex - 1], safeIndex, false)
                    }
                }
            }
        }
    }

    fun updateCurrentShelf(shelf: Shelf?, index: Int = 0, storeIndex: Boolean = true) {
        Logger.i("HomeViewModel::updateCurrentShelf::shelf=${shelf}, index=$index, storeIndex=$storeIndex")
        _currentShelf.value = shelf
        selectedTab.value = index

        if (storeIndex) {
            viewModelScope.launch {
                val appPref = _appPreferences.value ?: return@launch
                val appPreferences = appPref.copy(lastOpenShelfTabIndex = index)
                updateAppPreferences(appPreferences)
                Logger.d("HomeViewModel::updateCurrentShelf::shelf=${shelf}, index=$index")
            }
        }
    }

    fun updateCurrentTabRow(tab: Int) {
        Logger.i("HomeViewModel::updateCurrentTabRow::tab=$tab")
        _selectedTabRow.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun observeAppPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow.collect { preferences ->
                _appPreferences.value = preferences
            }
        }
    }

    fun refreshBooks() {
        silentScanJob?.cancel()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(500)
            if (_isImportingFile.value) return@launch
            showSnackbar(context.getString(R.string.refreshing_library))
                _appPreferences.value = appPreferencesUtil.appPrefsFlow.first()
            val appPref = _appPreferences.value ?: return@launch
            val scanDirectory = appPref.scanDirectories
            if (scanDirectory.isNotEmpty()) {
                observeBooks(appPref)
            } else {
                showSnackbar(context.getString(R.string.no_directory_set_for_scanning))
            }
        }
    }

    private fun showSnackbar(message: String, indefinite: Boolean = true) {
        _snackbarMessage.value = SnackbarEvent(
            message = message,
            duration = if (indefinite) SnackbarDuration.Long else SnackbarDuration.Short
        )
    }

    private fun hideSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun dismissImportDialog() {
        when (_importProgressState.value) {
            is ImportProgressState.Completed,
            is ImportProgressState.Error -> {
                _importProgressState.value = ImportProgressState.Idle
                _isAddingBooks.value = false
                _isImportingFile.value = false
            }
            else -> { }
        }
    }

    private fun observeBooks(preferences: AppPreferences, silent: Boolean = false) {
        if (silent) silentScanJob?.cancel()
        val job = viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Logger.e(throwable)
            if (silent) {
                showSnackbar(
                    stringResource(R.string.error_updateing_library, throwable.message ?: context.getString(R.string.unknown_error_occurred)),
                    indefinite = false
                )
            } else {
                _importProgressState.value =
                    ImportProgressState.Error(stringResource(R.string.error_updateing_library, throwable.message ?: "Unknown error occurred"))
                _isAddingBooks.value = false
                _isImportingFile.value = false
            }
        }) {
            if (silent) {
                if (_isSilentScanning.value) {
                    Logger.d("HomeViewModel::observeBooks skipped: silent scan in progress")
                    return@launch
                }
                _isSilentScanning.value = true
            } else {
                if (_isImportingFile.value) {
                    Logger.d("HomeViewModel::observeBooks skipped: import in progress")
                    return@launch
                }
                _isImportingFile.value = true
                hideSnackbar()
                _importProgressState.value = ImportProgressState.InProgress(0, 0)
            }
            val start = System.currentTimeMillis()
            try {
                val existingUris = getBookUrisUseCase().toSet()
                val deletedDocIds = deletedBookDao.getAllDocumentIds().toSet()
                val step1 = System.currentTimeMillis()
                Logger.d("HomeViewModel::observeBooks::step1=${step1 - start}")

                val documentFiles = mutableListOf<DocumentFile>()
                preferences.scanDirectories.forEach { directoryPath ->
                    Logger.d("HomeViewModel::observeBooks::directoryPath=$directoryPath")
                    val filesInDirectory = DocumentUtil.getFilesFromDirectory(context, directoryPath.toUri())
                    Logger.d("HomeViewModel::observeBooks::filesInDirectory=${filesInDirectory.size}")
                    documentFiles.addAll(filesInDirectory)
                }
                val step2 = System.currentTimeMillis()
                Logger.d("HomeViewModel::observeBooks::step2=${step2 - step1}")
                val uniqueFiles = documentFiles.distinctBy { it.uri.toString() }

                val newBooks = uniqueFiles.filter { documentFile ->
                    val uri = documentFile.uri
                    val bookUriString = uri.toString()
                    if (existingUris.contains(bookUriString)) return@filter false

                    if (deletedDocIds.isNotEmpty()) {
                        val docId = extractDocumentIdSafely(uri)
                        if (docId != null && deletedDocIds.contains(docId)) return@filter false
                    }

                    true
                }

                Logger.d("HomeViewModel::observeBooks::newBooks.size=${newBooks.size}")
                if (newBooks.isNotEmpty()) {
                    if (!silent) {
                        _isAddingBooks.value = true
                        _importProgressState.value = ImportProgressState.InProgress(0, newBooks.size)
                    }

                    val hasSpace = BookCacheManager.getInstance()
                        ?.hasAvailableSpace() ?: true
                    if (!hasSpace) {
                        if (silent) {
                            showSnackbar(stringResource(R.string.import_error_no_space), indefinite = true)
                        } else {
                            _importProgressState.value = ImportProgressState.Error(
                                stringResource(R.string.import_error_no_space)
                            )
                        }
                        return@launch
                    }

                    var hasStorageError = false
                    val results = mutableListOf<Int>()
                    val completedCount = AtomicInteger(0)
                    val totalBooks = newBooks.size

                    run breaking@ {
                        newBooks.chunked(2).forEachIndexed { batchIndex, batch ->
                            if (hasStorageError) return@breaking
                            if (batchIndex > 0) yield()
                            coroutineScope {
                                val batchResults = batch.map { documentFile ->
                                    async(Dispatchers.IO) {
                                        try {
                                            val result = addNewBook(documentFile)
                                            completedCount.incrementAndGet()
                                            result
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            Logger.w("observeBooks: parallel addNewBook failed：$e")
                                            completedCount.incrementAndGet()
                                            -1
                                        }
                                    }
                                }.awaitAll()
                                results.addAll(batchResults)

                                if (batchResults.any { it == -3 }) {
                                    hasStorageError = true
                                    if (silent) {
                                        showSnackbar(stringResource(R.string.import_error_no_space), indefinite = true)
                                    } else {
                                        _importProgressState.value = ImportProgressState.Error(
                                            stringResource(R.string.import_error_no_space)
                                        )
                                    }
                                } else {
                                    if (!silent) {
                                        _importProgressState.value = ImportProgressState.InProgress(
                                            completedCount.get(), totalBooks
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!hasStorageError) {
                        val successCount = results.count { it > 0 }
                        val duplicateCount = results.count { it == -2 }
                        val failCount = results.count { it == -1 }
                        if (silent) {
                            if (successCount > 0) {
                                showSnackbar(
                                    stringResource(R.string.new_books_imported, successCount),
                                    indefinite = false
                                )
                            }
                        } else {
                            _importProgressState.value = ImportProgressState.Completed(
                                successCount, duplicateCount, failCount
                            )
                        }
                    }
                    if (!silent) _isAddingBooks.value = false
                } else {
                    if (!silent) _importProgressState.value = ImportProgressState.Idle
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (silent) {
                    showSnackbar(
                        stringResource(R.string.error_updateing_library, e.message ?: context.getString(R.string.unknown_error_occurred)),
                        indefinite = false
                    )
                } else {
                    _importProgressState.value = ImportProgressState.Error(
                        stringResource(R.string.error_updateing_library, e.message ?: "Unknown error occurred")
                    )
                }
                Logger.e("HomeViewModel::Error observing books:${e.message}")
            } finally {
                if (silent) {
                    _isSilentScanning.value = false
                } else {
                    _isAddingBooks.value = false
                    _isImportingFile.value = false
                }
            }
        }
        if (silent) {
            silentScanJob = job
        }
    }

    fun updateAppPreferences(newPreferences: AppPreferences) {
        viewModelScope.launch {
//            Logger.d("HomeViewModel::updateAppPreferences:the home viewModel")
            appPreferencesUtil.updateAppPreferences(newPreferences)
            _appPreferences.value = newPreferences
        }
    }

    fun markFabGuideShown() {
        _showFabGuide.value = false
        viewModelScope.launch {
            guidePrefUtil.setHomeFabGuideShown()
        }
    }

    fun resetLayoutPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.resetLayoutPreferences()
            _appPreferences.value = appPreferencesUtil.appPrefsFlow.first()
        }
    }

    fun addShelf(shelfName: String) {
        viewModelScope.launchIO {
            try {
                val currentShelves = _shelves.value.toMutableList()
                val newOrder = currentShelves.size
                addShelfUseCase(shelfName, newOrder)
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.failed_to_add_shelf, e.message.orEmpty()))
            }
        }
    }

    fun removeShelf(shelf: Shelf) {
        viewModelScope.launch {
            try {
                removeShelfUseCase(shelf)
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.failed_remove_shelf, e.message.orEmpty()))
            }
        }
    }

    fun removeBooks(books: List<Book>) {
        viewModelScope.launch {
            if (_isImportingFile.value) {
                showSnackbar(context.getString(R.string.please_wait_until_import_completes))
                return@launch
            }
            try {
                books.forEach {
                    deleteBookUseCase(it)
                }
                showSnackbar(stringResource(R.string.book_remove_success))
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.failed_delete_book, e.message.toString()))
            }
        }
    }

    fun addBooksToShelves(bookIds: List<Long>, shelfIds: List<Long>) {
        viewModelScope.launch {
            try {
                bookIds.forEach { bookId ->
                    shelfIds.forEach { shelfId ->
                        addBookToShelfUseCase(bookId, shelfId)
                    }
                }
                showSnackbar(stringResource(R.string.books_add_shelf_success))
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.add_book_shelf_failed, e.message.toString()))
            }
        }
    }

    fun removeBooksFromShelves(bookIds: List<Long>, shelfIds: List<Long>) {
        viewModelScope.launch {
            try {
                bookIds.forEach { bookId ->
                    shelfIds.forEach { shelfId ->
                        removeBooksFromShelfUseCase(bookId, shelfId)
                    }
                }

                _booksInShelfSet.value -= bookIds.toSet()
//                showSnackbar("Books removed from shelf successfully" )
                showSnackbar(stringResource(R.string.remove_books_from_shelf_success))
            } catch (e: Exception) {
//                showSnackbar("Failed to remove books from shelf: ${e.message}" )
                showSnackbar(stringResource(R.string.remove_books_from_shelf_fail, e.message.toString()))
            }
        }
    }

    fun getBooksForShelfSelection(shelfId: Long): Flow<List<Book>> {
        return getBooksForShelfUseCase(shelfId)
    }

    fun getBooksForShelf(shelfId: Long)  {
        Logger.d("HomeViewModel::getBooksForShelf::shelfId=$shelfId")
        booksForShelfJob?.cancel()
        _booksInShelfSet.value = emptySet()
        booksForShelfJob = viewModelScope.launch {
            getBooksForShelfUseCase(shelfId).collect { books: List<Book> ->
                _booksInShelfSet.value = books.map { it.id }.toSet()
                Logger.d("HomeViewModel::getBooksForShelf::booksList.size=${books.size}")
            }
        }
    }

    fun toggleBookSelection(book: Book) {
        _selectedBooks.value = if (_selectedBooks.value.any { it.id == book.id }) {
            _selectedBooks.value.filter { it.id != book.id }
        } else {
            _selectedBooks.value + book
        }
        _selectionMode.value = _selectedBooks.value.isNotEmpty()
    }

    fun selectAllBooks(books: List<Book>){
        _selectedBooks.value = books
    }

    fun clearBookSelection() {
        Logger.i("HomeViewModel::clearBookSelection")
        _selectedBooks.value = emptyList()
        _selectionMode.value = false
        showMetadataModal.value = false
    }

    private fun extractDocumentIdSafely(uri: Uri): String? {
        return try {
            if (uri.scheme == "content") DocumentsContract.getDocumentId(uri) else null
        } catch (e: Exception) { null }
    }

    private suspend fun addNewBook(
        documentFile: DocumentFile,
        source: String = "scan",
        documentId: String? = null
    ): Int {
        var ret = 0
        withContext(Dispatchers.IO) {
            try {
                val docId = documentId ?: extractDocumentIdSafely(documentFile.uri)
                val cachedFile = CachedFileCompat.fromUri(context,
                    documentFile.uri, CachedFileCompat.build(
                        name = documentFile.name,
                        path = documentFile.uri.path,
                        isDirectory = false
                    ))
                val book = fileParser.parse(cachedFile)
                if (book != null) {
                    val existingUri = documentFile.uri.toString()
                    val existingBook = booksRepository.getBookByUri(existingUri)
                    if (existingBook != null && !existingBook.deleted) {
                        ret = -2
                    } else if (!book.contentHash.isNullOrBlank() &&
                        booksRepository.hasActiveBookWithHash(book.contentHash!!)
                    ) {
                        // ★ 扫描入口 hash 预查(方案 A 双保险之一):命中重复直接跳过 insert
                        // book.contentHash 由 FileParserImpl 解析时与 CRC 合并算填,
                        // 见 docs/plans/2026-07-07-扫描导入同书去重.md §4.1
                        ret = -2
                    } else {
                        retry {
                            insertBookUseCase(book.copy(importStatus = 0, source = source, documentId = docId))
                        }
                        ret = 1
                    }
                } else {
                    if (source == "import") {
                        ret = -1
                    } else {
                        Logger.e("HomeViewModel::Error add book: ${documentFile.name}")
                        val extension = documentFile.name?.substringAfterLast(".", "") ?: ""
                        val failedBook = Book(
                            title = documentFile.name ?: "Unknown",
                            filePath = documentFile.uri.toString(),
                            author = "",
                            description = null,
                            coverImage = null,
                            scrollIndex = 0,
                            scrollOffset = 0,
                            progress = 0f,
                            lastOpened = null,
                            category = null,
                            fileType = if (extension.isNotEmpty()) extension else FileType.UNKNOWN.typeName(),
                            publishDate = null,
                            publisher = null,
                            language = null,
                            numberOfPages = null,
                            wordCount = 0,
                            locator = "",
                            deleted = false,
                            rating = 0f,
                            isFavorite = false,
                            readingStatus = 0,
                            readingTime = 0,
                            startReadingDate = null,
                            endReadingDate = null,
                            review = null,
                            duration = null,
                            narrator = null,
                            crc = 0,
                            cachedDir = null,
                            importStatus = -1,
                            source = source,
                            documentId = docId
                        )
                        try {
                            insertBookUseCase(failedBook)
                        } catch (e: Exception) {
                            Logger.e("HomeViewModel::Error saving failed book: ${e.message}")
                        }
                    }
                }
            } catch (e: SQLiteFullException) {
                Logger.e("HomeViewModel::Database full: ${documentFile.name}")
                ret = -3
            } catch (e: Exception) {
                Logger.e("HomeViewModel::Error adding book: ${documentFile.name}, ${e.message}")
                throw e
            }
        }
        return ret
    }

    fun updateLastOpened(bookId: Long) {
        viewModelScope.launch {
            try {
                booksRepository.updateLastOpened(bookId, System.currentTimeMillis())
            } catch (e: Exception) {
                Logger.e("HomeViewModel::updateLastOpened failed: ${e.message}")
            }
        }
    }

    fun updateRating(bookId: Long, rating: Float) {
        viewModelScope.launch {
            try {
                booksRepository.updateRating(bookId, rating)
            } catch (e: Exception) {
                Logger.e("HomeViewModel::updateRating failed: ${e.message}")
            }
        }
    }

    fun toggleFavorite(books: List<Book>) {
        if (books.isEmpty()) return
        viewModelScope.launch {
            try {
                val allFavorited = books.all { it.isFavorite }
                val now = System.currentTimeMillis()
                books.forEach { book ->
                    val newFavorite = !allFavorited
                    val newDate = if (newFavorite) now else null
                    booksRepository.updateFavorite(book.id, newFavorite, newDate)
                }
            } catch (e: Exception) {
                Logger.e("HomeViewModel::toggleFavorite failed: ${e.message}")
                showSnackbar(stringResource(R.string.failed_to_update_favorite))
            }
        }
    }

    fun updateReadingDate(bookId: Long, isStart: Boolean, date: Long?) {
        viewModelScope.launch {
            try {
                if (isStart) {
                    booksRepository.updateStartReadingDateOnly(bookId, date)
                } else {
                    booksRepository.updateEndReadingDateOnly(bookId, date)
                }
            } catch (e: Exception) {
                Logger.e("HomeViewModel::updateReadingDate failed: ${e.message}")
            }
        }
    }

    fun updateReadingStatus(bookId: Long, newStatus: ReadingStatus) {
        viewModelScope.launch {
            try {
                when (newStatus) {
                    ReadingStatus.NOT_STARTED -> booksRepository.updateReadingStatusFull(
                        bookId, newStatus, null, null, 0L, 0f
                    )
                    ReadingStatus.IN_PROGRESS -> booksRepository.updateReadingStatusFull(
                        bookId, newStatus, System.currentTimeMillis(), null, 0L, 0f
                    )
                    ReadingStatus.FINISHED -> booksRepository.updateReadingStatusFull(
                        bookId, newStatus, null, System.currentTimeMillis(), 0L, 100f
                    )
                }
            } catch (e: Exception) {
                Logger.e("HomeViewModel::updateReadingStatus failed: ${e.message}")
            }
        }
    }

    suspend fun getFullBookById(bookId: Long): Book? {
        return getBookByIdUseCase(bookId)
    }

    fun sortBooks(sortOption: SortOption, sortOrder: SortOrder, newPrefs: AppPreferences) {
    }

    fun filterBooks(option: Any) {
        viewModelScope.launch {
            val currentPreferences = _appPreferences.value ?: return@launch
            val newPreferences = when (option) {
                is ReadingStatus -> {
                    val newStatuses = if (option in currentPreferences.readingStatus) {
                        currentPreferences.readingStatus - option
                    } else {
                        currentPreferences.readingStatus + option
                    }
                    Logger.d("HomeViewModel:filterBooks:readingStatus[${newStatuses}]")
                    currentPreferences.copy(readingStatus = newStatuses)
                }

                is FileType -> {
                    val newFileTypes = if (option in currentPreferences.fileTypes) {
                        emptySet()  // Deselect if it's the only selected option
                    } else {
                        setOf(option)  // Select only this option
                    }
                    Logger.d("HomeViewModel:filterBooks:fileType[${newFileTypes}]")
                    currentPreferences.copy(fileTypes = newFileTypes)
                }

                else -> currentPreferences
            }

            updateAppPreferences(newPreferences)
        }
    }

//    fun purchasePremium(purchaseHelper: PurchaseHelper) {
//        purchaseHelper.makePurchase()
//        viewModelScope.launch {
//            purchaseHelper.isPremium.collect { isPremium ->
//                updatePremiumStatus(isPremium)
//            }
//        }
//    }
//
//    fun updatePremiumStatus(isPremium: Boolean) {
//        viewModelScope.launch {
//            val currentPreferences = appPreferencesUtil.appPrefsFlow.first()
//            if (currentPreferences.isPremium != isPremium) {
//                val updatedPreferences = currentPreferences.copy(isPremium = isPremium)
//                Logger.d("HomeViewModel:updatePremiumStatus:the home viewModel")
//                appPreferencesUtil.updateAppPreferences(updatedPreferences)
//                _appPreferences.value = updatedPreferences
//            }
//        }
//    }

    fun addScanDirectory(uri: Uri) {
        viewModelScope.launch {
            val appPref = _appPreferences.value
            val currentDirectories = appPref?.scanDirectories ?: return@launch
            val directory = uri.toString()
            permissionRepository.grantPersistableUriPermission(uri)
            if (!currentDirectories.contains(directory)) {
                val updatedDirectories = currentDirectories + directory
                Logger.d("SettingsViewModel:addScanDirectory:the Settings viewModel")
                val newPrefs = appPref.copy(scanDirectories = updatedDirectories)
                appPreferencesUtil.updateAppPreferences(newPrefs)
                _appPreferences.value = newPrefs
                refreshBooks()
            }
        }
    }

    fun importSingleFile(uri: Uri) {
        viewModelScope.launch {
            silentScanJob?.cancel()
            if (_isImportingFile.value) return@launch
            _isImportingFile.value = true
            try {
                val documentFile = DocumentFile.fromSingleUri(context, uri)
                if (documentFile == null || !documentFile.exists()) {
                    showSnackbar(stringResource(R.string.import_file_failed, ""))
                    _isImportingFile.value = false
                    return@launch
                }
                val extension = documentFile.name?.substringAfterLast(".", "")?.lowercase() ?: ""
                if (extension !in supportedExtensions()) {
                    showSnackbar(stringResource(R.string.import_file_unsupported_format))
                    _isImportingFile.value = false
                    return@launch
                }

                permissionRepository.grantPersistableUriPermission(uri)

                val docId = extractDocumentIdSafely(uri)
                if (docId != null && deletedBookDao.exists(docId)) { //已删除文件白名单
                    deletedBookDao.deleteByDocumentId(docId) //从已删除文件白名单 中移除
                }

                _isAddingBooks.value = true
                showSnackbar(stringResource(R.string.adding_new_book_to_library))
                //添加新书籍
                val result = addNewBook(documentFile, source = "import", documentId = docId)
                _isAddingBooks.value = false
                _isImportingFile.value = false

                when (result) {
                    1 -> showSnackbar(stringResource(R.string.import_file_success, documentFile.name ?: ""))
                    -2 -> showSnackbar(stringResource(R.string.import_file_already_exists))
                    else -> showSnackbar(stringResource(R.string.import_file_failed, documentFile.name ?: ""))
                }
            } catch (e: Exception) {
                _isAddingBooks.value = false
                _isImportingFile.value = false
                showSnackbar(stringResource(R.string.import_file_failed, e.message.orEmpty()))
            }
        }
    }

    suspend fun openLastOpenBook(lastOpenBookId: Long, onRouteNav: (String)->Unit) {
        Logger.d("HomeViewModel::openLastOpenBook::lastBookId[$lastOpenBookId]")
        if (lastOpenBookId > 0) {
            getBookByIdUseCase(lastOpenBookId)?.let { lastBook ->
                Logger.d("HomeViewModel::openLastOpenBook::lastBook[$lastBook]")
                openBook(lastBook, onRouteNav)
            }
        }
    }

    fun openBook(openedBook: Book, onRouteNav: (String)->Unit) {
        val route = buildReaderRoute(openedBook.id,
            openedBook.fileType,
            openedBook.filePath,
            openedBook.coverImage,
            title = openedBook.title,
            author = openedBook.author)
        if (route.isNotEmpty()) {
            onRouteNav(route)
        }
    }

    fun updateHomeBgImage(path:String?) {
        viewModelScope.launch {
            themePreferencesUtil.updateBgImage(path ?: "")
        }
    }

    fun handleExternalFile(uri: Uri) {
        viewModelScope.launch {
            if (_isImportingFile.value) return@launch
            silentScanJob?.cancel()
            _isImportingFile.value = true
            _isAddingBooks.value = true
            try {
                val result = processExternalFile(uri)
                _isAddingBooks.value = false
                _isImportingFile.value = false
                when (result) {
                    is ExternalFileResult.NavigateToReader -> {
                        val route = buildReaderRoute(
                            bookId = result.bookId,
                            fileType = result.fileType,
                            filePath = result.filePath,
                            coverImage = result.coverImage,
                            title = result.title,
                            author = result.author
                        )
                        externalIntentBridge.submitNavigationRoute(route)
                    }
                    is ExternalFileResult.Error -> {
                        externalIntentBridge.submitNavigationError(result.message)
                    }
                    null -> {
                        externalIntentBridge.submitNavigationError(
                            context.getString(R.string.import_file_failed, "")
                        )
                    }
                }
            } catch (e: Exception) {
                _isAddingBooks.value = false
                _isImportingFile.value = false
                externalIntentBridge.submitNavigationError(
                    context.getString(R.string.import_file_failed, e.message.orEmpty())
                )
            }
        }
    }

    private fun resolveFilePath(
        resolution: UriResolutionResult,
        uri: Uri,
        fileName: String?,
        extension: String
    ): Pair<String, String>? {
        if (resolution.permissionPersisted) {
            return resolution.uri to "external"
        }
        val nameWithoutExt = fileName?.substringBeforeLast(".")?.trim() ?: "book"
        val safeName = nameWithoutExt
            .replace("[^a-zA-Z0-9._\\u4e00-\\u9fff]".toRegex(), "_")
            .takeLast(80)
        val destFileName = "${safeName}_${java.util.UUID.randomUUID()}.$extension"
        val localPath = externalFileResolver.importToStorage(uri, destFileName)
        return if (localPath != null) {
            localPath to "external_import"
        } else {
            null
        }
    }

    private fun cleanupImportedFile(filePath: String) {
        try {
            val uri = Uri.parse(filePath)
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path ?: return)
                val importedDir = java.io.File(context.filesDir, com.wxn.base.util.PathUtil.PATH_IMPORTED_BOOKS).absolutePath
                if (file.exists() && file.absolutePath.startsWith(importedDir)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun processExternalFile(uri: Uri): ExternalFileResult? {
        return withContext(Dispatchers.IO) {
            val fileName = externalFileResolver.extractFileName(uri)
            val extension = fileName?.substringAfterLast(".", "")?.lowercase() ?: ""

            if (extension !in supportedExtensions()) {
                return@withContext ExternalFileResult.Error(
                    stringResource(R.string.import_file_unsupported_format)
                )
            }

            val resolution = externalFileResolver.resolve(uri)

            if (resolution.permissionPersisted) {
                val existingByUri = booksRepository.getBookByUri(resolution.uri)
                if (existingByUri != null && !existingByUri.deleted) {
                    return@withContext ExternalFileResult.NavigateToReader(
                        bookId = existingByUri.id,
                        fileType = existingByUri.fileType,
                        filePath = existingByUri.filePath,
                        coverImage = existingByUri.coverImage,
                        title = existingByUri.title,
                        author = existingByUri.author
                    )
                }
            }

            val docId = extractDocumentIdSafely(uri)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            if (documentFile == null || !documentFile.exists()) {
                return@withContext ExternalFileResult.Error(
                    stringResource(R.string.book_file_not_found)
                )
            }

            val cachedFile = CachedFileCompat.fromUri(
                context,
                uri,
                CachedFileCompat.build(
                    name = fileName,
                    path = uri.path,
                    isDirectory = false
                )
            )
            val book = fileParser.parse(cachedFile)
            if (book == null) {
                return@withContext ExternalFileResult.Error(
                    stringResource(R.string.import_file_failed, fileName ?: "")
                )
            }

            if (book.crc != 0) {
                val existingByCrc = booksRepository.getBookByCrc(book.crc)
                if (existingByCrc != null) {
                    return@withContext ExternalFileResult.NavigateToReader(
                        bookId = existingByCrc.id,
                        fileType = existingByCrc.fileType,
                        filePath = existingByCrc.filePath,
                        coverImage = existingByCrc.coverImage,
                        title = existingByCrc.title,
                        author = existingByCrc.author
                    )
                }
            }

            val resolved = resolveFilePath(resolution, uri, fileName, extension)
            if (resolved == null) {
                return@withContext ExternalFileResult.Error(
                    stringResource(R.string.import_file_failed, fileName ?: "")
                )
            }
            val (filePath, source) = resolved

            val bookToInsert = book.copy(
                importStatus = 0,
                source = source,
                documentId = docId,
                filePath = filePath
            )

            val insertedId = insertBookUseCase(bookToInsert)
            if (insertedId > 0) {
                ExternalFileResult.NavigateToReader(
                    bookId = insertedId,
                    fileType = extension,
                    filePath = filePath,
                    coverImage = bookToInsert.coverImage,
                    title = bookToInsert.title,
                    author = bookToInsert.author
                )
            } else {
                cleanupImportedFile(filePath)
                ExternalFileResult.Error(
                    stringResource(R.string.import_file_failed, fileName ?: "")
                )
            }
        }
    }

    fun openBookWithAccessCheck(book: Book): Boolean {
        // ① 统一文件可访问性校验（覆盖所有 source）
        val result = FileAccessValidator.check(context, book.filePath, book.source)

        // ② 可访问 → 放行（清除可能残留的 fileMissing 状态，防止竞态残留）
        if (result == FileAccessValidator.Result.ACCESSIBLE) {
            _fileMissingBookId.value = null
            return true
        }

        // ③ 不可访问 + external → 走原 reselect 弹窗（允许重新选择文件）
        if (book.source == SOURCE_EXTERNAL) {
            _reselectBookId.value = book.id
            _reselectBookFileType.value = book.fileType
            _reselectInProgress.value = false
            return false
        }

        // ④ 不可访问 + 其他来源 → fileMissing 弹窗（从书库移除/忽略）
        _fileMissingBookId.value = book.id
        _fileMissingDeleteInProgress.value = false
        return false
    }

    fun clearReselectBookId() {
        _reselectBookId.value = null
        _reselectBookFileType.value = null
        _reselectInProgress.value = false
    }

    /** ★ 从书库移除文件不可访问的书籍（软删除，可在回收站恢复）。 */
    fun removeFileMissingBook(bookId: Long) {
        viewModelScope.launchIO {
            _fileMissingDeleteInProgress.value = true
            try {
                getBookByIdUseCase(bookId)?.let { deleteBookUseCase(it) }
                _fileMissingBookId.value = null
            } catch (e: Exception) {
                Logger.e("removeFileMissingBook failed", e)
                showSnackbar(context.getString(R.string.delete_failed_retry), indefinite = false)
            } finally {
                _fileMissingDeleteInProgress.value = false
            }
        }
    }

    fun clearFileMissingState() {
        _fileMissingBookId.value = null
        _fileMissingDeleteInProgress.value = false
    }

    // ──────────────────────────────────────────────────────────────
    // ★ 文件重定位(Relocate)流程
    // ──────────────────────────────────────────────────────────────

    /**
     * 用户在「文件不可访问」弹窗点「重新定位」后,通过系统文件选择器选了新文件。
     *
     * 流程:fileType 校验 → contentHash 比对 → resolveFilePath → 更新 DB →
     *      清理旧文件 → 清状态 → 导航到阅读器。
     *
     * contentHash 不匹配或 fileType 不一致时弹出 [RelocateMismatchState] 让用户重选。
     */
    fun relocateBook(bookId: Long, newUri: Uri) {
        if (_relocateInProgress.value) {
            Logger.w("relocateBook: already in progress, ignore")
            return
        }
        _relocateInProgress.value = true
        viewModelScope.launchIO {
            try {
                withContext(Dispatchers.IO) {
                    val book = booksRepository.getBookById(bookId)
                        ?: throw IllegalStateException("book not found: $bookId")

                    val fileName = externalFileResolver.extractFileName(newUri)
                    val extension = fileName?.substringAfterLast(".", "")?.lowercase()
                        ?: book.fileType.lowercase()

                    // ① fileType 一致性校验(扩展名不一致 → 视为选错文件)
                    if (extension.isNotEmpty() && extension != book.fileType.lowercase()) {
                        _relocateMismatch.value = RelocateMismatchState(bookId, newUri, book.title)
                        return@withContext
                    }

                    // ② contentHash 比对(主路径;仅当原书有 hash 时校验)
                    val storedHash = book.contentHash
                    if (!storedHash.isNullOrEmpty()) {
                        val hashResult = contentHashCalculator.computeHash(newUri)
                        val newHash = (hashResult as? ContentHashCalculator.EnsureHashResult.Ok)?.hash
                        if (newHash != null && newHash != storedHash) {
                            _relocateMismatch.value = RelocateMismatchState(bookId, newUri, book.title)
                            return@withContext
                        }
                    }
                    // contentHash 为空(极老书)→ 跳过校验直接放行

                    // ③ 解析新文件路径 + source
                    val resolution = externalFileResolver.resolve(newUri)
                    val resolved = resolveFilePath(resolution, newUri, fileName, extension)
                        ?: throw IllegalStateException("resolveFilePath returned null")
                    val (filePath, source) = resolved

                    // ④ 二次可访问性校验(防止 resolveFilePath 复制后文件又被删的极端竞态)
                    if (FileAccessValidator.check(context, filePath, source) != FileAccessValidator.Result.ACCESSIBLE) {
                        throw IllegalStateException("relocated file not accessible: $filePath")
                    }

                    // ⑤ 更新 DB(filePath + source + documentId 一并写,@Update 全字段)
                    val newDocId = extractDocumentIdSafely(newUri)
                    booksRepository.updateBook(
                        book.copy(
                            filePath = filePath,
                            source = source,
                            documentId = newDocId ?: book.documentId,
                        )
                    )

                    // ⑥ 清理旧位置的文件(best-effort,文件/权限已失效时自动 no-op)
                    cleanupOldFileBeforeRelocate(book)

                    // ⑦ 清状态
                    clearRelocateState()
                    clearFileMissingState()

                    // ⑧ 导航到阅读器(复用 buildReaderRoute,自动适配 EPUB/PDF/Audiobook 路由)
                    val route = buildReaderRoute(
                        bookId = bookId,
                        fileType = book.fileType,
                        filePath = filePath,
                        coverImage = book.coverImage,
                        title = book.title,
                        author = book.author
                    )
                    externalIntentBridge.submitNavigationRoute(route)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("relocateBook failed bookId=$bookId: ${e.message}", e)
                showSnackbar(stringResource(R.string.relocate_failed), indefinite = false)
            } finally {
                _relocateInProgress.value = false
            }
        }
    }

    /** 清除重定位状态(保留 _relocateMismatch,由 dismissRelocateMismatch 单独清)。 */
    fun clearRelocateState() {
        _relocateInProgress.value = false
    }

    /** 关闭「文件不匹配」弹窗,回到「文件不可访问」弹窗(fileMissingBookId 仍非空)。 */
    fun dismissRelocateMismatch() {
        _relocateMismatch.value = null
    }

    /**
     * relocate 成功后清理旧位置的文件(best-effort,所有操作在文件/权限已失效时自动 no-op)。
     * - external_import → 删除内部存储的副本
     * - opds → 删除 OPDS 下载文件
     * - external / import → 释放旧的 SAF 持久化权限
     * - scan → 无需清理(文件在用户文件系统,已被移动/改名)
     */
    private suspend fun cleanupOldFileBeforeRelocate(book: Book) {
        when (book.source) {
            SOURCE_EXTERNAL_IMPORT -> cleanupImportedFile(book.filePath)
            "opds" -> {
                try {
                    val uri = Uri.parse(book.filePath)
                    if (uri.scheme == "file") {
                        val file = java.io.File(uri.path ?: return)
                        if (file.exists()) file.delete()
                    }
                } catch (_: Exception) {
                }
            }
            SOURCE_EXTERNAL, "import" -> {
                if (book.filePath.startsWith("content://")) {
                    try {
                        permissionRepository.releasePersistableUriPermission(Uri.parse(book.filePath))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun reimportBookWithNewUri(bookId: Long, newUri: Uri) {
        // ★ P1-1:防重入守卫,防止 launcher 重复回调导致并发写入
        if (_reselectInProgress.value) {
            Logger.w("reimportBookWithNewUri: already in progress, ignore")
            return
        }
        _reselectInProgress.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val orphan = booksRepository.getBookById(bookId)
                        ?: throw IllegalStateException("book not found: $bookId")

                    // ── P1-1:校验所选文件是否与 orphan 是同一本书 ──
                    val fileName = externalFileResolver.extractFileName(newUri)
                    val extension = fileName?.substringAfterLast(".", "")?.lowercase()
                        ?: orphan.fileType.lowercase()

                    // 1. fileType 一致性校验(扩展名不一致 → 视为选错文件)
                    if (extension.isNotEmpty() && extension != orphan.fileType.lowercase()) {
                        _orphanMismatch.value = OrphanMismatchState(bookId, newUri, orphan.title)
                        return@withContext
                    }

                    // 2. contentHash 校验(仅当 orphan 有 hash 时,极旧备份 orphan 无 hash 跳过校验)
                    val orphanHash = orphan.contentHash
                    if (!orphanHash.isNullOrEmpty()) {
                        val hashResult = contentHashCalculator.computeHash(newUri)
                        val newHash = (hashResult as? ContentHashCalculator.EnsureHashResult.Ok)?.hash
                        if (newHash != null && newHash != orphanHash) {
                            _orphanMismatch.value = OrphanMismatchState(bookId, newUri, orphan.title)
                            return@withContext
                        }
                    }

                    // ── 校验通过 → 走原提升路径 ──
                    val resolution = externalFileResolver.resolve(newUri)
                    val resolved = resolveFilePath(resolution, newUri, fileName, extension)
                        ?: throw IllegalStateException("resolveFilePath returned null")
                    val (filePath, source) = resolved

                    if (orphan.source == SOURCE_EXTERNAL_IMPORT && orphan.filePath != filePath) {
                        cleanupImportedFile(orphan.filePath)
                    }

                    booksRepository.updateBook(
                        orphan.copy(
                            filePath = filePath,
                            source = source
                        )
                    )

                    val route = buildReaderRoute(
                        bookId = bookId,
                        fileType = orphan.fileType,
                        filePath = filePath,
                        coverImage = orphan.coverImage,
                        title = orphan.title,
                        author = orphan.author
                    )
                    externalIntentBridge.submitNavigationRoute(route)
                }
                // 成功:关闭弹窗 + 清状态(clearReselectBookId 已含 _reselectInProgress=false)
                clearReselectBookId()
            } catch (e: Exception) {
                // ★ 协程取消必须重抛,不能当失败处理(与项目既有范式一致:见行 624/676)
                if (e is CancellationException) throw e
                // 失败:snackbar 提示 + 保持弹窗(_reselectBookId 仍 set,用户可重试或取消)
                showSnackbar(stringResource(R.string.orphan_import_failed), indefinite = false)
                Logger.w("reimportBookWithNewUri failed bookId=$bookId: ${e.message}")
            } finally {
                _reselectInProgress.value = false
            }
        }
    }

    /**
     * ★ P1-1:用户在"文件不匹配"弹窗点"作为新书导入"。
     * 用 fileParser 解析新文件 → insertBookUseCase 走完整导入路径(含去重)。
     */
    fun confirmImportAsNewBook() {
        val mismatch = _orphanMismatch.value ?: return
        _orphanMismatch.value = null
        _reselectInProgress.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val uri = mismatch.newUri
                    val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        ?: throw IllegalStateException("cannot create DocumentFile from uri")

                    val parsedBook = withTimeoutOrNull(60_000) {
                        fileParser.parse(documentFile)
                    } ?: throw IllegalStateException("file parse timeout or null")

                    val fileName = externalFileResolver.extractFileName(uri)
                    val extension = fileName?.substringAfterLast(".", "")?.lowercase()
                        ?: parsedBook.fileType.lowercase()
                    val resolution = externalFileResolver.resolve(uri)
                    val resolved = resolveFilePath(resolution, uri, fileName, extension)
                        ?: throw IllegalStateException("resolveFilePath returned null")
                    val (filePath, source) = resolved

                    val bookToInsert = parsedBook.copy(
                        filePath = filePath,
                        source = source,
                        documentId = null,
                    )
                    val newBookId = insertBookUseCase(bookToInsert)
                    val route = buildReaderRoute(
                        bookId = newBookId,
                        fileType = bookToInsert.fileType,
                        filePath = filePath,
                        coverImage = bookToInsert.coverImage,
                        title = bookToInsert.title,
                        author = bookToInsert.author
                    )
                    externalIntentBridge.submitNavigationRoute(route)
                }
                clearReselectBookId()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showSnackbar(stringResource(R.string.orphan_import_failed), indefinite = false)
                Logger.w("confirmImportAsNewBook failed: ${e.message}")
            } finally {
                _reselectInProgress.value = false
            }
        }
    }

    /** ★ P1-1:用户在"文件不匹配"弹窗点"重新选择",回到文件选择器。 */
    fun dismissOrphanMismatch() {
        _orphanMismatch.value = null
    }

    companion object {
        const val SOURCE_EXTERNAL = "external"
        const val SOURCE_EXTERNAL_IMPORT = "external_import"
        const val SOURCE_SYNC_ORPHAN = "sync_orphan"
    }

    /**
     * ★ P1-1:orphan 文件不匹配弹窗状态。
     * @param orphanBookId 对应的 orphan 书 id
     * @param newUri 用户选择的文件 Uri
     * @param orphanTitle orphan 书标题(弹窗显示用)
     */
    data class OrphanMismatchState(
        val orphanBookId: Long,
        val newUri: Uri,
        val orphanTitle: String,
    )

    /**
     * 重定位时文件不匹配弹窗状态。
     * @param bookId 待重定位的书 id
     * @param uri 用户刚选的、不匹配的 URI(用于点"重新选择"时重新拉起 picker)
     * @param bookTitle 书标题(弹窗显示用)
     */
    data class RelocateMismatchState(
        val bookId: Long,
        val uri: Uri,
        val bookTitle: String,
    )
}

sealed class ExternalFileResult {
    data class NavigateToReader(
        val bookId: Long,
        val fileType: String,
        val filePath: String,
        val coverImage: String?,
        val title: String?,
        val author: String?
    ) : ExternalFileResult()

    data class Error(val message: String) : ExternalFileResult()
}
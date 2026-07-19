package com.wxn.reader.presentation.mainReader

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Stable
import androidx.core.graphics.toRect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.spreada.utils.chinese.ZHConverter
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.Bookmark
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TTSEngineType
import com.wxn.base.bean.TtsConfig
import com.wxn.base.bean.TtsPlaybackStatus
import com.wxn.base.exception.NotTextFileException
import com.wxn.base.ext.toStringColor
import com.wxn.base.ext.sysIsDarkMode
import com.wxn.base.util.BrightnessHelper
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.launchIO
import com.wxn.base.util.launchMain
import com.wxn.bookparser.BookParserEngine
import com.wxn.bookparser.TextParser
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.model.preference.TranslatorPreferences
import com.wxn.bookread.data.model.preference.TtsPreferences
import com.wxn.bookread.data.model.preference.applyTo
import com.wxn.reader.data.dto.toReaderPreferences
import com.wxn.reader.data.dto.toReaderThemeConfigEntity
import com.wxn.reader.data.dto.differsFrom
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.data.source.local.TranslatorPrefsUtil
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.BookApplication
import com.wxn.reader.R
import com.wxn.reader.data.dto.PerBookMetaEntity
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.DictionaryPreferences
import com.wxn.reader.data.model.TranslatorItem
import com.wxn.reader.data.model.WordResult
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.data.source.local.BatteryOptimazePrefsUtil
import com.wxn.reader.data.source.local.DictionaryPrefsUtil
import com.wxn.reader.data.source.local.GuidePrefUtil
import com.wxn.reader.domain.model.AnnotationType
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.LinkedContent
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.domain.repository.TTSModelsRepository
import com.wxn.reader.util.toAndroidBitmap
import com.wxn.reader.domain.use_case.annotations.AddAnnotationUseCase
import com.wxn.reader.domain.use_case.annotations.DeleteAnnotationUseCase
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.annotations.UpdateAnnotationUseCase
import com.wxn.reader.domain.use_case.bookmarks.AddBookmarkUseCase
import com.wxn.reader.domain.use_case.bookmarks.DeleteBookmarkUseCase
import com.wxn.reader.domain.use_case.bookmarks.GetBookmarksForBookUseCase
import com.wxn.reader.domain.use_case.books.GetBookByIdUseCase
import com.wxn.reader.domain.use_case.books.IncrementReadingTimeUseCase
import com.wxn.reader.domain.use_case.books.DeleteBookUseCase
import com.wxn.reader.domain.use_case.books.UpdateEndReadingDateAndStatusUseCase
import com.wxn.reader.domain.use_case.books.UpdateReadingStatusUseCase
import com.wxn.reader.domain.use_case.books.UpdateStartReadingDateUseCase
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.ChaptersIndexValidator
import com.wxn.reader.domain.use_case.chapters.GetChaptersByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.InsertChaptersUserCase
import com.wxn.reader.domain.use_case.chapters.ReplaceChaptersByBookIdUseCase
import com.wxn.reader.domain.use_case.font.FontListItem
import com.wxn.reader.domain.use_case.font.GetFontsUseCase
import com.wxn.reader.domain.use_case.font.ImportFontUseCase
import com.wxn.reader.domain.use_case.search.SearchInBookUseCase
import com.wxn.reader.domain.use_case.search.SearchResultItem
import com.wxn.reader.domain.use_case.search.SearchInBookUseCase.SearchProgress
import com.wxn.reader.domain.repository.TranslateRepository
import com.wxn.reader.domain.repository.DictionaryRepository
import com.wxn.reader.domain.repository.VocabularyRepository
import com.wxn.reader.data.remote.api.Constants
import com.wxn.reader.data.remote.dto.SupportedLanguage
import com.wxn.reader.domain.use_case.notes.AddNoteUseCase
import com.wxn.reader.domain.use_case.notes.DeleteNoteUseCase
import com.wxn.reader.domain.use_case.notes.GetNotesForBookUseCase
import com.wxn.reader.domain.use_case.notes.UpdateNoteUseCase
import com.wxn.reader.domain.use_case.reading_activity.IncrementReadingActivityTimeUseCase
import com.wxn.reader.events.VolumeEventBus
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.presentation.bookReader.components.modals.ReadUiEditType
import com.wxn.reader.presentation.bookReader.util.TtsPlayerPanelStatus
import com.wxn.reader.presentation.mainReader.helpers.JumpHelper
import com.wxn.reader.service.TtsEngineStatus
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.FileAccessValidator
import com.wxn.reader.util.BatteryOptimizationHelper
import com.wxn.reader.util.ImageUtils
import com.wxn.reader.util.LanguageDetector
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil
import com.wxn.reader.util.DictionaryHelper
import com.wxn.reader.util.TranslatorHelper
import com.wxn.reader.util.tts.TtsNavigator
import com.wxn.reader.util.tts.data.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.concurrent.Volatile
import kotlin.random.Random
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.wxn.bookread.ext.BitmapExt
import com.wxn.reader.data.source.local.QuoteCardPreferencesUtil
import com.wxn.reader.data.source.local.dao.PerBookMetaDao
import com.wxn.reader.data.source.local.dao.PerBookThemeOverrideDao
import com.wxn.reader.data.source.local.dao.ReaderThemeConfigDao
import com.wxn.reader.data.repository.PerBookConfigRepository
import com.wxn.bookread.data.model.preference.ReaderThemeMode
import com.wxn.reader.ui.theme.ReaderThemePresets

enum class SearchSheetState { HIDDEN, EXPANDED, MINIMIZED }

@HiltViewModel
@Stable
class MainReadViewModel @Inject constructor(
    val context: Application,

    private val appPrefsUtil: AppPreferencesUtil,
    private val readerPrefsUtil: ReaderPreferencesUtil,
    private val readerTipPrefsUtil: ReadTipPreferencesUtil,
    private val batteryOptimazePrefsUtil: BatteryOptimazePrefsUtil,
    private val guidePrefUtil: GuidePrefUtil,
    private val translatorPrefsUtil: TranslatorPrefsUtil,
    private val dictionaryPrefsUtil: DictionaryPrefsUtil,

    private val getBookByIdUseCase: GetBookByIdUseCase,

    // 选择性更新 UseCase
    private val updateReadingStatusUseCase: UpdateReadingStatusUseCase,
    private val updateStartReadingDateUseCase: UpdateStartReadingDateUseCase,
    private val updateEndReadingDateAndStatusUseCase: UpdateEndReadingDateAndStatusUseCase,

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
    private val deleteBookmarkUseCase: DeleteBookmarkUseCase,

    private val getChaptersByBookIdUserCase: GetChaptersByBookIdUserCase,
    private val insertChaptersUserCase: InsertChaptersUserCase,
    private val replaceChaptersByBookIdUseCase: ReplaceChaptersByBookIdUseCase,
    private val incrementReadingTimeUseCase: IncrementReadingTimeUseCase,
    private val incrementReadingActivityTimeUseCase: IncrementReadingActivityTimeUseCase,

    private val getFontsUseCase: GetFontsUseCase,
    private val importFontUseCase: ImportFontUseCase,

    private val textParser: TextParser,
    val pageController: PageViewController,


    val ttsPreferencesUtil: TtsPreferencesUtil,
    val ttsModelsRepository: TTSModelsRepository,

    private val translateRepository: TranslateRepository,

    private val dictionaryRepository: DictionaryRepository,

    private val vocabularyRepository: VocabularyRepository,

    private val searchInBookUseCase: SearchInBookUseCase,

    private val deleteBookUseCase: DeleteBookUseCase,

    private val quoteCardPrefsUtil: QuoteCardPreferencesUtil,

    private val readerThemeConfigDao: ReaderThemeConfigDao,

    // ★ v11 per-book 阅读配置(见设计方案 §三)
    private val perBookMetaDao: PerBookMetaDao,
    private val perBookThemeOverrideDao: PerBookThemeOverrideDao,
    private val perBookConfigRepo: PerBookConfigRepository,

    private val reviewPromptManager: com.wxn.reader.domain.ReviewPromptManager,

    // ★ v12 TXT 统一字节偏移方案（plan-txt-unify-byte-offset.md §3.3.4.2 / §3.5.1）：
    // 用于在 replaceChaptersByBookIdUseCase 后回填 BookEntity.txtCharset。
    private val txtBookMetaStore: com.wxn.bookparser.parser.txt.TxtBookMetaStore,

    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(context), PageViewController.OnClickListener {

    //给连续垂直滚动模式用的
    val pageProvider : ContinuousPageProvider = ContinuousPageProvider(pageController)

    /** E1：进程内去重，保证同一本书在一次进程生命周期内只通知一次"读完"（两条 FINISHED 路径共享）。 */
    private val notifiedBookIds = java.util.Collections.synchronizedSet(HashSet<Long>())

    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    private val _readerPreferences =
        MutableStateFlow<ReaderPreferences>(ReaderPreferencesUtil.defaultPreferences)
    val readerPreferences: StateFlow<ReaderPreferences> = _readerPreferences.asStateFlow()

    private val _translatorPrefs = MutableStateFlow<TranslatorPreferences>(TranslatorPrefsUtil.defaultPreferences)
    val translatorPrefs: StateFlow<TranslatorPreferences> = _translatorPrefs.asStateFlow()

    private val _downloadedFonts = MutableStateFlow<List<FontListItem>>(emptyList())
    val downloadedFonts: StateFlow<List<FontListItem>> = _downloadedFonts.asStateFlow()

    private val _uiState = MutableStateFlow<BookReaderUiState>(BookReaderUiState.Loading)
    val uiState: StateFlow<BookReaderUiState> = _uiState.asStateFlow()

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /**
     * 入口预传的封面，仅供 MainReadScreen 的 BookCover 显示使用。
     * 来源：路由参数 coverImage（本地路径 / content:// / 远程 URL）。
     *
     * 注意：书摘卡片预加载（preloadCoverBitmap）不消费此字段，
     * 它读取的是 _book.value?.coverImage（DB 中解析出的本地封面）。
     * 两者职责分离，不可混用。
     */
    private val _displayCover = MutableStateFlow<String?>(null)
    val displayCover: StateFlow<String?> = _displayCover.asStateFlow()
    private val _displayTitle = MutableStateFlow<String?>(null)
    val displayTitle: StateFlow<String?> = _displayTitle.asStateFlow()

    private val _displayAuthor = MutableStateFlow<String?>(null)
    val displayAuthor: StateFlow<String?> = _displayAuthor.asStateFlow()


    private val _currentBookId = MutableStateFlow<Long?>(null)
    val currentBookId: StateFlow<Long?> = _currentBookId.asStateFlow()

    // ===== ★ v11 per-book 阅读配置（见设计方案 §三.1）=====
    /**
     * 单一 meta 真相源（R2 ❸）：effective 流与 [_perBookMeta] 缓存共享同一上游 Flow。
     * 冷流 → shareIn 转热流，replay=1 让后订阅者立刻拿到当前值。
     * 避免两个独立订阅 emit 顺序不保证导致开关切换瞬间 UI 与生效态闪烁。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val perBookMetaFlow: Flow<PerBookMetaEntity?> = currentBookId
        .filterNotNull()
        .flatMapLatest { perBookMetaDao.observeByBookId(it) }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** 缓存：从同一 [perBookMetaFlow] 喂给 StateFlow，供 isPerBookMode / perBookThemeId 同步读取。 */
    private val _perBookMeta = MutableStateFlow<PerBookMetaEntity?>(null)
    private val isPerBookMode: Boolean get() = _perBookMeta.value?.overrideEnabled == true
    private val perBookThemeId: String? get() = _perBookMeta.value?.selectedThemeId

    /** UI 窄接口（R2 ❽）：不暴露 PerBookMetaEntity，仅暴露开关布尔态。 */
    val isPerBookEnabled: StateFlow<Boolean> = _perBookMeta
        .map { it?.overrideEnabled == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 首次 OFF→ON 提示（仿 showSearchFabGuide）。null=不显示，true=显示提示。 */
    private val _showPerBookTip = MutableStateFlow<Boolean?>(null)
    val showPerBookTip: StateFlow<Boolean?> = _showPerBookTip.asStateFlow()

    /**
     * effective 流（v12 全量快照模式）：
     * - currentBookId==null（进程恢复期）或 overrideEnabled=false 或 meta==null → 纯全局 DataStore。
     * - overrideEnabled=true + selectedThemeId 非空 →
     *     snapshot[book,theme] 存在 → 用快照的 17 主题字段覆盖 global，保留非主题字段；
     *     snapshot 不存在         → 用该主题 preset 兜底（applyTo），或退回 global。
     *
     * **冻结范围**（P-CORE-1）：17 个主题字段（视觉+排版+对齐）来自 per-book 快照——全局改这些字段
     * **不影响**已开启 per-book 的书（独立冻结）。非主题字段（brightness/keepScreenOn/colorHistory/翻页等）
     * 仍从 global 取，全局改这些字段 per-book 书**跟随**（这些字段不属于主题范畴）。
     *
     * **v12 vs v11**：删除了同主题/异主题基线分支 + overrideWith delta 合并。
     * snapshot 本身已是全量，无需再叠加 delta。preset 兜底分支处理"切到无快照的新主题"场景。
     *
     * **冷启动时序**（R2 ❼）：currentBookId 为 null 时直接走全局 readerPrefsFlow（与原行为一致，
     * 保证冷启动即渲染默认 prefs）；bookId 就绪后由 meta 驱动重算。
     *
     * **渲染层注入**：effective 流的最终值由 [applyReaderPreferences] 推入 `_effectiveOverride`，
     * 渲染层（ChapterProvider 等）通过 `readerPrefsFlow` 透明读取。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val effectiveReaderPrefsFlow: Flow<ReaderPreferences> = currentBookId
        .flatMapLatest { bookId ->
            if (bookId == null) {
                // 进程恢复期：无 bookId，直接全局（保持原 init collector 的即时代入行为）
                readerPrefsUtil.rawReaderPrefsFlow
            } else {
                // bookId 就绪：直接订阅 meta（不依赖 shareIn 的 perBookMetaFlow，避免 hot flow 时序问题）
                perBookMetaDao.observeByBookId(bookId).distinctUntilChanged().flatMapLatest { meta ->
                    if (meta?.overrideEnabled == true && meta.selectedThemeId != null) {
                        val themeId = meta.selectedThemeId
                        combine(
                            readerPrefsUtil.rawReaderPrefsFlow,
                            perBookThemeOverrideDao.observeByBookIdAndTheme(bookId, themeId).distinctUntilChanged()
                        ) { global, snapshot ->

                            val base = if (snapshot != null) {
                                snapshot.toReaderPreferences(global)
                            } else {
                                ReaderThemePresets.getPresetById(themeId)?.applyTo(global) ?: global
                            }
                            val mode = meta.readerThemeMode?.let {
                                runCatching {
                                    ReaderThemeMode.valueOf(it)
                                }.getOrNull()
                            }
                            if (mode != null) base.copy(readerThemeMode = mode) else base
                        }
                    } else {
                        readerPrefsUtil.rawReaderPrefsFlow
                    }
                }
            }
        }

    /**
     * ★ v12 修复：per-book 模式下 saveSnapshot 的基准 baseline。
     *
     * **Bug 根因**：原 14 处写入分支用 `rawReaderPrefsFlow`（全局值）作基准，但 saveSnapshot 是整行 REPLACE，
     * 全局基准会把 per-book 此前独立冻结的其余 16 字段冲刷成全局值（例如改对齐会重置字体、改字色会重置背景）。
     *
     * **正确基准** = 当前 per-book effective 偏好：
     *   - snapshot 存在 → 读 snapshot（保留 per-book 已冻结的全部 17 字段）
     *   - snapshot 不存在 → preset 兜底（applyTo(global)，保留非主题字段）
     * 与 [effectiveReaderPrefsFlow] 的算式完全一致，保证"读-改-写"基准 = 渲染层实际看到的值。
     *
     * **必须 suspend**：snapshot 来自 Room 查表；global 来自 DataStore。
     * **仅 per-book 分支调用**：调用方已保证 `isPerBookMode && themeId != null`。
     */
    private suspend fun currentEffectivePrefs(bookId: Long, themeId: String): ReaderPreferences {
        val global = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()
            ?: ReaderPreferencesUtil.defaultPreferences  // 极端：DataStore 尚未就绪，退回默认（与 effective 流兜底一致）
        val snapshot = perBookThemeOverrideDao.getByBookIdAndTheme(bookId, themeId)
        return if (snapshot != null) snapshot.toReaderPreferences(global)
        else com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(themeId)?.applyTo(global) ?: global
    }

    /**
     * ★ per-book 写操作统一包装（P-CRASH-2）：
     * 14 个 per-book 写函数共用此包装，捕获 Room/DataStore 异常，失败时记录日志 + Toast，
     * 避免异常传播到 CoroutineExceptionHandler 导致崩溃。
     *
     * 绑定 [viewModelScope]（VM 生命周期），保持 Main.immediate dispatcher（UI 相关），
     * 不复用 base/util/Coroutines.kt 的 launchIO/launchMain（那些用 application scope 且无 Toast）。
     */
    private fun launchPerBookWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::perBookWrite failed: ${e.message}", e)
                ToastUtil.show(R.string.theme_update_failed)
            }
        }
    }

    private var pageControllerOwnerToken: Long = 0L

    //显示总的菜单弹窗
    private val _showMenu = MutableStateFlow<Boolean>(false)
    val showMenu: StateFlow<Boolean> = _showMenu.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow<Boolean>(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _isShowTextToolbar = MutableStateFlow<Boolean>(false)
    private val _selectionVisibleOnPage = MutableStateFlow<Boolean>(false)
    val showTextToolbar: StateFlow<Boolean> = combine(
        _isShowTextToolbar,
        _selectionVisibleOnPage
    ) { isOpen, isVisible -> isOpen && isVisible }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false
    )

    /** TextToolbar 触摸激活标志：防止工具栏上的触摸事件穿透到阅读区 pointerInput -- 针对滚动模式 */
    private val _isToolbarTouchActive = MutableStateFlow(false)
    val isToolbarTouchActive: StateFlow<Boolean> = _isToolbarTouchActive.asStateFlow()

    fun setToolbarTouchActive(active: Boolean) {
        _isToolbarTouchActive.value = active
    }
    private val _textToolbarRect = MutableStateFlow<Rect>(Rect(0, 0, 0, 0))
    val textToolbarRect: StateFlow<Rect> = _textToolbarRect.asStateFlow()

    private val _isShowColorSelectionPanel = MutableStateFlow<Boolean>(false)
    val showColorSelectionPanel: StateFlow<Boolean> = _isShowColorSelectionPanel.asStateFlow()

    private val _showReadBgList = MutableStateFlow<Boolean>(false)
    val showReadBgList: StateFlow<Boolean> = _showReadBgList.asStateFlow()

    private val _showProgressBar = MutableStateFlow(false)
    val showProgressBar: StateFlow<Boolean> = _showProgressBar.asStateFlow()

    private val _showReaderSettings = MutableStateFlow<Boolean>(false)
    val showReaderSettings: StateFlow<Boolean> = _showReaderSettings.asStateFlow()

    private val _showReaderUISettings = MutableStateFlow<Boolean>(false)
    val showReaderUISettings: StateFlow<Boolean> = _showReaderUISettings.asStateFlow()

    //
    private val _showNoteDialog = MutableStateFlow<Boolean>(false)
    val showNoteDialog: StateFlow<Boolean> = _showNoteDialog.asStateFlow()

    private val _noteDialogSelectedText = MutableStateFlow<String>("")
    val noteDialogSelectedText: StateFlow<String> = _noteDialogSelectedText.asStateFlow()

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

    //高亮列表
    private val _highlights = MutableStateFlow<List<BookAnnotation>>(emptyList())
    val highlights: StateFlow<List<BookAnnotation>> = _highlights.asStateFlow()
    //下划线列表
    private val _underlines = MutableStateFlow<List<BookAnnotation>>(emptyList())
    val underlines: StateFlow<List<BookAnnotation>> = _underlines.asStateFlow()

    //选中的注释
    private val _selectedAnnotation = MutableStateFlow<BookAnnotation?>(null)
    val selectedAnnotation: StateFlow<BookAnnotation?> = _selectedAnnotation.asStateFlow()

    var checkedAnnotations: ArrayList<BookAnnotation> = arrayListOf()

    private val _clickedLinkContent = MutableStateFlow<LinkedContent?>(null)
    val clickedLinkContent: StateFlow<LinkedContent?> = _clickedLinkContent.asStateFlow()

    private val _isBookmarked = MutableStateFlow<Boolean>(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    // ==================== 书摘分享卡片状态（扩展，不新建独立 VM） ====================
    private val _quoteCardState = MutableStateFlow(
        com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardUiState()
    )
    val quoteCardState: StateFlow<com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardUiState> =
        _quoteCardState.asStateFlow()

    /** 可取消的渲染任务（防切样式/ dismiss 时的竞态） */
    private var renderJob: kotlinx.coroutines.Job? = null

    /**
     * 一次性快照当前选中文本 + 书籍信息 → [QuoteCardData]。
     *
     * 顺序：先快照（防御性，对齐 showTranslatePanel 范式），再由调用方关闭 toolbar。
     * 后续不响应 book 变化（数据快照，防 U-20 竞态）。
     */
    fun snapshotQuoteData() {
        val currentBook = _book.value
        val rawText = selectedLocator?.text.orEmpty()
        if (rawText.isBlank()) {
            _quoteCardState.value = _quoteCardState.value.copy(
                phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.ERR_DATA,
                errorCode = com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.DATA_MISSING
            )
            return
        }
        val chapterIdx = _curChapterIndex.value
        val chapterName = _curChapterName.value
        val denoised = com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardTextProcessor
            .denoise(rawText, chapterName)
        val data = com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData(
            rawQuoteText = rawText,
            defaultEditableText = denoised,
            bookTitle = currentBook?.title ?: "",
            bookAuthor = currentBook?.author,
            chapterName = chapterName.takeIf { it.isNotBlank() },
            chapterIndex = chapterIdx,
            coverPath = currentBook?.coverImage,
            readingProgress = (_readProgression.value * 100.0).toFloat(),
            bookFileType = currentBook?.fileType ?: "",
            createdAt = System.currentTimeMillis()
        )
        // 快照后立即清理选区，防 Surface 叠加后选区手柄穿透（S3）
        selectedLocator = null
        textToolbarOpen(false)
        cancelTextSelected()
        // 恢复持久化 Config + 写入快照数据
        viewModelScope.launch {
            val savedConfig = quoteCardPrefsUtil.configFlow.firstOrNull()
                ?: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardConfig()
            _quoteCardState.value = _quoteCardState.value.copy(
                data = data,
                config = savedConfig,
                coverBitmap = null,
                phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DATA_PREPARE,
                errorCode = null
            )
            // 异步预加载封面 Bitmap（Coil，软件位图）
            preloadCoverBitmap(data.coverPath)
            _quoteCardState.value = _quoteCardState.value.copy(
                phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN
            )
        }
    }

    /** 异步预加载封面为软件位图 ImageBitmap（BitmapFactory 天然返回软件位图，无 HwBitmap 问题） */
    private suspend fun preloadCoverBitmap(coverPath: String?) {
        if (coverPath.isNullOrBlank()) return
        runCatching {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val bitmap = BitmapExt.loadCoverBitmap(context, coverPath)
                if (bitmap != null) {
                    _quoteCardState.value = _quoteCardState.value.copy(
                        coverBitmap = bitmap.asImageBitmap()
                    )
                }
            }
        }.onFailure {
            Logger.w("MainReadViewModel::preloadCoverBitmap failed: $it")
            _quoteCardState.value = _quoteCardState.value.copy(
                errorCode = com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.COVER_LOAD_FAIL
            )
        }
    }

    fun updateQuoteCardConfig(config: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardConfig) {
        _quoteCardState.value = _quoteCardState.value.copy(config = config)
        viewModelScope.launch {
            quoteCardPrefsUtil.saveConfig(config)
        }
    }

    fun setQuoteCardPhase(phase: com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase) {
        _quoteCardState.value = _quoteCardState.value.copy(phase = phase)
    }

    fun setQuoteCardError(errorCode: com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode?) {
        _quoteCardState.value = _quoteCardState.value.copy(
            errorCode = errorCode,
            phase = if (errorCode != null && errorCode.isSevere) {
                when (errorCode) {
                    com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_OOM,
                    com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_TIMEOUT ->
                        com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.ERR_RENDER
                    else -> _quoteCardState.value.phase
                }
            } else _quoteCardState.value.phase
        )
    }

    fun setRenderJob(job: kotlinx.coroutines.Job?) {
        renderJob = job
    }

    /**
     * 渲染卡片并分享（系统 chooser）。
     * 守卫：RENDERING 中 → Snackbar；文本空 → 错误；文本过短 → 错误。
     */
    fun renderAndShare() {
        val state = _quoteCardState.value
        if (state.phase == com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.RENDERING) return
        val data = state.data ?: return
        val quoteText = data.defaultEditableText
        if (quoteText.isBlank()) {
            setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.QUOTE_EDIT_EMPTY)
            return
        }
        if (com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardTextProcessor.isTooShort(quoteText)) {
            setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.TEXT_TOO_SHORT)
            return
        }
        cancelRenderJob()
        renderJob = viewModelScope.launch {
            _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.RENDERING, errorCode = null)
            delay(50)  // R6 Loading 文字先绘制一帧
            try {
                val width = state.config.ratio.width
                val height = state.config.ratio.height
                val fontFamily = resolveFontFamilyForCard(data)
                val activity = BookApplication.app.topActivity
                if (activity == null) {
                    setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_TIMEOUT)
                    _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
                    return@launch
                }
                val bitmap = com.wxn.reader.util.QuoteCardCapture.capture(
                    context = activity,
                    data = data,
                    editableText = quoteText,
                    config = state.config,
                    coverBitmap = state.coverBitmap,
                    fontFamily = fontFamily,
                    width = width,
                    height = height
                )
                if (bitmap == null) {
                    setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_TIMEOUT)
                    _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
                    return@launch
                }
                val androidBitmap = bitmap.toAndroidBitmap()
                try {
                    val uri = com.wxn.reader.util.ShareQuoteCardUtil.saveBitmapToCacheUri(context, androidBitmap)
                    if (uri == null) {
                        setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_OOM)
                        _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
                        return@launch
                    }
                    val caption = com.wxn.reader.util.ShareQuoteCardUtil.buildShareCaption(data, quoteText)
                    try {
                        com.wxn.reader.util.ShareQuoteCardUtil.shareImage(context, uri, caption, context.getString(com.wxn.reader.R.string.share))
                    } catch (e: android.content.ActivityNotFoundException) {
                        // 真正无可接收分享的 App
                        setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.NO_SHARE_TARGET)
                    } catch (e: Exception) {
                        // 其他异常（FLAG/URI 权限等），log 区分根因，用户面统一提示
                        com.wxn.base.util.Logger.w("MainReadViewModel::renderAndShare shareImage failed: ${e.message}")
                        setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.NO_SHARE_TARGET)
                    }
                    _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.SHARE_CHOOSER)
                } finally {
                    // 渲染临时图写完即可回收（saveBitmapToCacheUri 已拷贝到文件）
                    if (!androidBitmap.isRecycled) androidBitmap.recycle()
                }
            } catch (e: OutOfMemoryError) {
                setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_OOM)
                _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
            } catch (e: Exception) {
                com.wxn.base.util.Logger.w("MainReadViewModel::renderAndShare failed: ${e.message}")
                setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.DATA_MISSING)
                _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
            }
        }
    }

    /**
     * 渲染卡片并保存到系统相册。
     */
    fun renderAndSave() {
        val state = _quoteCardState.value
        if (state.phase == com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.RENDERING) return
        val data = state.data ?: return
        val quoteText = data.defaultEditableText
        if (quoteText.isBlank()) {
            setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.QUOTE_EDIT_EMPTY)
            return
        }
        if (com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardTextProcessor.isTooShort(quoteText)) {
            setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.TEXT_TOO_SHORT)
            return
        }
        cancelRenderJob()
        renderJob = viewModelScope.launch {
            _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.RENDERING, errorCode = null)
            delay(50)
            try {
                val width = state.config.ratio.width
                val height = state.config.ratio.height
                val fontFamily = resolveFontFamilyForCard(data)
                val activity = BookApplication.app.topActivity
                if (activity == null) {
                    setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_TIMEOUT)
                    _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
                    return@launch
                }
                val bitmap = com.wxn.reader.util.QuoteCardCapture.capture(
                    context = activity,
                    data = data,
                    editableText = quoteText,
                    config = state.config,
                    coverBitmap = state.coverBitmap,
                    fontFamily = fontFamily,
                    width = width,
                    height = height
                )
                if (bitmap == null) {
                    setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_TIMEOUT)
                    _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
                    return@launch
                }
                val androidBitmap = bitmap.toAndroidBitmap()
                try {
                    val success = com.wxn.reader.util.ShareQuoteCardUtil.saveToGallery(context, androidBitmap)
                    if (success) {
                        _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.SAVED)
                    } else {
                        setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.GALLERY_IO)
                        _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
                    }
                } finally {
                    // 保存完即可回收临时渲染图
                    if (!androidBitmap.isRecycled) androidBitmap.recycle()
                }
            } catch (e: OutOfMemoryError) {
                setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.RENDER_OOM)
                _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
            } catch (e: Exception) {
                com.wxn.base.util.Logger.w("MainReadViewModel::renderAndSave failed: ${e.message}")
                setQuoteCardError(com.wxn.reader.presentation.shareQuoteCard.model.QuoteErrorCode.GALLERY_IO)
                _quoteCardState.value = _quoteCardState.value.copy(phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN)
            }
        }
    }

    /** 解析当前阅读字体为卡片用的 FontFamily */
    private fun resolveFontFamilyForCard(data: com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData): androidx.compose.ui.text.font.FontFamily? {
        if (data.bookFileType.isEmpty()) return null
        val prefs = _readerPreferences.value
        val typeface = com.wxn.reader.util.ShareQuoteCardUtil.resolveTypeface(
            context, prefs.font, prefs.fontVariant
        )
        return androidx.compose.ui.text.font.FontFamily(typeface)
    }

    fun cancelRenderJob() {
        renderJob?.cancel()
        renderJob = null
    }

    /**
     * Dialog dismiss 时调用：置 null + recycle 旧 coverBitmap。
     * 调用时机由 ReaderView 保证——exit 动画结束 + delay(280ms) 后调用，Composable 已 unmount，
     * 此时 recycle 安全（无 Composable 引用）。
     */
    fun clearQuoteCardBitmaps() {
        cancelRenderJob()
        val oldCover = _quoteCardState.value.coverBitmap
        _quoteCardState.value = _quoteCardState.value.copy(
            coverBitmap = null,
            phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.IDLE
        )
        oldCover?.asAndroidBitmap()?.takeIf { !it.isRecycled }?.recycle()
    }

    /**
     * 分享 chooser 关闭后（onResume）重置 phase，避免按钮永久 disabled（S2）。
     * 仅当停留在 SHARE_CHOOSER 时才重置。
     */
    fun resetPhaseIfSharing() {
        if (_quoteCardState.value.phase == com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.SHARE_CHOOSER) {
            _quoteCardState.value = _quoteCardState.value.copy(
                phase = com.wxn.reader.presentation.shareQuoteCard.model.QuotePhase.DIALOG_OPEN
            )
        }
    }

    // ==================== 书摘分享卡片状态 END ====================

    //翻译面板状态
    private val _showTranslatePanel = MutableStateFlow(false)
    val showTranslatePanel: StateFlow<Boolean> = _showTranslatePanel.asStateFlow()

    private val _translateSelectedText = MutableStateFlow("")
    val translateSelectedText: StateFlow<String> = _translateSelectedText.asStateFlow()

    private val _targetLang = MutableStateFlow("")
    val targetLang: StateFlow<String> = _targetLang.asStateFlow()

    private val _translatedText = MutableStateFlow<String?>(null)
    val translatedText: StateFlow<String?> = _translatedText.asStateFlow()

    private val _supportedLanguages = MutableStateFlow<List<SupportedLanguage>>(emptyList())
    val supportedLanguages: StateFlow<List<SupportedLanguage>> = _supportedLanguages.asStateFlow()



    /***
     * 翻译状态：
     * IDEL状态 0
     * 翻译中  1
     * 翻译成功 2
     * 翻译错误 3
     */
    enum class TranslateStatus{
        IDEL,
        TRANSLATING,
        TRANSLATED,
        ERROR
    }

    private val _translateStatus = MutableStateFlow(TranslateStatus.IDEL)
    val translateStatus: StateFlow<TranslateStatus> = _translateStatus.asStateFlow()

    private var _isLoadingLanguages = false

    private val _showTranslatePicker = MutableStateFlow(false)
    val showTranslatePicker: StateFlow<Boolean> = _showTranslatePicker.asStateFlow()

    private val _translatorItems = MutableStateFlow<List<TranslatorItem>>(emptyList())
    val translatorItems: StateFlow<List<TranslatorItem>> = _translatorItems.asStateFlow()

    enum class DictionaryStatus { IDLE, LOADING, SUCCESS, NOT_FOUND, ERROR }

    data class DictionaryHistoryEntry(
        val word: String,
        val lang: String
    )

    private val _showDictionaryPanel = MutableStateFlow(false)
    val showDictionaryPanel: StateFlow<Boolean> = _showDictionaryPanel.asStateFlow()

    private val _dictionaryResult = MutableStateFlow<WordResult?>(null)
    val dictionaryResult: StateFlow<WordResult?> = _dictionaryResult.asStateFlow()

    private val _dictionaryStatus = MutableStateFlow(DictionaryStatus.IDLE)
    val dictionaryStatus: StateFlow<DictionaryStatus> = _dictionaryStatus.asStateFlow()

    private val _dictionaryPrefs = MutableStateFlow(DictionaryPreferences())
    val dictionaryPrefs: StateFlow<DictionaryPreferences> = _dictionaryPrefs.asStateFlow()
    private val preferredDictLang: String? get() = _dictionaryPrefs.value.lastDictLang.ifBlank { null }

    private val _showDictionaryPicker = MutableStateFlow(false)
    val showDictionaryPicker: StateFlow<Boolean> = _showDictionaryPicker.asStateFlow()

    private val _dictionaryItems = MutableStateFlow<List<TranslatorItem>>(emptyList())
    val dictionaryItems: StateFlow<List<TranslatorItem>> = _dictionaryItems.asStateFlow()


    private val _brightness = MutableStateFlow(0.5f)
    val brightness : StateFlow<Float> = _brightness.asStateFlow()

    private var lastSyncedBrightness: Float = 0.0f
    private var lastSyncedBrightnessSet: Boolean = false
    @Volatile
    private var isBrightnessCommitting: Boolean = false

    // Q-06 防重入：主题切换期间忽略新的切换请求，避免快速连点导致 Room 存档污染。
    @Volatile
    private var isThemeSwitching: Boolean = false

    // * 号标识：被用户微调过的主题 id 集合（字段级判定：值偏离预设才算微调，非"Room 有存档"）。
    //
    // 写者职责分离（避免双写者竞态）：
    // - 【活跃主题】由 readerPrefsFlow 收集器（见 init）实时派生：用户改设置→出现 / 拖回预设→消失 / reset 后消失。
    // - 【非活跃主题】由 [refreshModifiedThemeIds] 用 Room 存档比对预设维护：切换走时 saveCurrent 写入存档，
    //   值等于预设的行会被 differsFrom 过滤。
    // 两条路径各管一半，永不交叉。UI 面板打开/切换/重置后主动调 [refreshModifiedThemeIds] 同步非活跃部分。
    private val _modifiedThemeIds = MutableStateFlow<Set<String>>(emptySet())
    val modifiedThemeIds: StateFlow<Set<String>> = _modifiedThemeIds.asStateFlow()

    private val _dictionaryLang = MutableStateFlow("en")
    val dictionaryLang: StateFlow<String> = _dictionaryLang.asStateFlow()

    private val _dictionaryWord = MutableStateFlow("")
    val dictionaryWord: StateFlow<String> = _dictionaryWord.asStateFlow()

    private var lookupJob: Job? = null
    private val lookupRequestId = AtomicLong(0L)

    private var translateJob: Job? = null
    private val translateRequestId = AtomicLong(0L)

    private var historyList = mutableListOf<DictionaryHistoryEntry>()
    private var historyIndex = -1

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private var currentDayStartTime = 0L

    private var isReadingSessionActive = false
    private var lastLocatorChangeTime = 0L

    private var allChapters = arrayListOf<BookChapter>()

    val _showOutChapters = MutableStateFlow<List<BookChapter>>(emptyList())

    val showOutChapters : StateFlow<List<BookChapter>> = _showOutChapters.asStateFlow()

    private val _readProgression = MutableStateFlow<Double>(0.0)
    val readProgression: StateFlow<Double> = _readProgression.asStateFlow()

    private val _curChapterIndex = MutableStateFlow<Int>(0)
    val curChapterIndex: StateFlow<Int> = _curChapterIndex.asStateFlow()

    private val _curChapterName = MutableStateFlow<String>("")
    val curChapterName: StateFlow<String> = _curChapterName.asStateFlow()

    private val _outHref = MutableStateFlow<String>("")
    val outHref: StateFlow<String> = _outHref.asStateFlow()
    private val _showOutHrefDialog = MutableStateFlow(false)
    val showOutHrefDialog: StateFlow<Boolean> = _showOutHrefDialog.asStateFlow()

    //tts
    //tts  面板控制, 0-关闭,
    // 1-打开显示主播放控制;
    // 2-显示TTS设置;
    // 3-显示语言选择界面;
    // 4-显示引擎切换选择界面;
    // 5-显示模型切换选择界面;
    // 6-显示语音切换选择界面
    private val _ttsPanelStatus = MutableStateFlow(TtsPlayerPanelStatus.PanelClose)
    val ttsPanelStatus: StateFlow<TtsPlayerPanelStatus> = _ttsPanelStatus.asStateFlow()

    //tts
    private val _enableTts = MutableStateFlow(false)
    val enableTts: StateFlow<Boolean> = _enableTts.asStateFlow()

    private val _ttsPlayStatus = MutableStateFlow(TtsPlaybackStatus.IDLE)
    val ttsPlayStatus: StateFlow<TtsPlaybackStatus> = _ttsPlayStatus.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _showTimerExpired = MutableStateFlow(false)
    val showTimerExpired: StateFlow<Boolean> = _showTimerExpired.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsLanguage = MutableStateFlow(LanguageUtil.languageMaps[1])
    val ttsLanguage: StateFlow<LanguageInfo?> = _ttsLanguage.asStateFlow()

    private val _ttsPrefs = MutableStateFlow<TtsPreferences?>(null)
    val ttsPrefs: StateFlow<TtsPreferences?> = _ttsPrefs.asStateFlow()

    private val _ttsPlayTimes = MutableStateFlow(0.0f)
    val ttsPlayTimes: StateFlow<Float> = _ttsPlayTimes.asStateFlow()

    private val _showClickAreaMode = MutableStateFlow(-1)
    val showClickAreaMode: StateFlow<Int> = _showClickAreaMode.asStateFlow()

    private val _leftHandMode = MutableStateFlow(false)
    val leftHandMode: StateFlow<Boolean> = _leftHandMode.asStateFlow()

    // 阅读引导页显示状态
    private val _showReaderGuide = MutableStateFlow(false)
    val showReaderGuide: StateFlow<Boolean> = _showReaderGuide.asStateFlow()

    private val _localTTSModels = MutableStateFlow<List<TTSModelData>>(emptyList())
    val localTTSModels: StateFlow<List<TTSModelData>> = _localTTSModels.asStateFlow()

    private val _currentSpeakers = MutableStateFlow<List<Speaker>>(emptyList())
    val currentSpeakers: StateFlow<List<Speaker>> = _currentSpeakers.asStateFlow()

    private val _showBatteryOptimizationDialog = MutableStateFlow(false)
    val showBatteryOptimizationDialog: StateFlow<Boolean> = _showBatteryOptimizationDialog.asStateFlow()

    private var _editingColorType = MutableStateFlow(ReadUiEditType.ColorType_BACKGROUND)
    val editingType: StateFlow<ReadUiEditType> = _editingColorType.asStateFlow()

    private val _showFontName = MutableStateFlow<String>("")
    val showFontName: StateFlow<String> = _showFontName.asStateFlow()

    private val _searchSheetState = MutableStateFlow(SearchSheetState.HIDDEN)
    val searchSheetState: StateFlow<SearchSheetState> = _searchSheetState.asStateFlow()

    private val _showSearchFabGuide = MutableStateFlow(false)
    val showSearchFabGuide: StateFlow<Boolean> = _showSearchFabGuide.asStateFlow()

    data class NavigationLoadingState(
        val isLoading: Boolean = false,
        val targetChapterIndex: Int = -1,
        val targetChapterName: String = "",
    )

    private val _navigationLoading = MutableStateFlow(NavigationLoadingState())
    val navigationLoading: StateFlow<NavigationLoadingState> = _navigationLoading.asStateFlow()

    private val _continuousScrollLoading = MutableStateFlow(false)
    val continuousScrollLoading: StateFlow<Boolean> = _continuousScrollLoading.asStateFlow()

    private val _searchProgress = MutableStateFlow(SearchProgress())
    val searchProgress: StateFlow<SearchProgress> = _searchProgress.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var searchJob: Job? = null
    private var isNavigatingToResult = false

    private companion object {
        const val LOADING_SHOW_DELAY_MS = 300L
        const val LOADING_MIN_DISPLAY_MS = 500L
        const val LOADING_TIMEOUT_MS = 30000L
    }

    private var loadingShowJob: Job? = null
    private var loadingTimeoutJob: Job? = null
    private var loadingMinDisplayJob: Job? = null
    private var loadingShowTimestamp: Long = 0L
    private var pendingLoadingTargetIndex: Int = -1

    private var _returnLocator = MutableStateFlow<Locator?>(null)
    val returnLocator: StateFlow<Locator?> = _returnLocator.asStateFlow()

    private val _returnChapterName = MutableStateFlow("")
    val returnChapterName: StateFlow<String> = _returnChapterName.asStateFlow()

    private suspend fun fetchBook(bookId: Long): Boolean {
        try {
            val theBook = getBookByIdUseCase(bookId)
            if (theBook != null) {
                _book.value = theBook
                // CR-1 修复：此处不设 LOAD_SUCCESS。章节由 resetBook 回调异步加载，
                // 过早设 LOAD_SUCCESS 会让封面 overlay 消失、暴露尚未就绪的 ReaderView → 空白屏闪现。
                // LOAD_SUCCESS 统一由 resetBook 回调（成功）或此处 else/catch（失败）设置。
                Logger.d("MainReadViewModel:fetchBook: book loaded from DB")
            } else {
                // A2 修复：book 为 null（DB 无记录）→ Error，不返回 true（否则 uiState 永久停在 Loading = 永久 spinner）
                _uiState.value = BookReaderUiState.Error(
                    context.getString(R.string.book_file_not_found)
                )
                Logger.w("MainReadViewModel:fetchBook: book not found in DB, bookId=$bookId")
                return false
            }
            loadAnnotations(bookId)
            loadNotes(bookId)
            loadBookmarks(bookId)

            return true
        } catch (e: Exception) {
            // R2 修订：e.message 只进 Logger，UI 显示友好文案（11 种语言可本地化）
            Logger.e("MainReadViewModel:fetchBook failed: ${e.message}", e)
            _uiState.value = BookReaderUiState.Error(
                context.getString(R.string.book_file_not_found)
            )
        }
        return false
    }

    /**
     * 从书库移除当前书籍（软删除）。返回 true 表示删除成功。
     * 调用方应在协程中通过返回值决定是否导航回首页。
     *
     * ★ 修复：早期错误路径（如 [NotTextFileException]、文件不可访问等）在 [fetchBook] 之前
     * 即 `return@launchIO`，此时 [_book] 尚未填充，原实现 `_book.value?.let{}` 会静默跳过删除，
     * 导致"Remove from library"按下后书籍仍在书库中。此处用 [_currentBookId] 兜底，
     * 它在 [bookload] 入口（L1302）即写入，覆盖所有错误路径。
     */
    suspend fun removeCurrentBook(): Boolean = withContext(Dispatchers.IO) {
        _isDeleting.value = true
        try {
            // 优先用已加载的 _book；为空时（早期错误路径）按 _currentBookId 现取一次。
            val bookToDelete = _book.value
                ?: _currentBookId.value?.let { getBookByIdUseCase(it) }
            if (bookToDelete == null) {
                Logger.w("MainReadViewModel:removeCurrentBook: no book to delete (book and currentBookId both null)")
                false
            } else {
                deleteBookUseCase.invoke(bookToDelete)
                _book.value = null
                true
            }
        } catch (e: Exception) {
            Logger.e("MainReadViewModel:removeCurrentBook failed", e)
            false
        } finally {
            _isDeleting.value = false
        }
    }


    private fun resetCurrentDayStartTime() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        currentDayStartTime = calendar.timeInMillis

    }

    /**
     * 应用 [ReaderPreferences] 到 [_readerPreferences]，保留原 init collector 的全部 6 个副作用（见设计方案 §三.1）。
     *
     * 抽出此函数是为让 effective 流（per-book 开启时合并 delta）驱动 [_readerPreferences]，
     * 而非原来直接收集全局 readerPrefsFlow。副作用清单（丢失任一会致功能退化）：
     * 1. `_readerPreferences.value = pref`
     * 2. `VolumeEventBus.volumeKeyPageTurning`
     * 3. 写 `_showFontName`
     * 4. `BrightnessHelper` 亮度同步
     * 5. **`oldPref != pref` diff → pageController.updateBg()/updatePageViews()`**（唯一重排触发器）
     * 6. 活跃主题 `*` 标记实时维护（per-book 模式下改为按 delta 判定，见下方分支）
     *
     * @param newPref effective 流算出的最终生效偏好（全局或 基线∪delta）
     */
    private fun applyReaderPreferences(newPref: ReaderPreferences) {
        val oldPref = _readerPreferences.value
        _readerPreferences.value = newPref
        // ★ v11 per-book：推入 override 层，让渲染层（ChapterProvider 等）通过 readerPrefsFlow 自动拿到 effective 值
        readerPrefsUtil.setEffectiveOverride(newPref)
        VolumeEventBus.volumeKeyPageTurning = newPref.volumeKeyPageTurning

        val fontName = when {
            newPref.font.isEmpty() -> "sans_serif"
            newPref.font in arrayOf("serif", "sans_serif", "monospace") -> newPref.font
            else -> {
                val dirName = newPref.font.substringAfterLast("/")
                if (dirName.startsWith("imported_")) {
                    dirName.removePrefix("imported_")
                        .substringBeforeLast("_")
                } else {
                    dirName
                }
            }
        }

        _showFontName.value = if (newPref.fontVariant.isEmpty()) {
            fontName
        } else {
            "$fontName/${newPref.fontVariant}"
        }

        if (isBrightnessCommitting) {
            lastSyncedBrightness = newPref.brightness
            lastSyncedBrightnessSet = newPref.brightnessSet
            isBrightnessCommitting = false
        } else if (newPref.brightnessSet != lastSyncedBrightnessSet ||
            (newPref.brightnessSet && newPref.brightness != lastSyncedBrightness)) {
            lastSyncedBrightness = newPref.brightness
            lastSyncedBrightnessSet = newPref.brightnessSet
            if (newPref.brightnessSet) {
                val bv = newPref.brightness.coerceIn(0.0f, 1.0f)
                _brightness.value = bv
                BookApplication.app.topActivity?.let { act ->
                    BrightnessHelper.setWindowBrightness(act, bv)
                }
            } else {
                BookApplication.app.topActivity?.let { act ->
                    BrightnessHelper.restoreSystemBrightness(act)
                    _brightness.value = BrightnessHelper.getSystemBrightnessSliderValue(
                        act.contentResolver, fallback = 0.5f
                    )
                }
            }
        }

        if (oldPref != newPref) {
            // v5 S5：改为正向比较——仅当背景两个字段变更且无其他字段变更时才走 updateBg()。
            // 原逻辑 `oldPref.copy(bg...) == newPref` 在「新增 dualColumn 字段后，仅 dualColumn 变化」时会误判为 true
            // （copy 把 bg 对齐后，旧字段本就相等，dualColumn 差异被 == 吞掉），导致双列切换错误走 updateBg()
            // （只清背景缓存，不重排）→ 显示旧单列 bitmap。正向判断 isOtherChange 明确检测「除背景外是否有其他字段变化」，
            // dualColumn 变化必落入 isOtherChange=true → 走 updatePageViews()（含 upStyle + upBg → clearBitmapCache）。
            val isBgOnlyChange =
                oldPref.backgroundColor != newPref.backgroundColor ||
                oldPref.backgroundImage != newPref.backgroundImage
            val isOtherChange = oldPref.copy(
                backgroundColor = newPref.backgroundColor,
                backgroundImage = newPref.backgroundImage
            ) != newPref

            if (isBgOnlyChange && !isOtherChange) {
                pageController.updateBg()
            } else {
                pageController.updatePageViews()
            }
        }

        // 活跃主题 * 号实时派生（写者职责：active 分支的唯一写者）。
        // v12 统一口径：* = effective prefs 比对预设（differsFrom）。
        // per-book 模式下 newPref 是 effective 值（已加载快照），differsFrom 正确反映"这本书在该主题下是否改过"；
        // 全局模式下 newPref 是全局 DataStore 值，逻辑不变。
        // 注意：differsFrom 排除对齐字段——per-book 改对齐不触发 *，与全局行为一致。
        val activeId = newPref.readerThemeId
        if (activeId != null) {
            val preset = com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(activeId)
            if (preset != null) {
                val activeModified = newPref.toReaderThemeConfigEntity(activeId).differsFrom(preset)
                _modifiedThemeIds.update { set ->
                    if (activeModified) set + activeId else set - activeId
                }
            }
        }
    }

    init {
        val openedBookId = savedStateHandle.get<String>("bookId")?.toLongOrNull()
        val bookUri = savedStateHandle.get<String>("bookUri")
        // Navigation 2.8.7 会自动 URL 解码路径参数，禁止手动 Uri.decode（F4），与 bookUri 处理方式一致
        val openedCoverImage = savedStateHandle.get<String>("coverImage")?.takeUnless { it == "none" }
        val openedTitle = savedStateHandle.get<String>("title")?.takeUnless { it == "none" }
        val openedAuthor = savedStateHandle.get<String>("author")?.takeUnless { it == "none" }
        _displayCover.value = openedCoverImage
        _displayTitle.value = openedTitle
        _displayAuthor.value = openedAuthor

        // 默认选中保障（需求 6 前提）：始终有选中主题。首启 readerThemeId==null → 按模式选默认主题。
        // - LIGHT/ AUTO(系统亮) → default（亮）
        // - DARK / AUTO(系统暗) → amoled_black（暗，与 default 配对）
        // 一次性执行（.first()），不随 Flow emit 重复触发，避免与 switchTheme 写 DataStore 形成递归。
        viewModelScope.launch {
            val first = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launch
            if (first.readerThemeId == null) {
                val effectiveDark = when (first.readerThemeMode) {
                    ReaderThemeMode.LIGHT -> false
                    ReaderThemeMode.DARK -> true
                    ReaderThemeMode.AUTO -> BookApplication.app.sysIsDarkMode()
                }
                val defaultId = if (effectiveDark) com.wxn.reader.ui.theme.ReaderThemePresets.ID_AMOLED_BLACK
                else com.wxn.reader.ui.theme.ReaderThemePresets.ID_DEFAULT
                switchTheme(defaultId)
            }
        }

        viewModelScope.launch {
            // ★ v11 per-book：数据源从全局 readerPrefsFlow 改为 effective 流（per-book 开启时合并 delta）。
            // effective 流在 per-book 关闭时等价于 readerPrefsFlow，保留原有全部行为。
            // 冷启动时序（R2 ❼）：effective 流首启要等 currentBookId emit 非 null + meta 查询返回，
            // 中间窗口 _readerPreferences 保持初始值 defaultPreferences；pageController 此时已初始化。
            effectiveReaderPrefsFlow.collect { pref -> applyReaderPreferences(pref) }
        }

        // ★ v11 per-book：单一 meta 数据源喂缓存（R2 ❸）——effective 流与 _perBookMeta 共享同一 perBookMetaFlow。
        perBookMetaFlow.onEach { _perBookMeta.value = it }.launchIn(viewModelScope)

        // ★ v12 per-book：非活跃主题的 * 标记持续订阅（R2 ❻ + R3 切书清残留）。
        // v12 统一口径：* = 该 (book,theme) 快照 differsFrom(preset)（与活跃主题判定一致）。
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        currentBookId.filterNotNull().flatMapLatest { bookId ->
            // R3：切书时先清 per-book 残留 *（旧书的 nonActive 不应带到新书）
            _modifiedThemeIds.value = emptySet()
            perBookThemeOverrideDao.observeByBookId(bookId)
        }.onEach { allOverrides ->
            if (isPerBookMode) {
                val activeId = perBookThemeId
                val nonActive = allOverrides
                    .filter { e -> e.themeId != activeId }
                    .filter { e ->
                        com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(e.themeId)
                            ?.let { e.toReaderThemeConfigEntity().differsFrom(it) } == true
                    }
                    .map { it.themeId }
                    .toSet()
                _modifiedThemeIds.update { cur -> cur.filter { it == activeId }.toSet() + nonActive }
            }
            // 全局口径下此 collector 不参与（非活跃 * 仍由 refreshModifiedThemeIds 快照维护）
        }.launchIn(viewModelScope)


        viewModelScope.launch {
            translatorPrefsUtil.transilatorPrefsFlow.stateIn(viewModelScope).collect { pref ->
                _translatorPrefs.value = pref
            }
        }
        viewModelScope.launch {
            appPrefsUtil.appPrefsFlow.stateIn(viewModelScope).collect { pref ->
                _appPreferences.value = pref
                Logger.d("MainReadViewModel::init appPreferences[$pref]")
            }
        }

        viewModelScope.launch {
            ttsPreferencesUtil.ttsPreferencesFlow.stateIn(viewModelScope).collect { pref ->
                _ttsPitch.value = pref.pitch
                _ttsSpeed.value = pref.speed
                _ttsLanguage.value = LanguageInfo.fromCode(pref.localeCode)
                _ttsPrefs.value = pref
            }
        }

        viewModelScope.launch {
            dictionaryPrefsUtil.dictionaryPrefsFlow.stateIn(viewModelScope).collect { prefs ->
                _dictionaryPrefs.value = prefs
            }
        }

        viewModelScope.launch {
            getFontsUseCase().collect { list ->
                _downloadedFonts.value = list.filter { it.isDownloaded }
            }
        }

        viewModelScope.launch {
            _showSearchFabGuide.value = !guidePrefUtil.isSearchFabGuideShown()
        }

        ChapterProvider.init(context, readerTipPrefsUtil, readerPrefsUtil)
        resetCurrentDayStartTime()

        viewModelScope.launchIO {
            Logger.d("MainReadViewModel:bookload:init start， OPEN t0 @ ${System.currentTimeMillis()}，openedBookId=$openedBookId")
            val bookId = openedBookId ?: return@launchIO
            _currentBookId.value = bookId
            if (!BookParserEngine.retryLoad()) {
                Logger.w("MainReadViewModel: Native library not available")
                return@launchIO
            }

            Logger.i("MainReadViewModel:bookload:init::bookId=$bookId")
            allChapters.clear()
            val chapters = getChaptersByBookIdUserCase.invoke(bookId).firstOrNull().orEmpty()
            allChapters.addAll(chapters)
            // 说明：旧版本 DB 章节没有 type 字段，Migration_7_8 已用 DEFAULT 0 补齐。
            // type=0 的章节走原始全量解析路径，与升级前行为一致，不会更差。
            // 虚拟切分（type=1）只对"新导入/重新解析的书"生效，采用渐进式策略。
            //
            // 自动失效：当 DB 为空，或章节索引非法（负值/重复/断号 —— 旧 vsplit bug 的
            // 典型特征：54 个虚拟章 chapterIndex 全为 -1）时，先从书文件重新解析，再原子替换。
            // 先解析后删写：解析失败则保留旧数据，绝不丢书。
            //
            // ★ v12 新增 TXT 格式迁移触发（详见 plan-txt-unify-byte-offset.md §3.3.4）：
            // 老版本写的 chapterUrl 是行偏移格式 "startLine:endLine"，本版本统一为字节偏移 "b:startByte:endByte"。
            // 检测到老格式时一次性重扫升级——代价等同重新导入该书（已被接受的操作），后续打开永久 O(1)。
            // 同一本书的 chapterUrl 是原子写入的，格式一致；非 TXT 格式 needsRescanForMigration 默认返回 false。
            if (allChapters.isEmpty() ||
                !ChaptersIndexValidator.isValid(allChapters) ||
                textParser.needsRescanForMigration(allChapters)
            ) {
                Logger.d("MainReaderViewModel:bookload:load all chapters from db failed:${System.currentTimeMillis()}")
                // ★ 二进制守卫：BookHelper.getChapters 对伪装成 .txt 的二进制文件
                // （JPEG/PNG/PDF/ZIP/MOBI 等，见 BinaryMagicNumberDetector）会抛 NotTextFileException。
                // 此处明确捕获并映射到 BookReaderUiState.Error，向用户提示「不是文本文件」，
                // 而非被通用 catch 吞成 emptyList 导致白屏。模式对齐 FileAccessValidator 拒绝路径（L1362-1370）。
                val reparsed = try {
                    BookHelper.getChapters(context, bookId, bookUri, textParser)
                } catch (e: NotTextFileException) {
                    Logger.e("MainReadViewModel:bookload: not a text file: ${e.message}")
                    _uiState.value = BookReaderUiState.Error(
                        context.getString(R.string.txt_not_a_text_file)
                    )
                    return@launchIO
                } catch (e: Exception) {
                    // v2 修复（review §X1）：不再 early return。
                    // 原实现若 allChapters 已空（首次导入失败），UI 永久卡 Loading；
                    // 但 early return 会跳过后续 fetchBook / FileAccessValidator / pageController.resetBook，
                    // 导致 pageController 仍持有上一次 book 引用，用户切回时进度错位。
                    // 改为：仅当 allChapters 为空时设置 Error，但继续走 fetchBook（fetchBook 失败也会映射 Error）。
                    Logger.e("MainReadViewModel:bookload: reparse chapters failed: ${e.message}", e)
                    if (allChapters.isEmpty()) {
                        _uiState.value = BookReaderUiState.Error(
                            context.getString(R.string.chapter_load_failed)
                        )
                        // 不 return@launchIO，让 fetchBook 继续执行
                    }
                    emptyList()
                }
                if (reparsed.isNotEmpty()) {
                    // v2 修复（review §O4）：先更新内存（cancel-safe），再更新 DB（写失败回滚内存）。
                    // 原实现顺序是 replaceChaptersByBookIdUseCase → allChapters.clear/addAll，
                    // 若 DB 写失败或协程被取消（用户快速返回），会出现 DB 已替换但 allChapters 未同步。
                    // 改为：先内存（不会失败）→ 再 DB（写失败时内存回滚到旧数据）。
                    val previousChapters = allChapters.toList()
                    allChapters.clear()
                    allChapters.addAll(reparsed)
                    Logger.d("MainReaderViewModel:bookload:load all chapters from book file:${System.currentTimeMillis()}")
                    var dbWriteOk = true
                    try {
                        // 原子替换：单事务 delete + insert，消除并发 Flow 收集器读到中间态的窗口。
                        replaceChaptersByBookIdUseCase(bookId, reparsed)
                    } catch (e: Exception) {
                        // DB 写失败：回滚内存到旧数据，保持 DB 与内存一致
                        dbWriteOk = false
                        allChapters.clear()
                        allChapters.addAll(previousChapters)
                        Logger.e("MainReadViewModel: replaceChapters DB write failed, rolled back memory", e)
                    }

                    // ★ v12 TXT charset 回填：扫描时探测的 charsetName 持久化到 BookEntity.txtCharset，
                    // 下次打开时 TxtTextParser.resolveCharsetName 直接命中（O(1)），无需重新探测。
                    // 非 TXT 格式 lastScanCharsetName 返回 null，整个链路短路，无副作用。
                    // runCatching：回填失败不影响本次打开（章节已写入 DB），下次打开时 resolveCharsetName 会现场探测兜底。
                    //
                    // v2 修复：原实现 `(textParser as? TxtTextParser)?.lastScanCharsetName(bookId)` 永远
                    // 返回 null（Hilt 注入的实际类型是 TextParserImpl），导致 txtCharset 永不回填。
                    // 改为通过 TextParser 接口的 lastScanCharsetName 默认方法调用，由 TextParserImpl
                    // 转发到 TxtTextParser（详见 docs/plans/plan-txt-chapter-scanner-fix.md §3.2 改动 0）。
                    //
                    // 仅在章节写库成功时回填——否则会出现"章节没写成但 charset 写成"的孤儿状态。
                    if (dbWriteOk) {
                        textParser.lastScanCharsetName(bookId)?.let { charsetName ->
                            runCatching { txtBookMetaStore.updateCharset(bookId, charsetName) }
                        }
                    }
                }
                // 解析失败：保留 allChapters（原 DB 数据，即便脏也比全空强），不删库。
            }
            _showOutChapters.value = allChapters.filter {
                !it.chapterName.isEmpty()
            }

            if (fetchBook(bookId)) {
                var newBook = _book.value ?: return@launchIO

                val cover = newBook.coverImage
                if (_displayCover.value.isNullOrEmpty() && !cover.isNullOrEmpty()) {
                    _displayCover.value = cover
                }

                // ★ 兜底校验：文件可访问性检查（防止深链接/自动恢复绕过第一层校验）
                val fileResult = FileAccessValidator.check(
                    context, bookUri, newBook.source
                )
                if (fileResult != FileAccessValidator.Result.ACCESSIBLE) {
                    _uiState.value = BookReaderUiState.Error(
                        when (fileResult) {
                            FileAccessValidator.Result.FILE_NOT_FOUND ->
                                context.getString(R.string.file_deleted_externally_simple)
                            else -> context.getString(R.string.file_not_accessible)
                        }
                    )
                    return@launchIO
                }

                if (openedBookId >= 0) {
                    _appPreferences.value?.let { pref ->
                        if (pref.lastBookId != openedBookId) {
                            viewModelScope.launch {
                                appPrefsUtil.updateAppPreferences(pref.copy(lastBookId = openedBookId))
                            }
                        }
                    }
                }

                Logger.d("MainReaderViewModel:bookload:load reset book to pageController:${System.currentTimeMillis()}")
                pageControllerOwnerToken = pageController.resetBook(newBook) { success ->//重新加载章节数
                    if (success) {
                        Logger.d("MainReaderViewModel:bookload: LOAD_SUCCESS @ ${System.currentTimeMillis()}")
                        _uiState.value = BookReaderUiState.LOAD_SUCCESS
                        Logger.d("MainReaderViewModel:bookload:load current chapter success:${System.currentTimeMillis()}")

                        _readProgression.value = (newBook.progress / 100.0).coerceIn(0.0, 1.0)
                        // 初始加载完成后立即检查TTS可用性
                        _enableTts.value = !pageController.currentPage()?.text.isNullOrEmpty()

                        _showProgressBar.value = newBook.wordCount > 0L
                        if (newBook.wordCount == 0L) {
                            loadChapterWords()
                        }
                    } else {
                        // P0-2 修复：章节加载失败 → Error（原先无论成败都置 LOAD_CHAPTER_SUCCESS 导致白屏）
                        Logger.e("MainReaderViewModel:bookload: chapter load failed")
                        _uiState.value = BookReaderUiState.Error(
                            context.getString(R.string.chapter_load_failed)
                        )
                    }
                }
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
        if (_navigationLoading.value.isLoading || pendingLoadingTargetIndex >= 0) return
        if (_readerPreferences.value.volumeKeyPageTurning) {
            pageController.animToPrev()
        }
    }

    private fun onVolumeDown() {
        if (_navigationLoading.value.isLoading || pendingLoadingTargetIndex >= 0) return
        if (_readerPreferences.value.volumeKeyPageTurning) {
            pageController.animToNext()
        }
    }

    private fun loadChapterWords() {
        viewModelScope.launchIO {
            try {
                delay(1500)
                pageController.calcChaptersWords()
                // 同步 wordCount 到 _book.value（防止被 onPageChange 的 copy() 覆盖）
                val totalWordCount = pageController.curTextChapter?.totalWordCount ?: 0L
                if (totalWordCount > 0L) {
                    _book.value?.let { currentBook ->
                        if (currentBook.wordCount != totalWordCount) {
                            _book.value = currentBook.copy(wordCount = totalWordCount)
                        }
                        // 刷新 allChapters：calcChaptersWords 已将 wordCount + chapterProgress 写入 DB
                        val updated = getChaptersByBookIdUserCase.invoke(currentBook.id).firstOrNull().orEmpty()
                        if (updated.isNotEmpty()) {
                            allChapters.clear()
                            allChapters.addAll(updated)
                        }
                    }
                    _showProgressBar.value = true
                    _readProgression.value = pageController.progression
                }
            } finally {
                // 兜底：calcChaptersWords 后 totalWordCount 仍为 0（纯图片书/空书），
                // 从 DB 读取最新 progression 作为显示值
                if (pageController.curTextChapter?.totalWordCount ?: 0L == 0L) {
                    val bookId = _book.value?.id ?: return@launchIO
                    val freshBook = getBookByIdUseCase(bookId) ?: return@launchIO
                    _readProgression.value = (freshBook.progress / 100.0).coerceIn(0.0, 1.0)
                }
            }
        }
    }

    internal fun handleNavigationLoadingStart(targetChapterIndex: Int, immediate: Boolean = false) {
        Logger.d("MainReadViewModel::handleNavigationLoadingStart: targetChapterIndex=$targetChapterIndex, immediate=$immediate")
        cancelLoadingJobs()
        pendingLoadingTargetIndex = targetChapterIndex
        val chapterName = getChapterName(targetChapterIndex)

        if (immediate) {
            loadingShowTimestamp = System.currentTimeMillis()
            _navigationLoading.value = NavigationLoadingState(
                isLoading = true,
                targetChapterIndex = targetChapterIndex,
                targetChapterName = chapterName,
            )
            startTimeoutJob()
        } else {
            loadingShowJob = viewModelScope.launch {
                delay(LOADING_SHOW_DELAY_MS)
                if (pendingLoadingTargetIndex == targetChapterIndex
                    && pageController.durChapterIndex == targetChapterIndex) {
                    loadingShowTimestamp = System.currentTimeMillis()
                    _navigationLoading.value = NavigationLoadingState(
                        isLoading = true,
                        targetChapterIndex = targetChapterIndex,
                        targetChapterName = chapterName,
                    )
                    startTimeoutJob()
                }
            }
        }
    }

    internal fun handleNavigationLoadingComplete(chapterIndex: Int) {
        Logger.d("MainReadViewModel::handleNavigationLoadingComplete: chapterIndex=$chapterIndex")
        if (_navigationLoading.value.targetChapterIndex != chapterIndex
            && pendingLoadingTargetIndex != chapterIndex) {
            return
        }
        dismissLoadingWithMinDisplay()
    }

    internal fun handleNavigationLoadingError(chapterIndex: Int) {
        Logger.e("MainReadViewModel::handleNavigationLoadingError: chapterIndex=$chapterIndex")
        if (_navigationLoading.value.targetChapterIndex == chapterIndex
            || pendingLoadingTargetIndex == chapterIndex) {
            cancelLoadingJobs()
            _navigationLoading.value = NavigationLoadingState()
            ToastUtil.show(R.string.chapter_load_failed)
        }
    }

    private fun startTimeoutJob() {
        loadingTimeoutJob = viewModelScope.launch {
            delay(LOADING_TIMEOUT_MS)
            Logger.w("MainReadViewModel::navigation loading timeout, force dismiss")
            cancelLoadingJobs()
            _navigationLoading.value = NavigationLoadingState()
            ToastUtil.show(R.string.chapter_load_failed)
        }
    }

    private fun dismissLoadingWithMinDisplay() {
        cancelLoadingJobs()
        if (!_navigationLoading.value.isLoading) {
            return
        }
        val elapsed = System.currentTimeMillis() - loadingShowTimestamp
        if (elapsed >= LOADING_MIN_DISPLAY_MS) {
            _navigationLoading.value = NavigationLoadingState()
        } else {
            val remaining = LOADING_MIN_DISPLAY_MS - elapsed
            loadingMinDisplayJob = viewModelScope.launch {
                delay(remaining)
                _navigationLoading.value = NavigationLoadingState()
            }
        }
    }

    private fun cancelLoadingJobs() {
        loadingShowJob?.cancel()
        loadingShowJob = null
        loadingTimeoutJob?.cancel()
        loadingTimeoutJob = null
        loadingMinDisplayJob?.cancel()
        loadingMinDisplayJob = null
        pendingLoadingTargetIndex = -1
    }

    override fun onContinuousScrollLoadingChanged(isLoading: Boolean) {
        if (isLoading) {
            cancelLoadingJobs()
            loadingShowJob = viewModelScope.launch {
                delay(LOADING_SHOW_DELAY_MS)
                loadingShowTimestamp = System.currentTimeMillis()
                _continuousScrollLoading.value = true
                loadingTimeoutJob = viewModelScope.launch {
                    delay(LOADING_TIMEOUT_MS)
                    Logger.w("MainReadViewModel::continuous scroll loading timeout")
                    cancelLoadingJobs()
                    _continuousScrollLoading.value = false
                }
            }
        } else {
            loadingShowJob?.cancel()
            loadingShowJob = null
            dismissContinuousScrollLoadingMinDisplay()
        }
    }

    private fun dismissContinuousScrollLoadingMinDisplay() {
        cancelLoadingJobs()
        if (!_continuousScrollLoading.value) return
        val elapsed = System.currentTimeMillis() - loadingShowTimestamp
        if (elapsed >= LOADING_MIN_DISPLAY_MS) {
            _continuousScrollLoading.value = false
        } else {
            loadingMinDisplayJob = viewModelScope.launch {
                delay(LOADING_MIN_DISPLAY_MS - elapsed)
                _continuousScrollLoading.value = false
            }
        }
    }

    fun resetReadingSession() {
        isReadingSessionActive = false
        lastLocatorChangeTime = 0L
    }

    override fun onCleared() {
        searchJob?.cancel()
        Logger.i("MainReadViewModel::onCleared:ownerToken=$pageControllerOwnerToken")
        pageController.navigationLoadingListener = null
        if (pageController.clickListener === this) {
            pageController.clickListener = null
        }
        if (pageController.callBack === pageProvider) {
            pageController.callBack = null
        }
        cancelLoadingJobs()
        pageController.clear(pageControllerOwnerToken)
        pageController.scope.launchIO { pageProvider.clear() }
        currentDayStartTime = 0
//        _initialLocator.value = null
        _currentBookId.value = null
        // O2 修订：删除 _uiState = Loading 复位。新 VM init 时 _uiState 初始值本就是 Loading，
        // 复位是冗余的；且 Activity 销毁重建时与 rememberSaveable 持久化的 showState 冲突。
        _book.value = null
        isReadingSessionActive = false
        lastLocatorChangeTime = 0L

        // K4：通知触发2评估（HomeViewModel 收到后检查连续天数）
        reviewPromptManager.notifyReadingSessionEnded()

        super.onCleared()
        Logger.i("MainReadViewModel::onCleared")
    }

    override fun onCenterClick() {
        _showMenu.value = !_showMenu.value
    }

    override fun hideMenu() {
        _showMenu.value = false
    }

    override fun showMenu() {
        _showMenu.value = true
    }

    fun onChapterClick(chapter: BookChapter) {
        pageController.changeChapter(chapter.chapterIndex)
    }

    /***
     * 点击link链接跳转到对应章节
     */
    override fun onLinkClick(href: String?, clickX: Float, clickY: Float) {
        Logger.d("MainReaderViewModel::onLinkClick:href=$href")
        if (!href.isNullOrEmpty()) {
            if (href.startsWith("http")) {
                //跳转到h5界面显示
                _outHref.value = href
                _showOutHrefDialog.value = true
            } else {
                val curIndex = curChapterIndex.value
                val currentChapter: BookChapter? =
                    allChapters.firstOrNull { it.chapterIndex == curIndex }
                if (currentChapter == null) {
                    Logger.d("MainReaderViewModel::onLinkClick:currentChapter is null")
                    return
                }
                val currentChapterSrc = currentChapter.srcName
                if (currentChapterSrc.isNullOrEmpty()) {
                    Logger.e("MainReaderViewModel::onLinkClick:currentChapter src is null")
                    return
                }

                // 检查 href 是否与某个章节的 srcName 完全匹配（章节级链接）
                val directChapterMatch = allChapters.firstOrNull {
                    it.srcName?.equals(href, ignoreCase = true) == true
                }
                if (directChapterMatch != null) {
                    Logger.i("MainReaderViewModel::onLinkClick:href matches chapter srcName, chapter=${directChapterMatch.chapterIndex}")
                    if (directChapterMatch.chapterIndex != curIndex) {
                        pageController.changeChapter(directChapterMatch.chapterIndex)
                    } else {
                        pageController.gotoChapterStart()
                    }
                    return
                }

                // 解析 href 的文件名部分，判断是否为同章节锚点
                val (hrefSrcName, _) = JumpHelper.parseHrefForFileCheck(href)
                val isSameFileLink = JumpHelper.isSameFile(hrefSrcName, currentChapterSrc) //同一个章节中的锚点

                val linkContent: String? = if (isSameFileLink) {
                    pageController.findLinkContent(href)
                } else {
                    null
                }
                //TODO 这里是否显示注释的弹窗， 需要有更完善的判断机制
                if (!linkContent.isNullOrEmpty()) {                                                 //本章节中的注释
                    Logger.d("MainReadViewModel:onLinkClick:linkContent=${linkContent}")
                    if (clickX >= 0 && clickY >= 0) {
                        _clickedLinkContent.value = LinkedContent(linkContent, clickX, clickY)
                    }
                } else {                                                                            //需要跳转到其他章节
                    var targetSrcName = ""
                    var targetAnchorId = ""
                    if (href.contains("#")) {
                        val hrefParts = href.split("#")
                        if (hrefParts.size == 2) {
                            targetSrcName = hrefParts[0]
                            targetAnchorId = hrefParts[1]
                        }
                    } else {
                        targetSrcName = href
                    }

                    val chapters = allChapters.toList()
                    if (chapters.isEmpty()) {
                        Logger.w("MainReaderViewModel::onLinkClick:chapters not loaded yet, skip")
                        return
                    }

                    val targetChapter = JumpHelper.findTargetChapter(chapters, targetSrcName, targetAnchorId, href)
                    if (targetChapter != null) {
                        Logger.i("MainReaderViewModel::onLinkClick:found target chapter=${targetChapter.chapterIndex}, srcName=${targetChapter.srcName}")

                        if (targetChapter.chapterIndex == curIndex) { //目标章节就是当前章节
                            if (targetAnchorId.isNotEmpty()) { //而且其锚点id不为空，则在当前章节中跳转到锚点
                                pageController.locateAnchorInCurrentChapter(targetAnchorId)
                            }
                        } else { //不是当前章节
                            if (targetAnchorId.isNotEmpty()) {  //有锚点，则先跳转到章节，然后在解析到章节数据之后，再跳转到目标锚点页面位置
                                pageController.changeChapterWithAnchor(targetChapter.chapterIndex, targetAnchorId)
                            } else {  //没有锚点，则直接跳转到目前章节即可
                                pageController.changeChapter(targetChapter.chapterIndex)
                            }
                        }
                    } else {
                        Logger.w("MainReaderViewModel::onLinkClick:href=$href, no target chapter found")
                    }
                }
            }
        }
    }



    /***
     * 滑动切换界面，或者跳转切换界面时，通知进度刷新
     */
    override fun onPageChange() {
        val curChapter = pageController.textChapter(0)
        val curChapterIndex = pageController.durChapterIndex
        val curPageInChpaterIndex = pageController.durPageIndex
        Logger.d("MainReadViewModel:onPageChange:chapter.index=${curChapterIndex},page.index=${pageController.durPageIndex}")
        val newProgression = pageController.progression

        if (curChapter != null) {
            _readProgression.value = newProgression
            _curChapterIndex.value = curChapterIndex
            _curChapterName.value = curChapter.title
            _isBookmarked.value =
                (curChapter.pages.getOrNull(pageController.durPageIndex)?.bookmarkId ?: -1) > 0
            _enableTts.value = !pageController.currentPage()?.text.isNullOrEmpty()
        }

        val isLastPage = if (curChapter != null) {
                (curChapterIndex >= curChapter.chaptersSize - 1 && curPageInChpaterIndex >= curChapter.pageSize - 1)
            } else {
                false
            }

        _selectionVisibleOnPage.value = pageController.isSelectionOnCurrentPage()

        viewModelScope.launch {
            if (isReadingSessionActive) {
                updateReadingTime()
            } else {
                isReadingSessionActive = true
                lastLocatorChangeTime = System.currentTimeMillis()
            }
            updateStartReadingDate() //尝试更新开始阅读时间

            if (newProgression >= 0.999) {  //尝试更新结束阅读时间
                updateEndReadingDate()
                // F1：此分支在 curBook(:1458) 声明之前，作用域内无 curBook，单独读取 _book.value
                // E1：进程内 Set 去重，防与 isLastPage 分支重复通知
                // P1-2：仅 readingStatus 非 FINISHED 时触发（跨 session 重复打开已读完的书不再误触发）
                val book = _book.value
                if (book != null
                    && book.readingStatus != ReadingStatus.FINISHED.value
                    && notifiedBookIds.add(book.id)) {
                    reviewPromptManager.notifyBookFinished(book.id, book.wordCount)
                }
            }

            var curBook = _book.value ?: return@launch
            val readingStatus = curBook.readingStatus ?: 0

            //更新阅读状态
            if (readingStatus == 0) {
                curBook = curBook.copy(readingStatus = ReadingStatus.IN_PROGRESS.value)
                _book.value = curBook
                updateReadingStatusUseCase(curBook.id, ReadingStatus.IN_PROGRESS)
            } else if (isLastPage && readingStatus != ReadingStatus.FINISHED.value) {
                curBook = curBook.copy(readingStatus = ReadingStatus.FINISHED.value)
                _book.value = curBook
                updateReadingStatusUseCase(curBook.id, ReadingStatus.FINISHED)
                // E1：isLastPage 跃迁为 FINISHED 的那一刻发 trigger（D5 best-effort）
                if (notifiedBookIds.add(curBook.id)) {
                    reviewPromptManager.notifyBookFinished(curBook.id, curBook.wordCount)
                }
            }
        }
    }

    @Volatile
    private var selectedLocator: Locator? = null

    private var pendingSentenceText: String? = null

    /***
     * click one annotation then show out TextToolbar
     */
    override fun onCheckedAnnotation(annotationIds: List<String>, rect: RectF) {
        Logger.d("MainReadViewModel::onCheckedAnnotation:annotationIds=$annotationIds,retc=$rect")
        viewModelScope.launch {
            val curAnnotations = _annotations.firstOrNull() ?: return@launch
            val targetAnnotations = curAnnotations.filter {
                annotationIds.contains(it.id.toString())
            }
            if (targetAnnotations.isNotEmpty()) {
                checkedAnnotations.clear()
                checkedAnnotations.addAll(targetAnnotations)

                val targetAnnotation = targetAnnotations.firstOrNull()
                _selectedAnnotation.value = targetAnnotation
                val locator = targetAnnotation?.locatorInfo ?: return@launch
                selectedLocator = locator
                _textToolbarRect.value = rect.toRect()
                showColorSelectionPanel(true)
                textToolbarOpen(true)
            }
        }
    }

    override fun onCheckedNote(noteId: String, rect: RectF) {
        viewModelScope.launch {
            val curNotes = _notes.firstOrNull() ?: return@launch
            val targetNote = curNotes.firstOrNull {
                it.id == noteId.toLongOrNull()
            }
            if (targetNote != null) {
                _selectedNote.value = targetNote
            }
            Logger.d("MainReadViewModel::onCheckedNote::noteId=$noteId,targetNote[$targetNote]")
        }
    }

    private var checkedAnnotationJob: Job? = null

    fun onContinuousScrollCheckedAnnotation(annotationIds: List<String>, rect: RectF) {
        checkedAnnotationJob?.cancel()
        checkedAnnotationJob = viewModelScope.launch {
            val curAnnotations = _annotations.firstOrNull() ?: return@launch
            val targetAnnotations = curAnnotations.filter {
                annotationIds.contains(it.id.toString())
            }
            if (targetAnnotations.isNotEmpty()) {
                checkedAnnotations.clear()
                checkedAnnotations.addAll(targetAnnotations)
                _selectedAnnotation.value = targetAnnotations.firstOrNull()
                _textToolbarRect.value = rect.toRect()
                showColorSelectionPanel(true)
                _isShowTextToolbar.value = true
                _selectionVisibleOnPage.value = true
                targetAnnotations.firstOrNull()?.locatorInfo?.let { selectedLocator = it }
                hideMenu()
            }
        }
    }

    /***
     * 点选不同的TextToolbar 按钮时，
     * 判断当前选中的标注是否 是 对应的标注
     */
    fun onShowTextAnnotationAction(type: AnnotationType) {
        viewModelScope.launch {
            if (_selectedAnnotation.value?.type != type) {
                _selectedAnnotation.value = checkedAnnotations.firstOrNull {
                    it.type == type
                }
            }
        }
    }

    override fun onSelectedText(startX: Float, startY: Float, endX: Float, endY: Float) {

        val rect = Rect(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt())
        _textToolbarRect.value = rect

        selectedLocator = null
        pendingSentenceText = null
        val locator = this.pageController.getSelectedLocator()
        if (locator != null) {
            selectedLocator = locator
            pendingSentenceText = extractSentenceText(locator)
        }
        Logger.d("MainReadViewModel:onSelectedText:[$selectedLocator]")
        if (selectedLocator != null) {
            showColorSelectionPanel(false)
            textToolbarOpen(true)
            hideMenu()
        }
    }

    fun onContinuousScrollTextSelected(
        startX: Float, startY: Float,
        endX: Float, endY: Float
    ) {
        onSelectedText(
            startX, startY,
            endX, endY
        )
        _selectionVisibleOnPage.value = true
    }

    fun handleHighlight(color: ComposeColor) {
        val bookid = _currentBookId.value ?: return
        val locator = selectedLocator ?: return
        Logger.d("MainReadViewModel:handleHighlight,locator=$locator")
        selectedLocator = null
        val colorStr: String = color.toStringColor()
        viewModelScope.launch {
            textToolbarOpen(false)
            cancelTextSelected()
            val conflictAnnotations = emptyList<BookAnnotation>()
            val newAnnotation = BookAnnotation(
                bookId = bookid,
                locator = locator.toJsonString(),
                color = colorStr,
                note = null,
                type = AnnotationType.HIGHLIGHT
            )
            val annotationId = addAnnotationUseCase(newAnnotation)
            val newAnnotation2 = newAnnotation.copy(id = annotationId)
            _annotations.value += newAnnotation2
            _selectedAnnotation.value = newAnnotation2
            currentBookId.value?.let { loadAnnotations(it) }
            pageController.updateChapter(newAnnotation2, null, null, conflictAnnotations)
            updateHighlinesAndUnderlines()
        }
    }

    fun updateHighlinesAndUnderlines() {
        _highlights.value = _annotations.value.filter {
            it.type == AnnotationType.HIGHLIGHT
        }.reversed()

        _underlines.value = _annotations.value.filter {
            it.type == AnnotationType.UNDERLINE
        }.reversed()
    }

    fun handleNote() {
        _showNoteDialog.value = true
    }

    fun handleUnderline(color: ComposeColor) {
        Logger.d("MainReadViewModel:handleUnderline")
        val bookid = _currentBookId.value ?: return
        val locator = selectedLocator ?: return
        val colorStr: String = color.toStringColor()
        Logger.d("MainReadViewModel:handleUnderline:bookid=$bookid, locator=${locator}, color=$colorStr")
        selectedLocator = null
        viewModelScope.launch {
            textToolbarOpen(false)
            cancelTextSelected()
            val conflictAnnotations = emptyList<BookAnnotation>()

            val newAnnotation = BookAnnotation(
                bookId = bookid,
                locator = locator.toJsonString(),
                color = colorStr,
                note = null,
                type = AnnotationType.UNDERLINE
            )
            val annotationId = addAnnotationUseCase(newAnnotation)
            val newAnnotation2 = newAnnotation.copy(id = annotationId)
            _annotations.value += newAnnotation2
            _selectedAnnotation.value = newAnnotation2
            currentBookId.value?.let { loadAnnotations(it) }
            pageController.updateChapter(newAnnotation2, null, null, conflictAnnotations)
            updateHighlinesAndUnderlines()

        }
    }

    override fun onSelectedCancel() {
        Logger.i("MainReadViewModel::onSelectedCancel")
        textToolbarOpen(false)
        _textToolbarRect.value = Rect(0, 0, 0, 0)
    }

    override suspend fun updateReadingTime(force: Boolean) {
        val currentTime = System.currentTimeMillis()
        if (lastLocatorChangeTime != 0L) {
            val sessionDuration = currentTime - lastLocatorChangeTime
            if (force || sessionDuration >= 3000) {
                updateBookReadingTime(sessionDuration)
                updateReadingActivity(sessionDuration)
                lastLocatorChangeTime = currentTime
            }
        } else {
            lastLocatorChangeTime = currentTime
        }
    }

    private suspend fun updateStartReadingDate() {
        val book = _book.value ?: return
        if (book.startReadingDate == null) {
            val startDate = System.currentTimeMillis()
            updateStartReadingDateUseCase(book.id, startDate)
            _book.value = book.copy(startReadingDate = startDate)
        }
    }

    private suspend fun updateEndReadingDate() {
        val book = _book.value ?: return
        if (book.endReadingDate == null) {
            val endDate = System.currentTimeMillis()
            updateEndReadingDateAndStatusUseCase(book.id, endDate, ReadingStatus.FINISHED)
            _book.value =
                book.copy(endReadingDate = endDate, readingStatus = ReadingStatus.FINISHED.value)
        }
    }

    private suspend fun updateBookReadingTime(sessionDuration: Long) {
        currentBookId.value?.let { bookId ->
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
        incrementReadingActivityTimeUseCase(currentDate, sessionDuration)
    }

    fun updateProgressionFromScroll(progression: Double, chapterIndex: Int, pageIndex: Int, chapterTitle: String? = null) {
        _readProgression.value = progression
        _curChapterIndex.value = chapterIndex
        if (chapterTitle != null) {
            _curChapterName.value = chapterTitle
        } else {
            val chapter = pageController.textChapter(0)
            if (chapter != null) {
                _curChapterName.value = chapter.title
            }
        }
        val chapter = pageController.textChapter(0)
        if (chapter != null && chapter.position == chapterIndex) {
            _isBookmarked.value = (chapter.pages?.getOrNull(pageIndex)?.bookmarkId ?: -1) > 0
        }
    }

    /***
     * 拖动阅读进度条来改变阅读位置
     */
    fun changePageByProgress(newProgress: Double): Boolean {
        var targetChapter: BookChapter? = null
        val curTextChapter = pageController.curTextChapter ?: return false
        if (curTextChapter.totalWordCount == 0L) {
            val bookWordCount = _book.value?.wordCount ?: 0L //防止 wordCount 还没有同步的问题
            if (bookWordCount > 0L) {
                curTextChapter.totalWordCount = bookWordCount
            } else {
                ToastUtil.show(stringResource(R.string.is_load_chapter_info))
                return false
            }
        }
        for (index in 0 until allChapters.size) {
            val startProgress: Double = allChapters[index].chapterProgress.toDouble()
            val endProgress: Double = if (index < allChapters.size - 1) {
                allChapters[index + 1].chapterProgress.toDouble()
            } else {
                1.001
            }
            if (newProgress >= startProgress && newProgress < endProgress) {
                targetChapter = allChapters[index]
            }
        }
        Logger.d("MainReadViewModel::changePageByProgress:newProgress[$newProgress],targetChapterIndex=${targetChapter?.chapterIndex}")
        targetChapter?.chapterIndex?.let { newChapterIndex ->
            pageController.changeChapter(newChapterIndex, newProgress)
        }
        return true
    }

    fun textToolbarOpen(open: Boolean = true) {
        _isShowTextToolbar.value = open
        if (open) {
            _selectionVisibleOnPage.value = pageController.isSelectionOnCurrentPage()
        } else {
            _selectionVisibleOnPage.value = false
        }
    }

    fun showColorSelectionPanel(open: Boolean = true) {
        _isShowColorSelectionPanel.value = open
    }

    fun readerUISettingsOpen(open: Boolean = true) {
        _showReaderUISettings.value = open
    }

    fun readerSettingsOpen(open: Boolean = true) {
        _showReaderSettings.value = open
        if (open) {
            loadTranslatorItems()
            loadDictionaryItems()
        }
    }

    fun showReadBgList(open: Boolean = true) {
        _showReadBgList.value = open
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
                _notes.value = updatedNotes.reversed()
            }
        }
    }

    /**
     * 加载书签列表
     */
    private fun loadBookmarks(bookId: Long) {
        viewModelScope.launch {
            getBookmarksForBookUseCase(bookId).collect { bookmarks ->
                _bookmarks.value = bookmarks.reversed()
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
            pageController.updateChapterByUpdateNote(note)
        }
    }

    /***
     * 删除笔记
     */
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            deleteNoteUseCase(note)
            currentBookId.value?.let { loadNotes(it) }
            pageController.updateChapter(null, null, note, emptyList())
        }
    }

    fun addNote(noteText: String, color: ComposeColor) {
        val locator = selectedLocator ?: return
        val bookId = _currentBookId.value ?: return
        val noteColor = color.toStringColor()
        val newNote = Note(
            bookId = bookId,
            locator = locator.toJsonString(),
            selectedText = locator.text,
            note = noteText,
            color = noteColor,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val newNoteId = addNotesUseCase(newNote)
            currentBookId.value?.let {
                loadNotes(it)
            }
            val newNote2 = newNote.copy(id = newNoteId)
            pageController.updateChapter(null, newNote2, null, emptyList())
            textToolbarOpen(false)
            cancelTextSelected()
            selectedLocator = null
        }
    }

    /***
     * 加载注释列表
     */
    private fun loadAnnotations(bookId: Long) {
        viewModelScope.launch {
            getAnnotationsUseCase(bookId).collect { annotationsList ->
                _annotations.value = annotationsList
                updateHighlinesAndUnderlines()
            }
        }
    }

    /***
     * 移除书签
     */
    fun deleteBookmark(bookmark: Bookmark? = null) {
        Logger.i("MainReadViewModel:deleteBookmark")
        viewModelScope.launch {
            val mark = if (bookmark == null) {
                val bookmarkId =
                    pageController.curTextChapter?.pages?.getOrNull(pageController.durPageIndex)?.bookmarkId
                        ?: return@launch
                _bookmarks.value.firstOrNull {
                    it.id == bookmarkId
                }
            } else {
                bookmark
            }
            Logger.d("MainReadViewModel:deleteBookmark:markid=${mark?.id}")
            if (mark != null) {
                deleteBookmarkUseCase(mark)
                currentBookId.value?.let { loadBookmarks(it) }
                if (pageController.updateChapterByDelBookmark(mark)) {
                    val isBookmarked =
                        (pageController.curTextChapter?.pages?.getOrNull(pageController.durPageIndex)?.bookmarkId
                            ?: -1) > 0
                    _isBookmarked.value = isBookmarked
                    Logger.d("MainReadViewModel:deleteBookmark:_isBookmarked=${isBookmarked}")
                }
            }
        }
    }

    fun addBookmark() {
        Logger.i("MainReadViewModel:addBookmark")
        val bookid = _currentBookId.value ?: return
        pageController.getCurrentPageLocator()?.let { locator ->
            viewModelScope.launch {
                val currentTime = System.currentTimeMillis()
                val newBookmark = Bookmark(
                    locator = locator.toJsonString(),
                    chapterIndex = locator.chapterIndex,
                    bookId = bookid,
                    dateAndTime = currentTime,
                    color = ComposeColor.Blue.toStringColor()
                )
                val newBookmarkId = addBookmarksUseCase(newBookmark)
                val newBookmark2 = newBookmark.copy(id = newBookmarkId)
                Logger.i("MainReadViewModel:addBookmark[${newBookmark2}]")
                currentBookId.value?.let { loadBookmarks(it) }
                if (pageController.updateChapterByAddBookmark(newBookmark2)) {
                    val isBookmarked =
                        (pageController.curTextChapter?.pages?.getOrNull(pageController.durPageIndex)?.bookmarkId
                            ?: -1) > 0
                    Logger.d("MainReadViewModel:addBookmark:isBookmarked[${isBookmarked}]")
                    _isBookmarked.value = isBookmarked
                }
            }
        }
    }

    /***
     * 删除注释
     */
    fun deleteAnnotation(annotation: BookAnnotation) {
        Logger.d("MainReaderViewModel::deleteAnnotation:${annotation}")
        viewModelScope.launch {
            deleteAnnotationUseCase(annotation)
            _annotations.update { currentAnnotations ->
                currentAnnotations.filter { it.id != annotation.id }
            }
            _selectedAnnotation.value = null
            currentBookId.value?.let { loadAnnotations(it) }
            pageController.updateChapter(null, null, null, arrayListOf(annotation))
            updateHighlinesAndUnderlines()
        }
    }

    /***
     * 更新注释
     */
    fun updateAnnotation(annotation: BookAnnotation) {
        Logger.d("MainReaderViewModel::updateAnnotation:$annotation")
        viewModelScope.launch {
            updateAnnotationUseCase(annotation)
            _annotations.update { currentAnnotations ->
                currentAnnotations.map { if (it.id == annotation.id) annotation else it }
            }
            _selectedAnnotation.value = annotation
            currentBookId.value?.let { loadAnnotations(it) }
            pageController.updateChapterByUpdateAnnotation(annotation)
            updateHighlinesAndUnderlines()
        }
    }

    fun resetReaderUIPreferences() {
        viewModelScope.launch {
            readerPrefsUtil.resetReadUiPreferences()
        }
    }

    /**
     * 切换阅读主题（switchTheme）。
     *
     * 流程（O-01 保留 Room 存档 + Q-06 防重入 + L-02 回滚）：
     * 1. 防重入：[isThemeSwitching] 为 true 时直接返回。
     * 2. saveCurrent：若当前有主题且与目标不同，把当前偏好存入 Room（reader_theme_configs）。
     * 3. loadTarget：从 Room 读取目标主题存档；无存档则用预设默认值（ReaderThemePresets）。
     * 4. 单次批量写：[ReaderPreferencesUtil.updatePreferences]（Q-10 禁逐字段 setter，避免 N 次磁盘写+upStyle 卡顿）。
     *    批量写触发单次 Flow emit → collector → updatePageViews(resetPageOffset=false) 保留阅读位置（L-01/F4-01）。
     * 5. 异常处理：catch 记录日志 + Toast 反馈（R-03）；Room 存档已写入则幂等无害（L-05 回滚顺序正确）。
     */
    fun switchTheme(themeId: String) {
        if (isThemeSwitching) return  // Q-06
        viewModelScope.launch {
            isThemeSwitching = true
            try {
                if (isPerBookMode) {
                    // ★ v12 per-book：只更新 meta.selectedThemeId，effective 流自动按新 (bookId, themeId) 重算
                    // P-CONCUR-2：不预建 snapshot 行——切到无快照的新主题时，effective 流走 preset 兜底，
                    // 用户看到该主题 preset 默认值（正确行为：切到一个没改过的主题，本就该看 preset）。
                    // 用户改设置时 saveSnapshot 自然建行。
                    // P-CONCUR-3：upsert 失败时回滚乐观更新（外层 catch 不回滚 _perBookMeta，需内层处理）
                    val bookId = currentBookId.value ?: return@launch
                    val meta = perBookMetaDao.getByBookId(bookId) ?: return@launch
                    if (meta.selectedThemeId != themeId) {
                        val newMeta = meta.copy(selectedThemeId = themeId, updatedAt = System.currentTimeMillis())
                        try {
                            _perBookMeta.value = newMeta  // 乐观更新，消除 Room 回调窗口
                            perBookMetaDao.upsert(newMeta)
                        } catch (e: Exception) {
                            _perBookMeta.value = meta  // 回滚乐观更新
                            throw e  // 重新抛出，由外层 catch 记录日志 + Toast
                        }
                    }
                    // * 标记由 applyReaderPreferences 自动维护（effective 流重算后 differsFrom 判定）
                } else {
                    // 1. saveCurrent：存档当前主题（先 Room，保证 DataStore 失败时存档幂等无害）
                    val cur = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launch
                    val curThemeId = cur.readerThemeId
                    if (curThemeId != null && curThemeId != themeId) {
                        readerThemeConfigDao.upsert(cur.toReaderThemeConfigEntity(curThemeId))
                    }

                    // 2. loadTarget：Room 存档 or 预设默认值
                    val target = readerThemeConfigDao.getByThemeId(themeId)?.toReaderPreferences(cur)
                        ?: com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(themeId)?.applyTo(cur)
                        ?: run {
                            Logger.w("MainReadViewModel::switchTheme unknown themeId=$themeId")
                            return@launch
                        }

                    // 3. 单次批量写（Q-10）
                    readerPrefsUtil.updatePreferences(target)
                    // 4. 刷新 * 号标识的非活跃部分
                    refreshModifiedThemeIds()
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::switchTheme failed: ${e.message}", e)
                ToastUtil.show(R.string.theme_update_failed)
            } finally {
                isThemeSwitching = false
            }
        }
    }

    /**
     * 切换阅读主题模式（LIGHT/DARK/AUTO，需求 3 核心）。
     *
     * **per-book 模式**：mode 与 [selectedThemeId] 一起作为 per-book 字段存于 [PerBookMetaEntity]，
     * 写 meta 不污染全局 DataStore。effective 流按新 meta.readerThemeMode 重算，自动驱动
     * SegmentedButton 选中态 / 主题过滤 / AUTO LaunchedEffect。
     *
     * **全局模式**：mode 写全局 DataStore，所有非 per-book 书跟随。
     *
     * 两分支共同尾段：若当前主题 isDark 与新模式不符 → switchTheme 到新模式的默认主题
     *（切亮→default / 切暗→amoled_black）。per-book 分支用 [perBookThemeId] 判明暗
     *（全局 readerThemeId 在 per-book 下不更新，已陈旧）。
     */
    fun updateReaderThemeMode(mode: ReaderThemeMode) {
        viewModelScope.launch {
            try {
                val newIsDark = when (mode) {
                    ReaderThemeMode.LIGHT -> false
                    ReaderThemeMode.DARK -> true
                    ReaderThemeMode.AUTO ->
                        BookApplication.app.sysIsDarkMode()
                }
                if (isPerBookMode) {
                    // ★ per-book：写 meta.readerThemeMode（不污染全局 DataStore）。
                    // 对齐 switchTheme/togglePerBookOverride 的乐观更新 + try-catch 回滚范式。
                    // P-CONCUR-3：upsert 失败时回滚 _perBookMeta，避免 UI/DB 状态分裂。
                    val bookId = currentBookId.value ?: return@launch
                    val meta = perBookMetaDao.getByBookId(bookId) ?: return@launch
                    if (meta.readerThemeMode != mode.name) {
                        val newMeta = meta.copy(
                            readerThemeMode = mode.name,
                            updatedAt = System.currentTimeMillis()
                        )
                        try {
                            _perBookMeta.value = newMeta  // 乐观更新，消除 Room 回调窗口
                            perBookMetaDao.upsert(newMeta)
                        } catch (e: Exception) {
                            _perBookMeta.value = meta  // 回滚乐观更新
                            throw e  // 重新抛出，由外层 catch 记录日志
                        }
                    }
                    // effective 流会按新 meta.readerThemeMode 重算，自动驱动 SegmentedButton/主题过滤/AUTO LaunchedEffect。
                    // 模式与当前主题明暗不符 → switchTheme 到新模式的默认主题（switchTheme 内部走 per-book 分支）。
                    val activeThemeId = perBookThemeId
                    val curIsDark = com.wxn.reader.ui.theme.ReaderThemePresets.getById(activeThemeId)?.preset?.isDark
                    if (curIsDark != null && curIsDark != newIsDark) {
                        val defaultTarget = if (newIsDark) com.wxn.reader.ui.theme.ReaderThemePresets.ID_AMOLED_BLACK
                        else com.wxn.reader.ui.theme.ReaderThemePresets.ID_DEFAULT
                        switchTheme(defaultTarget)
                    }
                } else {
                    // 全局模式：写全局 DataStore + 可能 switchTheme（原逻辑）
                    val cur = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launch
                    readerPrefsUtil.updateReaderThemeMode(mode)
                    val curIsDark = com.wxn.reader.ui.theme.ReaderThemePresets.getById(cur.readerThemeId)?.preset?.isDark
                    if (curIsDark != null && curIsDark != newIsDark) {
                        val defaultTarget = if (newIsDark) com.wxn.reader.ui.theme.ReaderThemePresets.ID_AMOLED_BLACK
                        else com.wxn.reader.ui.theme.ReaderThemePresets.ID_DEFAULT
                        switchTheme(defaultTarget)
                    }
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::updateReaderThemeMode failed: ${e.message}", e)
            }
        }
    }

    /**
     * 重置当前选中主题为预设默认值。始终有选中主题，故 readerThemeId 必非空。
     *
     * 三步顺序执行（同协程，禁止再 launch，消除 fire-and-forget 竞态）：
     * 1. 删除该主题 Room 存档（resetToThemeDefaults 原逻辑）
     * 2. 写入预设默认值到 DataStore
     * 3. 刷新非活跃主题的 * 号标识（DataStore 已写入，collector 会同步移除活跃主题的 * 号）
     *
     * 互斥守卫：[isThemeSwitching] 期间直接返回，避免切+重置并发污染 Room 存档。
     */
    fun resetCurrentTheme() {
        if (isThemeSwitching) return  // 与 switchTheme 对称的互斥守卫
        viewModelScope.launch {
            try {
                if (isPerBookMode) {
                    // ★ v12 per-book：删行 + effective 流走 preset 兜底（P-CORE-2）
                    // 删行后 snapshot 不存在 → effective 流用该主题 preset.applyTo(global) 兜底 → 显示 preset 默认值
                    // → differsFrom(preset) = false → applyReaderPreferences 自动清 *
                    // 不写"伪 reset 快照"（applyTo 不碰对齐字段，会导致对齐改动未被重置）
                    val bookId = currentBookId.value ?: return@launch
                    val themeId = perBookThemeId ?: return@launch
                    perBookThemeOverrideDao.clearForTheme(bookId, themeId)
                    // * 标记由 applyReaderPreferences 自动维护（effective 流重算后 differsFrom=false → 清 *）
                } else {
                    val cur = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launch
                    val themeId = cur.readerThemeId
                    if (themeId == null) {
                        Logger.w("MainReadViewModel::resetCurrentTheme no theme selected")
                        return@launch
                    }
                    val preset = com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(themeId)
                    // 无论如何先删存档（resetToThemeDefaults 原逻辑）
                    readerThemeConfigDao.deleteByThemeId(themeId)
                    if (preset != null) {
                        readerPrefsUtil.updatePreferences(preset.applyTo(cur))
                    } else {
                        // preset 缺失（极罕见，如旧版本残留 id）：仅清存档，不强写未知预设
                        Logger.w("MainReadViewModel::resetCurrentTheme preset not found for $themeId, only cleared archive")
                    }
                    // 三步必须在同一协程顺序执行：DataStore 已写入后才刷新，消除旧值竞态
                    refreshModifiedThemeIds()
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::resetCurrentTheme failed: ${e.message}", e)
                ToastUtil.show(R.string.theme_update_failed)
            }
        }
    }

    /**
     * AUTO 模式下系统明暗变化时，切到配对主题（需求 3）。
     * 仅当 mode==AUTO 且当前主题 isDark 与系统 isDark 不符时才切。
     * UI 层 LaunchedEffect(isSystemInDarkTheme()) 调用，debounce 由 UI 层处理。
     */
    fun applyAutoModeSwitch(systemIsDark: Boolean) {
        viewModelScope.launch {
            try {
                val cur = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launch
                if (cur.readerThemeMode != ReaderThemeMode.AUTO) return@launch

                val effectiveMode = if (isPerBookMode) {
                    _perBookMeta.value?.readerThemeMode?.let {
                        runCatching {
                            ReaderThemeMode.valueOf(it)
                        }.getOrNull()
                    } ?: cur.readerThemeMode
                } else cur.readerThemeMode
                if (effectiveMode != ReaderThemeMode.AUTO) return@launch

                if (isPerBookMode) {
                    // ★ v11 per-book：以 meta.selectedThemeId 为当前主题判断配对
                    val curThemeId = perBookThemeId ?: return@launch
                    val curEntry = ReaderThemePresets.getById(curThemeId) ?: return@launch
                    if (curEntry.preset.isDark == systemIsDark) return@launch
                    val paired = ReaderThemePresets.getPairedThemeId(curThemeId) ?: return@launch
                    switchTheme(paired)   // switchTheme 内部会再走 per-book 分支
                } else {
                    val curEntry = ReaderThemePresets.getById(cur.readerThemeId) ?: return@launch
                    if (curEntry.preset.isDark == systemIsDark) return@launch
                    val paired = ReaderThemePresets.getPairedThemeId(cur.readerThemeId ?: return@launch)
                    if (paired != null) switchTheme(paired)
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::applyAutoModeSwitch failed: ${e.message}", e)
            }
        }
    }

    /**
     * 刷新 * 号标识的【非活跃主题】部分（被微调过的非当前主题集合）。UI 在面板打开、切换主题、重置主题后调用。
     *
     * 写者职责分离（避免双写者竞态）：
     * - 【本函数负责非活跃主题】：用 Room 存档逐字段比对预设（[ReaderThemeConfigEntity.differsFrom]）。
     *   切走时 saveCurrent 无条件写入存档，但值等于预设的行会被 differsFrom 过滤，不再误判为已修改。
     * - 【活跃主题由 collector 实时派生】：见本类 init 中的 readerPrefsFlow 收集器，
     *   那里用实时 prefs 比对预设（Room 行可能缺失/过期，非真值源）。
     *
     * 本函数只替换集合中的非活跃部分，保留活跃主题的实时值，两条路径永不交叉。
     */
    fun refreshModifiedThemeIds() {
        viewModelScope.launch {
            try {
                val cur = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launch
                val bookId = currentBookId.value
                // P-BUG-2：per-book 模式用 perBookThemeId 作活跃主题口径（与 init collector L1213 一致）；
                // 全局模式用 cur.readerThemeId。原代码两处口径冲突导致活跃 per-book 主题被误判为非活跃。
                val activeId = if (isPerBookMode && bookId != null) perBookThemeId ?: cur.readerThemeId
                               else cur.readerThemeId
                // v12：per-book 模式读 per-book 快照表，全局模式读 Room 归档（原逻辑）
                val nonActive = if (isPerBookMode && bookId != null) {
                    // per-book：读 per_book_theme_overrides 快照，逐行 toReaderThemeConfigEntity().differsFrom(preset)
                    perBookThemeOverrideDao.observeByBookId(bookId).firstOrNull()
                        ?.asSequence()
                        ?.filter { it.themeId != activeId }
                        ?.filter { e ->
                            com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(e.themeId)
                                ?.let { e.toReaderThemeConfigEntity().differsFrom(it) } == true
                        }
                        ?.map { it.themeId }
                        ?.toSet()
                        ?: emptySet()
                } else {
                    // 全局：Room 存档逐字段比对预设
                    readerThemeConfigDao.getAll()
                        .asSequence()
                        .filter { it.themeId != activeId }
                        .filter { e ->
                            com.wxn.reader.ui.theme.ReaderThemePresets.getPresetById(e.themeId)
                                ?.let { e.differsFrom(it) } == true
                        }
                        .map { it.themeId }
                        .toSet()
                }
                // 只替换非活跃部分，保留活跃主题的实时值（由 collector 维护）
                _modifiedThemeIds.update { cur0 ->
                    cur0.filter { it == activeId }.toSet() + nonActive
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::refreshModifiedThemeIds failed: ${e.message}", e)
            }
        }
    }

    /**
     * ★ v12 per-book：开启/关闭"仅本书生效"开关（全量快照模式）。
     *
     * - 开启：兜底全局无主题时 bootstrap 到默认主题；selectedThemeId 同步当前全局 readerThemeId。
     *   **P-CONCUR-1**：先 saveSnapshot（冻结当前全局配置为 per-book 快照），再翻开关。
     *   顺序关键——若先翻开关，effective 流会切到 per-book 读 snapshot，但 snapshot 行还没建，
     *   走 preset 兜底 → 用户看到一瞬间 preset 默认值闪烁。先建行再翻开关，effective 流切换时直接读到快照。
     *   **P-CONCUR-3**：try-catch 回滚乐观更新（saveSnapshot 或 upsert 失败时恢复 [_perBookMeta]）。
     *   仅当首次 OFF→ON 且未展示过提示时触发 [_showPerBookTip]。
     * - 关闭：只翻开关，不删快照行（保留数据，下次开启可直接恢复）；乐观更新 [_perBookMeta]。
     *
     * @param bookId 当前书 id
     * @param enable true=开启 / false=关闭
     */
    fun togglePerBookOverride(bookId: Long, enable: Boolean) {
        viewModelScope.launch {
            val existing: PerBookMetaEntity? = perBookMetaDao.getByBookId(bookId)
            val now = System.currentTimeMillis()
            if (enable) {
                try {


                    // P-CRASH-1：firstOrNull 兜底（DataStore 冷启未 seed 时不抛 NoSuchElementException）
                    var currentTheme = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()?.readerThemeId
                    // 兜底：全局无主题时 bootstrap 到默认（复用 init 的默认选中逻辑）
                    if (currentTheme == null) {
                        currentTheme = com.wxn.reader.ui.theme.ReaderThemePresets.ID_DEFAULT
                        val g = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()
                            ?: ReaderPreferencesUtil.defaultPreferences
                        readerPrefsUtil.updatePreferences(g.copy(readerThemeId = currentTheme))
                    }
                    // P-CONCUR-1：先 saveSnapshot（此时 effective 流还没切 per-book，无闪烁）
                    // 首次开启时冻结当前全局配置为 per-book 快照；已有快照则保留（关再开数据保留语义）
                    val existingSnapshot = perBookThemeOverrideDao.getByBookIdAndTheme(bookId, currentTheme)
                    if (existingSnapshot == null) {
                        val global = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()
                            ?: ReaderPreferencesUtil.defaultPreferences
                        perBookConfigRepo.saveSnapshot(bookId, currentTheme, global)
                    }

                    val globalMode = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()?.readerThemeMode
                        ?: ReaderPreferencesUtil.defaultPreferences.readerThemeMode

                    // selectedThemeId 同步当前全局，避免开启瞬间主题跳变
                    val newMeta = existing?.copy(
                        overrideEnabled = true,
                        selectedThemeId = currentTheme,
                        readerThemeMode = existing.readerThemeMode ?: globalMode.name,   // 已存在则保留
                        updatedAt = now
                    ) ?: PerBookMetaEntity(
                        bookId = bookId,
                        overrideEnabled = true,
                        selectedThemeId = currentTheme,
                        readerThemeMode = globalMode.name,
                        createdAt = now,
                        updatedAt = now
                    )
                    // ★ 再翻开关（snapshot 行已就绪，effective 流切换时直接读到快照，无闪烁）
                    _perBookMeta.value = newMeta  // 乐观更新
                    perBookMetaDao.upsert(newMeta)
                    // 首次 OFF→ON 提示
                    if (!guidePrefUtil.isPerBookOverrideTipShown()) {
                        _showPerBookTip.value = true
                        guidePrefUtil.setPerBookOverrideTipShown()
                    }
                } catch (e: Exception) {
                    // P-CONCUR-3：回滚乐观更新
                    _perBookMeta.value = existing
                    Logger.e("MainReadViewModel::togglePerBookOverride enable failed: ${e.message}", e)
                    ToastUtil.show(R.string.theme_update_failed)
                }
            } else {
                // 关闭：只翻开关，不删快照（保留数据，下次开启可恢复）
                existing?.let {
                    val newMeta = it.copy(overrideEnabled = false, updatedAt = now)
                    try {
                        _perBookMeta.value = newMeta  // 乐观更新
                        perBookMetaDao.upsert(newMeta)
                        // 关闭后 effective 流切回全局，* 标记需按全局口径重算（移进 try：upsert 成功后才重算）
                        refreshModifiedThemeIds()
                    } catch (e: Exception) {
                        // P-UX-1：回滚乐观更新，避免 UI/DB 状态分裂（UI 显示 OFF，DB 仍 ON）
                        _perBookMeta.value = existing
                        Logger.e("MainReadViewModel::togglePerBookOverride disable failed: ${e.message}", e)
                        ToastUtil.show(R.string.theme_update_failed)
                    }
                }
            }
        }
    }

    /** ★ v11 per-book：用户看过首次提示后调用，关闭 Overlay。 */
    fun markPerBookTipShown() {
        _showPerBookTip.value = null
    }

    fun updateReaderBgImage(bgImagePath: String) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(backgroundImage = bgImagePath))
        } else {
            readerPrefsUtil.updateReaderBgImage(bgImagePath)
        }
    }

    fun resetReaderPreferences() {
        viewModelScope.launch {
            readerPrefsUtil.resetReaderPreferences()
        }
        BookApplication.app.topActivity?.let { act ->
            BrightnessHelper.restoreSystemBrightness(act)
            _brightness.value = BrightnessHelper.getSystemBrightnessSliderValue(
                act.contentResolver, fallback = 0.5f
            )
        }
    }

    fun updateLetterSpacing(letterSpacing: Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(letterSpacing = letterSpacing))
        } else {
            readerPrefsUtil.updateLetterSpacing(letterSpacing)
        }
    }

    fun updateTextColor(color: Int) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(textColor = color))
        } else {
            readerPrefsUtil.updateTextColor(color)
        }
    }

    fun updateLineHeight(lineHeight: Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(lineHeight = lineHeight))
        } else {
            readerPrefsUtil.updateLineHeight(lineHeight)
        }
    }

    fun updateFontSize(fontSize: Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(fontSize = fontSize))
        } else {
            readerPrefsUtil.updateFontSize(fontSize)
        }
    }

    fun updateLeftHandMode(leftHandMode: Boolean) {
        viewModelScope.launch {
            readerPrefsUtil.updateLeftHandMode(leftHandMode)
        }
    }

    fun updateClickAreaMode(clickAreaMode: Int) {
        viewModelScope.launch {
            readerPrefsUtil.updateClickAreaMode(clickAreaMode)
        }
    }

    fun updateAnimSpeed(animSpeed: Int) {
        viewModelScope.launch {
            readerPrefsUtil.updateAnimSpeed(animSpeed)
        }
    }

    fun updateScrollType(scrollType: Int) {
        viewModelScope.launch {
            readerPrefsUtil.updateScrollType(scrollType)
        }
    }

    /**
     * 双列显示开关（全局阅读设置，不进 per-book override，S2 决策）。
     *
     * 走简单全局模式（参照 [updateScrollType]），直接写 DataStore，不经过 perBookConfigRepo.saveSnapshot。
     * per-book 模式下双列也写全局——切换书籍时 per-book restore 只覆盖那 17 个枚举字段，
     * 双列状态不被 per-book 覆盖，保持全局一致（这正是「全局阅读设置」的语义）。
     *
     * [isDualColumnSwitching] 防重入：避免快速连点导致多次 upStyle 重排竞态
     * （命名对齐既有 [isBrightnessCommitting] / [isThemeSwitching]）。
     */
    @Volatile
    private var isDualColumnSwitching: Boolean = false

    fun updateDualColumn(enabled: Boolean) {
        if (isDualColumnSwitching) return
        isDualColumnSwitching = true
        viewModelScope.launch {
            try {
                readerPrefsUtil.updateDualColumn(enabled)
            } finally {
                isDualColumnSwitching = false
            }
        }
    }

    fun updateVolumeKeyPageTurning(isEnable: Boolean) {
        viewModelScope.launch {
            readerPrefsUtil.updateVolumeKeyPageTurning(isEnable)
        }
    }

    fun updateKeepScreenOn(isKeepScreenOn:Boolean) {
        viewModelScope.launch {
            readerPrefsUtil.updateKeepScreenOn(isKeepScreenOn)
        }
    }

    fun updatePageHorizontalMargins(margin:Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(pageHorizontalMargins = margin))
        } else {
            readerPrefsUtil.updatePageHorizontalMargins(margin)
        }
    }

    fun updatePageVerticalMargins(margin:Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(pageVerticalMargins = margin))
        } else {
            readerPrefsUtil.updatePageVerticalMargins(margin)
        }
    }

    fun updateBgColorWithNonImage(color:Int) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            // 非图片背景：backgroundColor 设色 + 清空 backgroundImage（两字段同 copy，单次 saveSnapshot 原子写）
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(backgroundColor = color, backgroundImage = ""))
        } else {
            readerPrefsUtil.updateBgColorWithNonImage(color)
        }
    }

    fun updateParagraphSpacing(spacing: Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(paragraphSpacing = spacing))
        } else {
            readerPrefsUtil.updateParagraphSpacing(spacing)
        }
    }

    fun updateParagraphIndent(indent:Double) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(paragraphIndent = indent))
        } else {
            readerPrefsUtil.updateParagraphIndent(indent)
        }
    }

    /**
     * 原子更新文本对齐设置（单次批量写 → 单次 Flow emit → 单次重排版）。
     * v12 per-book：两字段同 copy + 单次 saveSnapshot（整行替换天然原子，无需 @Transaction）。
     * @param forceOverride 是否强制覆盖书籍 CSS 对齐样式
     * @param align 用户对齐偏好: 1=Left, 2=Right, 3=Center, 4=Justify
     */
    fun updateTextAlign(forceOverride: Boolean, align: Int) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(forceAlignOverride = forceOverride, userTextAlign = align))
        } else {
            val cur = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull() ?: return@launchPerBookWrite
            readerPrefsUtil.updatePreferences(cur.copy(forceAlignOverride = forceOverride, userTextAlign = align))
        }
    }

    /**
     * ★ P-BUG-1：updateBgColor 也感知 per-book（原仅 updateBgColorWithNonImage 感知）。
     * 取色器 else 兜底分支调此函数，per-book 模式下必须写 snapshot 否则被遮蔽。
     * 区别于 [updateBgColorWithNonImage]：本函数只改色，不清 backgroundImage（语义：纯改色）。
     */
    fun updateBgColor(color: Int) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(backgroundColor = color))
        } else {
            readerPrefsUtil.updateBgColor(color)
        }
    }

    fun updateColorHistory(colorHistory: List<Color>) {
        viewModelScope.launch {
            readerPrefsUtil.updateColorHistory(colorHistory)
        }
    }

    fun selectSystemFont(name: String) = launchPerBookWrite {
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(font = name, fontVariant = "regular"))
        } else {
            readerPrefsUtil.updateFontPrefs(name, "regular")
        }
    }

    fun selectDownloadedFont(localDir: String, variant: String) = launchPerBookWrite {
        Logger.i("MainReadViewModel::selectDownloadedFont:localDir=$localDir, variant=$variant")
        val bookId = currentBookId.value ?: return@launchPerBookWrite
        val themeId = perBookThemeId
        if (isPerBookMode && themeId != null) {
            val base = currentEffectivePrefs(bookId, themeId)
            perBookConfigRepo.saveSnapshot(bookId, themeId, base.copy(font = localDir, fontVariant = variant))
        } else {
            readerPrefsUtil.updateFontPrefs(localDir, variant)
        }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    fun importFontFile(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = importFontUseCase.importFontFile(uri)
            _isImporting.value = false
            val msg = if (result.success) {
                context.getString(R.string.font_import_success, result.importedFamilies.joinToString())
            } else {
                context.getString(R.string.font_import_failed, result.errorMessage ?: "")
            }
            ToastUtil.show(msg)
        }
    }

    fun importFontDirectory(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = importFontUseCase.importFontDirectory(uri)
            _isImporting.value = false
            val msg = if (result.success) {
                context.getString(R.string.font_import_success, result.importedFamilies.joinToString())
            } else {
                context.getString(R.string.font_import_failed, result.errorMessage ?: "")
            }
            ToastUtil.show(msg)
        }
    }

    fun clearClickedLinkContent() {
        _clickedLinkContent.value = null
    }

    fun cancelTextSelected() {
        Logger.i("MainReaderViewModel::cancelTextSelected")
        pageController.cancelTextSelected()
    }

    fun showTranslatePanel() {
        val text = selectedLocator?.text ?: return

        textToolbarOpen(false)

        _translateSelectedText.value = text
        _translatedText.value = null
        _translateStatus.value = TranslateStatus.IDEL

        val lastTragetLang = _translatorPrefs.value.lastTargetTransilateLang
        Logger.d("MainReadViewModel::showTranslatePanel::targetLang=$lastTragetLang")
        _targetLang.value = lastTragetLang.ifBlank { "en" }

        _showTranslatePanel.value = true
        _showDictionaryPanel.value = false

        if (_supportedLanguages.value.isEmpty() && !_isLoadingLanguages) {
            loadSupportedLanguages()
        }
    }

    fun hideTranslatePanel() {
        // 不在此主动取消；_showTranslatePanel=false 会触发 ReaderView 的 DisposableEffect.onDispose → cancelTranslateRequest()
        _translateStatus.value = TranslateStatus.IDEL
        _translatedText.value = null
        _showTranslatePanel.value = false
    }

    fun hideTranslatePanelAndShowToolbar() {
        // 请求进行中收起：只关面板+显示工具栏，不重置 status(由 onDispose 兜底取消)
        _showTranslatePanel.value = false
        textToolbarOpen(true)
    }

    fun updateTargetLang(lang: String) {
        Logger.i("MainReadViewModel::updateTragetLang:$lang")
        _targetLang.value = lang
        viewModelScope.launch {
            translatorPrefsUtil.updateTargetLang(lang)
        }
    }

    private fun loadSupportedLanguages() {
        _isLoadingLanguages = true
        viewModelScope.launch {
            try {
                val languages = translateRepository.getSupportedLanguages()
                _supportedLanguages.value = languages
            } catch (e: Exception) {
                Logger.e("TranslatePanel: Failed to load languages - ${e.message}")
            } finally {
                _isLoadingLanguages = false
            }
        }
    }

    fun translate() {
        val text = _translateSelectedText.value
        if (text.isBlank() || _translateStatus.value == TranslateStatus.TRANSLATING) return

        val targeLang = _targetLang.value
        var sourceLang = LanguageDetector.detectLanguage(text)
        if (sourceLang.isNullOrEmpty()) {
            sourceLang = _book.value?.language
        }
        if (sourceLang.isNullOrEmpty()) {
            sourceLang = "en"
        }
        if (targeLang.isEmpty()) {
            ToastUtil.show(context.getString(R.string.select_target_lang))
        }

        _translateStatus.value = TranslateStatus.TRANSLATING
        _translatedText.value = null

        translateJob?.cancel()
        val myRequestId = translateRequestId.incrementAndGet()
        translateJob = viewModelScope.launch {
            translateRepository.translate(text, targeLang, sourceLang)
                .onSuccess { response ->
                    if (myRequestId != translateRequestId.get()) return@onSuccess
                    _translateStatus.value = TranslateStatus.TRANSLATED
                    response.data?.translatedText?.let {
                        _translatedText.value = it
                    }
                }.onFailure {
                    if (myRequestId != translateRequestId.get()) return@onFailure
                    _translateStatus.value = TranslateStatus.ERROR
                    ToastUtil.show(TranslateErrorMessage.getMessage(context, it))
                }
        }
    }

    /**
     * 取消翻译网络请求。幂等：自增 requestId 使所有在途回调立即过期，并取消当前 Job。
     * 由 [ReaderView] 的 DisposableEffect.onDispose 在面板离开组合时调用。
     */
    internal fun cancelTranslateRequest() {
        translateRequestId.incrementAndGet()
        translateJob?.cancel()
        translateJob = null
    }

    fun onTranslateClicked() {
        if (_showTranslatePicker.value) return
        val text = selectedLocator?.text ?: return
        val lastTranslator = _translatorPrefs.value.lastSelectedTranslator

        if (lastTranslator.isNotBlank()) {
            if (TranslatorHelper.isAppAvailable(context, lastTranslator)) {
                executeTranslation(lastTranslator, text)
                return
            } else {
                viewModelScope.launch {
                    translatorPrefsUtil.updateLastTranslator("")
                }
            }
        }

        loadTranslatorItems()
        _showTranslatePicker.value = true
        textToolbarOpen(false)
    }

    fun onTranslatorConfirmed(translatorId: String, setAsDefault: Boolean) {
        _showTranslatePicker.value = false
        val text = selectedLocator?.text
        if (text == null) {
            textToolbarOpen(true)
            return
        }
        viewModelScope.launch {
            if (setAsDefault) {
                translatorPrefsUtil.updateLastTranslator(translatorId)
            } else {
                translatorPrefsUtil.updateLastTranslator("")
            }
        }
        executeTranslation(translatorId, text)
    }

    fun hideTranslatePicker() {
        _showTranslatePicker.value = false
        textToolbarOpen(true)
    }

    private fun resolveDictionaryLang(text: String): String {
        preferredDictLang?.let { return it }
        var lang = LanguageDetector.detectLanguage(text)
        if (lang.isNullOrEmpty()) lang = _book.value?.language
        if (lang.isNullOrEmpty()) lang = "en"
        return lang
    }

    fun onDictionaryClicked() {
        if (_showDictionaryPicker.value) return
        val text = selectedLocator?.text ?: return

        val defaultApp = _dictionaryPrefs.value.defaultLookupApp
        if (defaultApp.isNotBlank()) {
            if (DictionaryHelper.isAppAvailable(context, defaultApp)) {
                executeDictionaryLookup(defaultApp, text)
                return
            } else {
                viewModelScope.launch {
                    dictionaryPrefsUtil.updateDefaultLookupApp("")
                }
            }
        }

        loadDictionaryItems()
        _showDictionaryPicker.value = true
        textToolbarOpen(false)
    }

    private fun executeDictionaryLookup(dictId: String, text: String) {
        if (dictId == Constants.BUILT_IN_DICTIONARY) {
            showBuiltInDictionaryPanel(text)
        } else {
            textToolbarOpen(false)
            DictionaryHelper.sendTextToDictAppById(context, dictId, text)
        }
    }

    private fun showBuiltInDictionaryPanel(text: String) {
        clearDictionaryHistory()

        _dictionaryWord.value = text.trim().take(200)
        _dictionaryResult.value = null
        _dictionaryStatus.value = DictionaryStatus.IDLE

        val lang = resolveDictionaryLang(text)
        _dictionaryLang.value = lang

        historyList.add(DictionaryHistoryEntry(_dictionaryWord.value, lang))
        historyIndex = 0
        updateNavigationState()

        textToolbarOpen(false)
        _showTranslatePanel.value = false
        _showDictionaryPanel.value = true
    }

    private fun loadDictionaryItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val builtInItem = TranslatorItem(
                    id = Constants.BUILT_IN_DICTIONARY,
                    name = context.getString(R.string.built_in_dictionary_name),
                    subtitle = context.getString(R.string.built_in_dictionary_subtitle),
                    isBuiltIn = true
                )
                val thirdPartyItems = DictionaryHelper.getInstalledDictionaryItems(context)
                withContext(Dispatchers.Main) {
                    _dictionaryItems.value = listOf(builtInItem) + thirdPartyItems
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::loadDictionaryItems error: ${e.message}")
            }
        }
    }

    fun onDictionaryPickerConfirmed(dictId: String, setAsDefault: Boolean) {
        _showDictionaryPicker.value = false
        val text = selectedLocator?.text
        if (text == null) {
            textToolbarOpen(true)
            return
        }
        viewModelScope.launch {
            if (setAsDefault) {
                dictionaryPrefsUtil.updateDefaultLookupApp(dictId)
            } else {
                dictionaryPrefsUtil.updateDefaultLookupApp("")
            }
        }
        executeDictionaryLookup(dictId, text)
    }

    fun hideDictionaryPicker() {
        _showDictionaryPicker.value = false
        textToolbarOpen(true)
    }

    fun showDictionaryPickerForSettings() {
        loadDictionaryItems()
    }

    fun updateDefaultDictionary(dictId: String) {
        viewModelScope.launch {
            dictionaryPrefsUtil.updateDefaultLookupApp(dictId)
        }
    }

    fun clearDefaultDictionary() {
        viewModelScope.launch {
            dictionaryPrefsUtil.updateDefaultLookupApp("")
        }
    }

    fun lookupWord(saveToLookupHistory: Boolean = true) {
        val word = _dictionaryWord.value
        if (word.isBlank()) return

        _dictionaryStatus.value = DictionaryStatus.LOADING
        _dictionaryResult.value = null

        val lang = _dictionaryLang.value
        val queryWord = if (lang == "zh") {
            ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(word)
        } else {
            word
        }

        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val myRequestId = lookupRequestId.incrementAndGet()
            val converterType = appPrefsUtil.chineseConverterType()
            dictionaryRepository.lookup(queryWord, lang)
                .onSuccess { result ->
                    if (myRequestId != lookupRequestId.get()) return@onSuccess
                    var finalResult = result
                    if (converterType == 0) {
                        finalResult = finalResult.filterChineseDefinitions()
                    }
                    if (lang == "zh" && converterType == 2) {
                        val toTraditional = ZHConverter.getInstance(ZHConverter.TRADITIONAL)
                        finalResult = finalResult.convertChinese { toTraditional.convert(it) }
                    }
                    _dictionaryResult.value = finalResult
                    _dictionaryStatus.value = if (finalResult.hasResult) DictionaryStatus.SUCCESS else DictionaryStatus.NOT_FOUND

                    if (saveToLookupHistory && finalResult.hasResult) {
                        val bookId = _currentBookId.value
                        val locator = selectedLocator
                        val sentence = pendingSentenceText
                        if (bookId != null && locator != null && sentence != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    vocabularyRepository.saveEntry(bookId, queryWord, lang, locator, sentence)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                .onFailure {
                    if (myRequestId != lookupRequestId.get()) return@onFailure
                    _dictionaryStatus.value = DictionaryStatus.ERROR
                    ToastUtil.show(DictionaryErrorMessage.getMessage(context, it))
                }
        }
    }

    fun onDictionaryLangChange(lang: String) {
        if (historyIndex >= 0 && historyIndex < historyList.size) {
            historyList[historyIndex] = historyList[historyIndex].copy(lang = lang)
        }
        _dictionaryLang.value = lang
        viewModelScope.launch {
            dictionaryPrefsUtil.updateLastDictLang(lang)
        }
        lookupWord(saveToLookupHistory = false)
    }

    fun lookupAnotherWord(word: String) {
        val trimmedWord = word.trim().take(200)
        if (trimmedWord == _dictionaryWord.value) return

        if (historyIndex < historyList.size - 1) {
            historyList = ArrayList(historyList.subList(0, historyIndex + 1))
        }

        _dictionaryWord.value = trimmedWord
        _dictionaryResult.value = null
        _dictionaryStatus.value = DictionaryStatus.IDLE

        val lang = resolveDictionaryLang(word)
        _dictionaryLang.value = lang

        historyList.add(DictionaryHistoryEntry(trimmedWord, lang))
        historyIndex = historyList.size - 1
        updateNavigationState()

        lookupWord(saveToLookupHistory = false)
    }

    fun retryDictionaryLookup() {
        lookupWord(saveToLookupHistory = false)
    }

    private fun updateNavigationState() {
        _canGoBack.value = historyIndex > 0
        _canGoForward.value = historyIndex < historyList.size - 1
    }

    private fun clearDictionaryHistory() {
        historyList.clear()
        historyIndex = -1
        updateNavigationState()
    }

    fun goBack() {
        if (historyIndex <= 0) return
        cancelDictionaryRequest()
        historyIndex--
        restoreFromHistory(historyList[historyIndex])
    }

    fun goForward() {
        if (historyIndex >= historyList.size - 1) return
        cancelDictionaryRequest()
        historyIndex++
        restoreFromHistory(historyList[historyIndex])
    }

    private fun restoreFromHistory(entry: DictionaryHistoryEntry) {
        _dictionaryWord.value = entry.word
        _dictionaryLang.value = entry.lang
        _dictionaryResult.value = null
        _dictionaryStatus.value = DictionaryStatus.IDLE
        updateNavigationState()
        lookupWord(saveToLookupHistory = false)
    }

    /**
     * 取消词典网络请求。幂等：自增 requestId 使所有在途回调立即过期，并取消当前 lookupJob。
     * 由 [ReaderView] 的 DisposableEffect.onDispose 在面板离开组合时调用。
     */
    internal fun cancelDictionaryRequest() {
        lookupRequestId.incrementAndGet()
        lookupJob?.cancel()
        lookupJob = null
    }

    fun hideDictionaryPanel() {
        // 不在此主动取消；_showDictionaryPanel=false 会触发 ReaderView 的 DisposableEffect.onDispose → cancelDictionaryRequest()
        _dictionaryStatus.value = DictionaryStatus.IDLE
        _dictionaryResult.value = null
        _showDictionaryPanel.value = false
        clearDictionaryHistory()
        cancelTextSelected()
        pendingSentenceText = null
    }

    fun hideDictionaryPanelAndShowToolbar() {
        // 请求进行中收起：只关面板+显示工具栏，不重置 status/result(由 onDispose 兜底取消)
        _showDictionaryPanel.value = false
        textToolbarOpen(true)
    }

    private fun extractSentenceText(locator: Locator): String {
        val textChapter = pageController.textChapter(0) ?: return locator.text
        val readerTexts = textChapter.readerTexts
        val paragraphIndex = locator.startParagraphIndex
        if (paragraphIndex !in readerTexts.indices) return locator.text

        val paragraphText = when (val rt = readerTexts[paragraphIndex]) {
            is ReaderText.Text -> rt.line
            is ReaderText.Chapter -> rt.title
            else -> return locator.text
        }
        if (paragraphText.isEmpty()) return locator.text

        val startOffset = locator.startTextOffset.coerceIn(0, paragraphText.length)
        val endOffset = locator.endTextOffset.coerceIn(0, paragraphText.length)

        val sentenceStart = findSentenceStart(paragraphText, startOffset)
        val sentenceEnd = findSentenceEnd(paragraphText, endOffset)

        if (sentenceStart >= sentenceEnd || sentenceEnd > paragraphText.length) return locator.text
        return paragraphText.substring(sentenceStart, sentenceEnd).trim()
    }

    private fun findSentenceStart(text: String, fromOffset: Int): Int {
        val delimiters = charArrayOf('.', '!', '?', '\u3002', '\uff01', '\uff1f', ';', '\uff1b', '\n')
        for (i in (fromOffset - 1) downTo 0) {
            if (text[i] in delimiters) return i + 1
        }
        return 0
    }

    private fun findSentenceEnd(text: String, fromOffset: Int): Int {
        val delimiters = charArrayOf('.', '!', '?', '\u3002', '\uff01', '\uff1f', ';', '\uff1b', '\n')
        for (i in fromOffset until text.length) {
            if (text[i] in delimiters) return minOf(i + 1, text.length)
        }
        return text.length
    }

    fun showTranslatePickerForSettings() {
        loadTranslatorItems()
    }

    fun updateDefaultTranslator(translatorId: String) {
        viewModelScope.launch {
            translatorPrefsUtil.updateLastTranslator(translatorId)
        }
    }

    fun clearDefaultTranslator() {
        viewModelScope.launch {
            translatorPrefsUtil.updateLastTranslator("")
        }
    }

    private fun executeTranslation(translatorId: String, text: String) {
        if (translatorId == Constants.AI_TRANSILATOR) {
            showTranslatePanel()
        } else {
            textToolbarOpen(false)
            TranslatorHelper.sendTextToAppById(context, translatorId, text)
        }
    }

    private fun loadTranslatorItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val builtInItem = TranslatorItem(
                    id = Constants.AI_TRANSILATOR,
                    name = context.getString(R.string.ai_translator_name),
                    subtitle = context.getString(R.string.ai_translator_subtitle),
                    isBuiltIn = true
                )
                val thirdPartyItems = TranslatorHelper.getInstalledTranslatorItems(context)
                withContext(Dispatchers.Main) {
                    _translatorItems.value = listOf(builtInItem) + thirdPartyItems
                }
            } catch (e: Exception) {
                Logger.e("MainReadViewModel::loadTranslatorItems error: ${e.message}")
            }
        }
    }

    fun navigateTo(locatorInfo: Locator?) {
        Logger.i("MainReadViewModel:navigateTo:$locatorInfo")
        locatorInfo ?: return
        val chapterIndex = locatorInfo.chapterIndex
        pageController.changeChapterAndPage(chapterIndex, locatorInfo)
    }

    val selectedTextForSearch: String?
        get() = selectedLocator?.text

    fun getChapterName(chapterIndex: Int): String {
        return allChapters.firstOrNull { it.chapterIndex == chapterIndex }?.chapterName ?: ""
    }

    fun startSearch(query: String) {
        Logger.i("MainReadViewModel:startSearch")
        pageController.clearSearchHighlights()
        if (query.trim().length < 2) {
            ToastUtil.show(R.string.search_select_longer_text)
            return
        }
        val currentBook = book.value ?: return
        val chapters = allChapters.toList()
        if (chapters.isEmpty()) return

        _returnLocator.value = null
        _returnChapterName.value = ""
        _searchQuery.value = query
        _searchProgress.value = SearchProgress(
            searchedChapters = 0,
            totalChapters = chapters.size,
        )
        _searchSheetState.value = SearchSheetState.EXPANDED

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            searchInBookUseCase.search(
                context = context,
                book = currentBook,
                chapters = chapters,
                query = query,
                textParser = textParser,
                appPrefsUtil = appPrefsUtil,
                onProgress = { progress ->
                    _searchProgress.value = progress
                },
                isActive = { _searchSheetState.value != SearchSheetState.HIDDEN },
            )
        }
    }

    fun minimizeSearchSheet() {
        Logger.i("MainReadViewModel:minimizeSearchSheet")
        _searchSheetState.value = SearchSheetState.MINIMIZED
    }

    fun expandSearchSheet() {
        Logger.i("MainReadViewModel:expandSearchSheet")
        _searchSheetState.value = SearchSheetState.EXPANDED
    }

    /***
     * 关闭搜索弹窗面板
     */
    fun closeSearchSheet() {
        Logger.i("MainReadViewModel:closeSearchSheet")
        pageController.clearSearchHighlights()
        searchJob?.cancel()
        searchJob = null
        _searchSheetState.value = SearchSheetState.HIDDEN
        _searchProgress.value = SearchProgress()
        _searchQuery.value = ""
        _returnLocator.value = null
        _returnChapterName.value = ""
    }

    fun markSearchFabGuideShown() {
        _showSearchFabGuide.value = false
        viewModelScope.launch {
            guidePrefUtil.setSearchFabGuideShown()
        }
    }

    /****
     * 书籍内搜索功能，
     * 点击某一条搜索条目，导航到该条目
     */
    fun navigateToSearchResult(result: SearchResultItem) {
        if (isNavigatingToResult) return
        isNavigatingToResult = true

        if (_returnLocator.value == null) {
            val currentLocator = pageController.getSelectionLocator()
                ?: pageController.getCurrentLocator()
            if (currentLocator != null) {
                _returnLocator.value = currentLocator
                _returnChapterName.value = getChapterName(currentLocator.chapterIndex)
            }
        }

        val highlightLocators = result.highlightLocators
            .takeIf { it.isNotEmpty() }
            ?: listOf(result.locator)
        pageController.addSearchHighlight(highlightLocators)

        _searchSheetState.value = SearchSheetState.MINIMIZED
        navigateTo(result.locator)

        viewModelScope.launch {
            delay(300)
            isNavigatingToResult = false
        }
    }


    /****
     * 书籍内搜索，点击返回源搜索位置
     */
    fun navigateBackToReturnLocator() {
        if (isNavigatingToResult) return
        val target = _returnLocator.value ?: return
//        _returnLocator.value = null
//        _returnChapterName.value = ""

        // 统一显示逻辑：用搜索高亮（黄色标记），与 navigateToSearchResult 行为一致
        // 跨段落选区降级处理（drawSearchResultsBg 只匹配 startParagraphIndex，不支持段落范围）
        if (target.startParagraphIndex == target.endParagraphIndex) {
            pageController.addSearchHighlight(listOf(target))
        }

        // 清除 selection 状态（连续模式 cancelTextSelected 不级联 onCancelSelect，需显式调用）
        textToolbarOpen(false)
        pageController.onCancelSelect()
        cancelTextSelected()

        // 清除选中状态数据，保持与视觉一致（onCancelSelect 不清除这两个字段）
        selectedLocator = null
        pendingSentenceText = null

        _searchSheetState.value = SearchSheetState.MINIMIZED
        navigateTo(target)
    }

    fun resumeTtsPlaying() {
        Logger.i("MainReadViewModel:resumeTtsPlaying")
        if (pageController.resumeTtsPlaying()) {
            /* do nothing */
        } else {
            Logger.d("MainReadViewModel:resumeTtsPlaying failed, try reset ttsConfig and play again.")
            ttsPlay()
        }
    }

    fun pauseTtsPlaying() {
        pageController.pauseTtsPlaying()
    }

    fun dismissBatteryOptimizationDialog() {
        _showBatteryOptimizationDialog.value = false
    }

    fun onBatteryOptimizationConfirm() {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
    }

    fun onBatteryOptimizationSkip() {
        _showBatteryOptimizationDialog.value = false
    }

    fun onBatteryOptimizationNeverShowAgain() {
        viewModelScope.launchIO {
            batteryOptimazePrefsUtil.disableBatteryOptimaze()
        }
    }

    private fun ttsPlay() {
        setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowPlayer) //显示TtsPlayer面板
        _showMenu.value = false  //隐藏其他菜单

        Logger.i("MainReadViewModel::ttsPlay")
        _ttsPlayStatus.value = TtsPlaybackStatus.PENDING_PLAYING
        pageController.startTtsService()
        viewModelScope.launchIO {
            _book.value?.let { book ->
                pageController.setTtsBookInfo(book)
            }
            val ttsConfig = getTtsConfig()

            //防止定时器,暂停/或者修改其他配置时被重置了
            val currentPlayTime = _ttsPlayTimes.value
            if (currentPlayTime > 0) {
                pageController.setTtsTimer(currentPlayTime)
            }

            pageController.readPageNew(ttsConfig) { status ->
                if (status == StartTtsFinishedStatus.EngineInitSuccess) {
                    /* do nothing*/
                } else if (status == StartTtsFinishedStatus.EngineFailByNeedModel) {
                    _ttsPlayStatus.value = TtsPlaybackStatus.PAUSED
                    setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowModelSelect)  //但是没有配置model,需要显示设置模型项
                } else if (status == StartTtsFinishedStatus.PlayStopFail) {
                    //当其开始新的播放,中间会停止掉就的播放,所以这里不需要重置状态
                    if (_ttsPlayStatus.value != TtsPlaybackStatus.PENDING_PLAYING) {
                        _ttsPlayStatus.value = TtsPlaybackStatus.PAUSED
                    }
                } else {        //失败的情况
                    _ttsPlayStatus.value = TtsPlaybackStatus.PAUSED
                    // 显示TTS初始化失败提示
                    viewModelScope.launchMain {
                        ToastUtil.show(R.string.tts_init_failed)
                    }
                }
            }
        }
    }

    override fun onTtsPlayStatus(ttsPlayStatus: TtsPlaybackStatus) {
        _ttsPlayStatus.value = ttsPlayStatus
        Logger.d("MainReadViewModel::onTtsPlayStatus:$ttsPlayStatus")

        if (ttsPlayStatus == TtsPlaybackStatus.PLAYING) {
            //开始播放时, 尝试弹窗提示后台运行优化
            viewModelScope.launchIO {
                if (batteryOptimazePrefsUtil.enableShowToday() &&
                    !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                    _showBatteryOptimizationDialog.value = true
                    //一天只显示一次, 显示了就需要更新显示的时间
                    batteryOptimazePrefsUtil.updateShowDay()
                }
            }
        }
    }

    override fun showTimerExpired() {
        _ttsPlayTimes.value = 0.0f  //重置定时器
        _showTimerExpired.value = true
    }

    private suspend fun getTtsConfig(): TtsConfig {
        val ttsPref = ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()

        val engineType =
            if ((ttsPref?.ttsEngineType ?: TTSEngineType.SYSTEM) == TTSEngineType.SYSTEM) 0 else 1

        var speed = ttsPref?.speed ?: 1.0f
        var pitch = ttsPref?.pitch ?: 1.0f

        speed = speed.coerceIn(TtsNavigator.TTS_MIN_SPEED, TtsNavigator.TTS_MAX_SPEED)
        pitch = pitch.coerceIn(TtsNavigator.TTS_MIN_PITCH, TtsNavigator.TTS_MAX_PITCH)

        val lang = ttsPref?.localeCode ?: _appPreferences.value?.language ?: "en"
        if (speed != _ttsSpeed.value) {
            _ttsSpeed.value = speed
        }
        if (pitch != _ttsPitch.value) {
            _ttsPitch.value = pitch
        }

        if (engineType == 0) {
            if (lang != _ttsLanguage.value?.code) {
                _ttsLanguage.value = LanguageInfo.fromCode(lang)
            }
        }

        val engineModel = ttsPref?.selectedTTSModel.orEmpty()
        val speaker = ttsPref?.selectedSpeaker

        var modelType = ""
        var modelLocale = ""
        var speakerNum = 0
        var modelDir = ""
        val baseDataTriples = mutableListOf<Triple<String, String, String>>()
        if (engineType == 1 && engineModel.isNotEmpty()) {
            ttsModelsRepository.getLocalModelByName(engineModel)?.let { modelData ->
                modelType = modelData.type
                modelLocale = modelData.locale
                speakerNum = modelData.speakers_num
                modelData.name

                val baseDatas = modelData.base.orEmpty()
                if (baseDatas.isNotEmpty()) {
                    for (baseData in baseDatas) {
                        val fileId = baseData.name
                        val url = baseData.url
                        var localPath = ""
                        val file = File(
                            PathUtil.getDownloadDir(context, DownloadFileType.TTS_DEPENDENCY),
                            fileId
                        )
                        if (file.exists() && file.canRead() && file.isDirectory) {
                            localPath = file.absolutePath
                        }

                        if (localPath.isEmpty()) {
                            var fileName = fileId
                            if (!fileName.endsWith(".onnx")) {
                                fileName = "$fileName.onnx"
                            }
                            val file = File(
                                PathUtil.getDownloadDir(context, DownloadFileType.TTS_DEPENDENCY),
                                fileName
                            )
                            if (file.exists() && file.canRead() && file.isFile) {
                                localPath = file.absolutePath
                            }
                        }

                        if (localPath.isNotEmpty() && url.isNotEmpty() && fileId.isNotEmpty()) {
                            baseDataTriples.add(Triple(fileId, url, localPath))
                        } else {
                            Logger.e("MainReadViewModel:getTtsConfig:error:baseData:fileId=$file,url=$url,localPath=$localPath")
                        }
                    }
                }
            }

            val modelDirName = "$modelType-$modelLocale-$engineModel"
            val engineModelDir = File(PathUtil.getDownloadDir(context, DownloadFileType.TTS_MODEL), modelDirName)
            if (!engineModelDir.exists()) {
                val parent = PathUtil.getDownloadDir(context, DownloadFileType.TTS_MODEL)
                val fileList = parent.listFiles()
                if (!fileList.isNullOrEmpty()) {
                    for (file in fileList) {
                        if (file.name.contains(engineModel)
                            && file.isDirectory
                            && file.name.startsWith(modelType)
                        ) {
                            modelDir = file.absolutePath
                            break
                        }
                    }
                }
            } else {
                modelDir = engineModelDir.absolutePath
            }
        }

        return TtsConfig(
            engineType = engineType,
            engineModel = engineModel,
            modelType = modelType,
            modelDir = modelDir,
            baseDatas = baseDataTriples,
            speakerNum = speakerNum,

            speaker = speaker ?: 0,
            speed = speed,
            pitch = pitch,
            language = if (engineType == 1 && engineModel.isNotEmpty()) modelLocale else lang
        )
    }

    /****
     * 标题栏的TTS开关按钮, 触发权限申请或者不需要权限审查,都会走此方法
     */
    fun toggleTts() {
        val isPlayging = (_ttsPlayStatus.value == TtsPlaybackStatus.PLAYING)
        Logger.i("MainReadViewModel:toggleTts:isPlayging=$isPlayging,_ttsPlayStatus.value=${_ttsPlayStatus.value}")
        if (!isPlayging) {
            if (pageController.currentPage()?.text.isNullOrEmpty()) {
                ToastUtil.show(R.string.no_text_to_speech_on_current_page)
            } else {
                ttsPlay()
            }
        } else {
            pageController.stopTts()
            setTtsPanelStatus(TtsPlayerPanelStatus.PanelClose)
        }
    }

    fun stopTts() {
        pageController.stopTts()
        setTtsPanelStatus(TtsPlayerPanelStatus.PanelClose)
        _showMenu.value = true
    }

    fun hideOutHrefDialog() {
        _outHref.value = ""
        _showOutHrefDialog.value = false
    }

    /***
     * 检查是否需要显示阅读引导页
     * 如果用户从未看过引导页，则显示
     */
    fun checkAndShowReaderGuide() {
        viewModelScope.launch {
            val prefs = appPrefsUtil.appPrefsFlow.firstOrNull()
            if (prefs != null && !prefs.isReaderGuideShown) {
                _showReaderGuide.value = true
            }

            val readerPrefs = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()
            if (readerPrefs != null) {
                _leftHandMode.value = readerPrefs.leftHandedMode
            }
        }
    }

    /***
     * 标记阅读引导页已显示
     * 点击引导页后调用此方法
     */
    fun markReaderGuideShown() {
        _showReaderGuide.value = false
        viewModelScope.launch {
            val prefs = appPrefsUtil.appPrefsFlow.firstOrNull()
            if (prefs != null) {
                appPrefsUtil.updateAppPreferences(prefs.copy(isReaderGuideShown = true))
            }
        }
    }

    fun markTimerLayerDismiss() {
        _showTimerExpired.value = false
    }

    fun showClickAreaMode(mode: Int) {
        _showClickAreaMode.value = mode
    }

    fun showClickAreaMode(mode: Int, isLeftHandMode: Boolean) {
        _showClickAreaMode.value = mode
        _leftHandMode.value = isLeftHandMode
    }

    fun setTtsSpeed(speed: Float) {
        pageController.setTtsSpeed(speed)
    }

    fun setTtsPitch(pitch: Float) {
        pageController.setTtsPitch(pitch)
    }

    fun setTtsLanguage(language: LanguageInfo) {
        pageController.setTtsLanguage(language)
    }

    fun setTtsSpeakerIndex(index: Int) {
        pageController.setSpeakerIndex(index)
    }

    fun setTtsPlayTime(duration: Float) {
        pageController.setTtsTimer(duration)
        _ttsPlayTimes.value = duration
    }

    fun skipToNextUtterance() {
        pageController.skipToNextUtterance()
    }

    fun skipToPreviousUtterance() {
        pageController.skipToPreviousUtterance()
    }

    fun getSupportedLanguages(): List<LanguageInfo> {
        val ttsLocales = pageController.ttsStateHolder.state.value.supportedLanguages
        val languages = ttsLocales.map { locale ->
            val code = locale.language
            val lang = locale.displayLanguage
            val country = locale.displayCountry
            LanguageInfo(
                id = Random.nextLong() + System.currentTimeMillis(),
                lang = lang,
                country = country,
                code = code,
                locale = locale,
                displayName = locale.displayName
            )
        }
        return languages
    }

    fun updateReadingBgImage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.let { ImageUtils.saveReadingBgImage(bitmap, uri.toString(), context) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateTTSEngineType(engineType: TTSEngineType) {
        viewModelScope.launch {
            val ttsPrefs = _ttsPrefs.value ?: return@launch
            // Clear model selection when switching to system TTS
            val (newModel, newSpeaker) = if (engineType == TTSEngineType.SYSTEM) {
                null to 0
            } else {
                ttsPrefs.selectedTTSModel to ttsPrefs.selectedSpeaker
            }

            ttsPreferencesUtil.updatePreferences(
                ttsPrefs.copy(
                    ttsEngineType = engineType,
                    selectedTTSModel = newModel,
                    selectedSpeaker = newSpeaker,
                    isFirstAiTtsSelection = if (engineType == TTSEngineType.OFFLINE_NEURAL_AI) false else ttsPrefs.isFirstAiTtsSelection
                )
            )

            if (engineType == TTSEngineType.SYSTEM) {
                _currentSpeakers.value = emptyList<Speaker>()
                _localTTSModels.value = emptyList<TTSModelData>()
            }

            pauseTtsPlaying()

            //更改引擎,则需要重新初始化引擎
            pageController.ttsStateHolder.updateEngineInitState(TtsEngineStatus.IDLE)
        }
    }

    /***
    //tts  面板控制, 0-关闭,
    // 1-打开显示主播放控制;
    // 2-显示TTS设置;
    // 3-显示语言选择界面;
    // 4-显示引擎切换选择界面;
    // 5-显示模型切换选择界面;
    // 6-显示语音切换选择界面
     */
    fun setTtsPanelStatus(status: TtsPlayerPanelStatus) {
        _ttsPanelStatus.value = status
    }

    fun loadLocalTtsModels() {
        Logger.i("MainReadViewModel:loadLocalTtsModels")
        viewModelScope.launchIO {
            ttsModelsRepository.getDownloadedModels().stateIn(viewModelScope).firstOrNull()
                ?.let { list ->
                    _localTTSModels.value = list
                }
        }
    }

    fun loadLocalTtsSpeakers() {
        viewModelScope.launchIO {
            ttsPrefs.value?.let { prefs ->
                if (prefs.ttsEngineType == TTSEngineType.OFFLINE_NEURAL_AI) {
                    val modelName = prefs.selectedTTSModel.orEmpty()
                    if (modelName.isEmpty()) {
                        return@launchIO
                    }
                    val model =
                        ttsModelsRepository.getLocalModelByName(modelName) ?: return@launchIO
                    _currentSpeakers.value = model.speakers
                }
            }
        }
    }

    fun selectTtsModel(model: TTSModelData, onCompleteed: () -> Unit) {
        Logger.i("MainReadViewModel:selectTtsModel:${model.name}")
        viewModelScope.launchIO {
            val ttsPrefs = _ttsPrefs.value ?: return@launchIO
            ttsPreferencesUtil.updatePreferences(
                ttsPrefs.copy(
                    selectedTTSModel = model.name,
                    selectedSpeaker = 0
                )
            )
            _currentSpeakers.value = model.speakers

            withContext(Dispatchers.Main) {
                onCompleteed.invoke()
            }
        }
    }

    fun selectSpeaker(speakerId: Int) {
        viewModelScope.launchIO {
            val ttsPrefs = _ttsPrefs.value ?: return@launchIO
            ttsPreferencesUtil.updatePreferences(
                ttsPrefs.copy(
                    selectedSpeaker = speakerId
                )
            )
        }
    }

    fun showReaderDrawer(flag: Boolean) {
        _isDrawerOpen.value = flag
    }

    fun setReadUiEditType(type: ReadUiEditType) {
        _editingColorType.value = type
    }

    fun updateBrightness(value: Float) {
        val newValue = value.coerceIn(0.0f, 1.0f)
        _brightness.value = newValue
        BookApplication.app.topActivity?.let { act ->
            BrightnessHelper.setWindowBrightness(act, newValue)
        }
    }

    fun commitBrightness() {
        isBrightnessCommitting = true
        val currentBrightness = _brightness.value
        viewModelScope.launch {
            readerPrefsUtil.updateBrightness(currentBrightness, true)
        }
    }

    fun updateBrightnessFromSystem(value: Float) {
        _brightness.value = value.coerceIn(0.0f, 1.0f)
    }

}
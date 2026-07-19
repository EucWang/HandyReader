package com.wxn.reader.presentation.lookuphistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.launchIO
import com.wxn.base.util.toLocale
import com.wxn.reader.data.dto.BookVocabularyEntity
import com.wxn.reader.data.model.WordResult
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.DictionaryCacheDao
import com.wxn.reader.domain.repository.DictionaryRepository
import com.wxn.reader.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LookupHistoryViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val dictionaryCacheDao: DictionaryCacheDao,
    private val bookDao: BookDao,
    private val json: Json,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LookupHistoryUiState())
    val uiState: StateFlow<LookupHistoryUiState> = _uiState.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Long?>(null)
    private val _selectedLang = MutableStateFlow<Pair<String, String>?>(null)
    private val _sortBy = MutableStateFlow(SortBy.TIME_DESC)

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // 刷新信号 — 替代 MutableStateFlow<Int> 避免 flatMapLatest cancel/recreate crash
    private val _refreshSignal = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // 当前正在查词中的单词集合（给 UI 显示 loading 用）
    private val _loadingDefinitions = MutableStateFlow<Set<String>>(emptySet())
    // 已尝试查词但失败的单词集合（给 UI 显示 retry 用）
    private val _fetchFailedKeys = MutableStateFlow<Set<String>>(emptySet())
    // 防重入集合 — 保证同一 key 不会同时发起两个请求
    private val _fetchingDefinitions = mutableSetOf<String>()

    private fun cacheKey(word: String, lang: String) = "${word}|${lang}"

    init {
        loadVocabulary()
        loadFilters()
        observeLoadingChanges()
    }

    private fun observeLoadingChanges() {
        viewModelScope.launch {
            _loadingDefinitions
                .drop(1)  // 跳过初始 emptySet
                .collect { _refreshSignal.tryEmit(Unit) }
        }
    }

    private fun loadVocabulary() {
        viewModelScope.launchIO {
            combine(
                _selectedBookId,
                _selectedLang,
                _sortBy,
                _refreshSignal.onStart { emit(Unit) }  // 初始触发
            ) { bookId, lang, sortBy, _ ->
                Triple(bookId, lang, sortBy)
            }
            .debounce(200)
            .flatMapLatest { (bookId, lang, sortBy) ->
                val (sortColumn, isAsc) = when (sortBy) {
                    SortBy.TIME_DESC -> "createdAt" to false
                    SortBy.TIME_ASC -> "createdAt" to true
                    SortBy.WORD_ASC -> "word" to true
                    SortBy.WORD_DESC -> "word" to false
                }
                vocabularyRepository.getEntries(bookId, lang?.first, sortColumn, isAsc)
            }.collect { entries ->
                try {
                    val cards = buildCards(entries)
                    _uiState.value = _uiState.value.copy(
                        cards = cards,
                        isLoading = false,
                        error = null,
                        totalCount = cards.size,
                        selectedBookId = _selectedBookId.value,
                        selectedLang = _selectedLang.value,
                        sortBy = _sortBy.value
                    )
                    // 收集完成后查缺失定义
                    fetchMissingDefinitions(entries)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    private suspend fun buildCards(entries: List<BookVocabularyEntity>): List<LookupHistoryCard> {
        val bookIds = entries.map { it.bookId }.distinct()
        val bookTitles = mutableMapOf<Long, String>()
        for (id in bookIds) {
            val book = bookDao.getBookById(id)
            if (book != null) bookTitles[id] = book.title
        }

        val loadingSet = _loadingDefinitions.value
        val failedSet = _fetchFailedKeys.value

        val grouped = entries.groupBy { "${it.word}|${it.lang}" }
        return grouped.values.map { group ->
            val first = group.first()
            val key = cacheKey(first.word, first.lang)
            val wordResult = try {
                dictionaryCacheDao.getByWordAndLang(first.word, first.lang)
                    ?.let { entity ->
                        json.decodeFromString(WordResult.serializer(), entity.dataJson)
                    }
            } catch (_: Exception) {
                null
            }

            LookupHistoryCard(
                word = first.word,
                lang = first.lang,
                wordResult = wordResult,
                positions = group.map { entry ->
                    LookupHistoryPosition(
                        entryId = entry.id,
                        bookId = entry.bookId,
                        bookTitle = bookTitles[entry.bookId] ?: "",
                        sentenceText = entry.sentenceText,
                        createdAt = entry.createdAt
                    )
                },
                isLoadingDefinition = wordResult == null && key in loadingSet,
                fetchFailed = wordResult == null && key in failedSet && key !in loadingSet,
            )
        }
    }

    private fun fetchMissingDefinitions(entries: List<BookVocabularyEntity>) {
        // 提取无定义的 (word, lang) 对
        viewModelScope.launchIO {
            val missing = entries
                .distinctBy { cacheKey(it.word, it.lang) }
                .filter { entry ->
                    val key = cacheKey(entry.word, entry.lang)
                    val cached = try {
                        dictionaryCacheDao.getByWordAndLang(entry.word, entry.lang)
                    } catch (_: Exception) { null }
                    cached == null && _fetchingDefinitions.add(key)  // 防重入
                }

            if (missing.isEmpty()) return@launchIO

            // 设置 loading 状态
            missing.forEach { entry ->
                val key = cacheKey(entry.word, entry.lang)
                _loadingDefinitions.update { it + key }
            }

            // 并发查词
            missing.map { entry ->
                async {
                    val key = cacheKey(entry.word, entry.lang)
                    val result = dictionaryRepository.lookup(entry.word, entry.lang)
                    if (result.isFailure) {
                        _fetchFailedKeys.update { it + key }
                    }
                }
            }.awaitAll()

            // 所有查词完成后清理 loading 状态
            missing.forEach { entry ->
                val key = cacheKey(entry.word, entry.lang)
                _loadingDefinitions.update { it - key }
                _fetchingDefinitions.remove(key)
                // _refreshSignal 由 snapshotFlow 自动触发
            }
        }
    }

    /**
     *  用户手动重试
     */
    fun retryFetchDefinition(card: LookupHistoryCard) {
        val key = cacheKey(card.word, card.lang)
        _fetchingDefinitions.remove(key)           // 允许重新查
        _fetchFailedKeys.update { it - key }       // 清除失败标记
        _refreshSignal.tryEmit(Unit)                // 触发 re-collect
    }

    private fun loadFilters() {
        viewModelScope.launchIO {
            vocabularyRepository.getDistinctBookIds().collect { bookIds ->
                val books = mutableMapOf<Long, String>()
                for (id in bookIds) {
                    val book = bookDao.getBookById(id)
                    if (book != null) books[id] = book.title
                }
                _uiState.value = _uiState.value.copy(availableBooks = books)
            }
        }
        viewModelScope.launchIO {
            vocabularyRepository.getDistinctLangs().collect { langs ->
                val locales = langs.mapNotNull { lang ->
                    val locale = lang.toLocale()?.displayLanguage
                    if (locale == null) {
                        null
                    } else {
                        Pair(lang, locale)
                    }
                }
                if (locales.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(availableLangs = locales)
                }
            }
        }
    }

    fun selectBook(bookId: Long?) {
        _selectedBookId.value = bookId
    }

    fun selectLang(lang: Pair<String, String>?) {
        _selectedLang.value = lang
    }

    fun setSortBy(sortBy: SortBy) {
        _sortBy.value = sortBy
    }

    fun deleteVocabulary(entryId: Long) {
        viewModelScope.launchIO {
            vocabularyRepository.softDelete(entryId)
        }
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }
}

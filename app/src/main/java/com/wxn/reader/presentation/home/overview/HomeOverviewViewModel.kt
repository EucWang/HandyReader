package com.wxn.reader.presentation.home.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.reader.data.model.RecentToggle
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.use_case.home.GetHeroBookUseCase
import com.wxn.reader.domain.use_case.home.GetHomeHeatmapUseCase
import com.wxn.reader.domain.use_case.home.GetRecentBooksUseCase
import com.wxn.reader.presentation.statistics.components.rollingHeatmapStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeOverviewViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    private val getHeroBookUseCase: GetHeroBookUseCase,
    private val getRecentBooksUseCase: GetRecentBooksUseCase,
    private val getHomeHeatmapUseCase: GetHomeHeatmapUseCase,
) : ViewModel() {

    // 滚动 52 周窗口的起点(今天零点往前推 364 天)。热力图始终展示最近一年,
    // 跨年时上一年数据不会丢失。复用组件层的共享算法,保证 VM 与组件口径一致。
    private val heatmapStartMillis: Long = rollingHeatmapStart()

    /** 供热力图组件作为单一数据源锚点使用(与下方 SQL 查询同源)。 */
    val heatmapWindowStart: Long get() = heatmapStartMillis

    @OptIn(ExperimentalCoroutinesApi::class)
    val heroBook: StateFlow<Book?> = appPreferencesUtil.appPrefsFlow
        .map { it.lastBookId }
        .distinctUntilChanged()
        .flatMapLatest { lastBookId -> getHeroBookUseCase(lastBookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentBooks: StateFlow<List<Book>> = appPreferencesUtil.appPrefsFlow
        .map { it.recentBooksToggle }
        .distinctUntilChanged()
        .flatMapLatest { toggle -> getRecentBooksUseCase(toggle, limit = 30) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentToggle: StateFlow<RecentToggle> = appPreferencesUtil.appPrefsFlow
        .map { it.recentBooksToggle }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecentToggle.READ)

    val heatmapData: StateFlow<List<ReadingActive>> = getHomeHeatmapUseCase(heatmapStartMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateRecentToggle(toggle: RecentToggle) {
        viewModelScope.launch {
            appPreferencesUtil.updateRecentBooksToggle(toggle)
        }
    }
}

package com.wxn.reader.presentation.home.overview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.bean.Book
import com.wxn.reader.presentation.home.overview.components.HeatmapCard
import com.wxn.reader.presentation.home.overview.components.HeroReadingCard
import com.wxn.reader.presentation.home.overview.components.RecentBooksSection

/****
 * 首页Tab页面
 */
@Composable
fun HomeOverviewPanel(
    contentPadding: PaddingValues,
    onOpenBook: (Book) -> Unit,
    onImportClick: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    viewModel: HomeOverviewViewModel = hiltViewModel(),
) {
    val heroBook by viewModel.heroBook.collectAsStateWithLifecycle()
    val recentBooks by viewModel.recentBooks.collectAsStateWithLifecycle()
    val recentToggle by viewModel.recentToggle.collectAsStateWithLifecycle()
    val heatmapData by viewModel.heatmapData.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = contentPadding) {
        item {
            HeroReadingCard(
                book = heroBook,
                onOpenBook = onOpenBook,
                onImportClick = onImportClick,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            RecentBooksSection(
                books = recentBooks,
                currentToggle = recentToggle,
                onToggleSelected = viewModel::updateRecentToggle,
                onOpenBook = onOpenBook,
            )
        }

        item {
            HeatmapCard(
                readingActivities = heatmapData,
                windowStartMillis = viewModel.heatmapWindowStart,
                onClick = onNavigateToStatistics,
            )
        }
    }
}

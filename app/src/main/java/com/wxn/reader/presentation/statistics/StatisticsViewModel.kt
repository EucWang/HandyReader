package com.wxn.reader.presentation.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.AnnotationType
import com.wxn.reader.domain.model.Author
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Genre
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.model.Statistics
import com.wxn.reader.domain.use_case.annotations.GetAllAnnotationsUseCase
import com.wxn.reader.domain.use_case.books.GetAllBooksUseCase
import com.wxn.reader.domain.use_case.notes.GetAllNotesUseCase
import com.wxn.reader.domain.use_case.reading_activity.GetAllReadingActivitiesUseCase
import com.wxn.reader.presentation.statistics.components.rollingHeatmapStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    private val getAllBooksUseCase: GetAllBooksUseCase,
    private val getAllNotesUseCase: GetAllNotesUseCase,
    private val getAllAnnotationsUseCase: GetAllAnnotationsUseCase,
    private val getAllReadingActivitiesUseCase: GetAllReadingActivitiesUseCase,
    application: Application,
) : AndroidViewModel(application) {


    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    private val _statistics = MutableStateFlow(Statistics())
    val statistics: StateFlow<Statistics> = _statistics.asStateFlow()


    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()


    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _annotations = MutableStateFlow<List<BookAnnotation>>(emptyList())
    val annotations: StateFlow<List<BookAnnotation>> = _annotations.asStateFlow()

    /** 热力图滚动 52 周窗口起点(今天零点往前推 364 天)。与首页同口径,供组件作为单一数据源锚点。 */
    val heatmapWindowStart: Long = rollingHeatmapStart()

    private val _readingActivities = MutableStateFlow<List<ReadingActive>>(emptyList())
//    val readingActivities: StateFlow<List<ReadingActivity>> = _readingActivities.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow.collect { initialPreferences ->
                _appPreferences.value = initialPreferences
            }
        }
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            combine(
                getAllBooksUseCase(),
                getAllNotesUseCase(),
                getAllAnnotationsUseCase(),
                getAllReadingActivitiesUseCase(),
            ) { books, notes, annotations, readingActivities ->
                StatisticsInput(books, notes, annotations, readingActivities)
            }.collect { (books, notes, annotations, readingActivities) ->
                _books.value = books
                _notes.value = notes
                _annotations.value = annotations
                _readingActivities.value = readingActivities
                _statistics.value =
                    calculateStatistics(books, notes, annotations, readingActivities)
            }
        }
    }

    private data class StatisticsInput(
        val books: List<Book>,
        val notes: List<Note>,
        val annotations: List<BookAnnotation>,
        val readingActivities: List<ReadingActive>
    )


    private fun calculateStatistics(
        books: List<Book>,
        notes: List<Note>,
        annotations: List<BookAnnotation>,
        readingActivities: List<ReadingActive>
    ): Statistics {
        val currentDate = LocalDate.now()
        val currentYear = currentDate.year
        val currentMonth = currentDate.monthValue

        val booksReadThisYear = books.count { book ->
            book.readingStatus == ReadingStatus.FINISHED.value &&
                    book.endReadingDate?.let { endDate ->
                        val endReadingDate =
                            Instant.ofEpochMilli(endDate).atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        endReadingDate.year == currentYear
                    } ?: false
        }

        val booksReadThisMonth = books.count { book ->
            book.readingStatus == ReadingStatus.FINISHED.value &&
                    book.endReadingDate?.let { endDate ->
                        val endReadingDate =
                            Instant.ofEpochMilli(endDate).atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        endReadingDate.year == currentYear && endReadingDate.monthValue == currentMonth
                    } ?: false
        }

        val totalReadingTime = readingActivities.sumOf { it.readingTime }

        //每本书平均阅读时长
        val readBooksCount = books.count { it.readingStatus != ReadingStatus.NOT_STARTED.value }
        val averageReadingTimePerBook =
            if (readBooksCount > 0) totalReadingTime / readBooksCount else 0

        //每日阅读时长(★ v1.4 一般-F4:从"按行均"改"按天均"——v9 起复合 PK (date, deviceId),
        //  同一天会有多设备多行,按行均会偏小;按 distinct day 均才是真正的"日均")
        val distinctDayCount = readingActivities.map { it.date }.distinct().size
        val averageDailyReadingTime = if (distinctDayCount > 0) {
            totalReadingTime / distinctDayCount
        } else {
            0
        }


        // Sort reading activities by date
        val sortedActivities = readingActivities.sortedBy { it.date }

        // calculate reading streaks
        val (longestStreak, currentStreak) = calculateReadingStreaks(sortedActivities, currentDate)


        return Statistics(
            totalBooks = books.size,
            booksRead = books.count { it.readingStatus == ReadingStatus.FINISHED.value },
            booksReadThisYear = booksReadThisYear,
            booksReadThisMonth = booksReadThisMonth,
            booksInProgress = books.count { it.readingStatus == ReadingStatus.IN_PROGRESS.value },
            booksToRead = books.count { it.readingStatus == ReadingStatus.NOT_STARTED.value },
            totalReadingTime = totalReadingTime,
            averageReadingTimePerBook = averageReadingTimePerBook,
            averageDailyReadingTime = averageDailyReadingTime,
            longestReadingStreak = longestStreak,
            currentReadingStreak = currentStreak,
            favoriteBooks = books.count { it.isFavorite },
            ratedBooks = books.count { it.rating > 0 },
            averageRating = books.filter { it.rating > 0 }.let { ratedBooks ->
                if (ratedBooks.isNotEmpty()) ratedBooks.sumOf { it.rating.toDouble() } / ratedBooks.size else 0.0
            },

            totalNotes = notes.size,
            totalHighlights = annotations.count { it.type == AnnotationType.HIGHLIGHT },
            totalUnderlines = annotations.count { it.type == AnnotationType.UNDERLINE },

            favoriteAuthors = books.filter { it.author.isNotBlank() }
                .groupBy { it.author }
                .map { (author, books) ->
                    Author(name = author, books = books)
                }.sortedByDescending { it.books.size },

            genreDistribution = books.flatMap {
                it.category?.split(",")?.map { it.trim() } ?: emptyList()
            }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()
                .map { (genre, count) -> Genre(name = genre, count = count) }
                .sortedByDescending { it.count },

            readingActivities = sortedActivities


        )
    }

    private fun calculateReadingStreaks(
        sortedActivities: List<ReadingActive>,
        currentDate: LocalDate
    ): Pair<Int, Int> {
        var currentStreak = 0
        var longestStreak = 0
        var lastReadDate: LocalDate? = null

        for (activity in sortedActivities) {
            // Only consider activities that are at least 1 minute long
            if (activity.readingTime >= 60000) {
                val activityDate =
                    Instant.ofEpochMilli(activity.date).atZone(ZoneId.systemDefault()).toLocalDate()

                if (lastReadDate == null || activityDate == lastReadDate.plusDays(1)) {
                    currentStreak++
                    if (currentStreak > longestStreak) {
                        longestStreak = currentStreak
                    }
                } else if (activityDate != lastReadDate) {
                    currentStreak = 1
                }

                lastReadDate = activityDate
            }
        }

        // Check if the streak is still active
        if (lastReadDate != null) {
            val daysSinceLastRead = ChronoUnit.DAYS.between(lastReadDate, currentDate)
            if (daysSinceLastRead > 1) {
                currentStreak = 0
            }
        } else {
            // No valid reading activities found
            currentStreak = 0
        }

        return Pair(longestStreak, currentStreak)
    }
}
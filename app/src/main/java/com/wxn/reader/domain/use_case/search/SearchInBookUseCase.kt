package com.wxn.reader.domain.use_case.search

import android.content.Context
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.bookparser.TextParser
import com.wxn.reader.domain.use_case.search.SearchInBookUseCase.Companion.CONTEXT_RADIUS
import com.wxn.reader.domain.use_case.search.SearchInBookUseCase.Companion.MAX_MERGED_MATCHES
import javax.inject.Inject

data class MatchRange(val start: Int, val end: Int)

data class SearchResultItem(
    val locator: Locator,
    val chapterName: String,
    val contextText: String,
    val matchRanges: List<MatchRange>,
    val highlightLocators: List<Locator> = emptyList(),
)

private class MergedResultBuilder(
    private val line: String,
    private val paragraphIndex: Int,
    firstMatchStart: Int,
    firstMatchEnd: Int,
    firstLocator: Locator,
) {
    private val matches = mutableListOf<Triple<Int, Int, Locator>>()

    init {
        matches.add(Triple(firstMatchStart, firstMatchEnd, firstLocator))
    }

    val lastMatchEnd: Int get() = matches.last().second

    fun tryAddMatch(start: Int, end: Int, locator: Locator): Boolean {
        if (matches.size >= MAX_MERGED_MATCHES) return false
        matches.add(Triple(start, end, locator))
        return true
    }

    fun build(chapterName: String): SearchResultItem {
        val firstStart = matches.first().first
        val lastEnd = matches.last().second
        val contextStart = maxOf(0, firstStart - CONTEXT_RADIUS)
        val contextEnd = minOf(line.length, lastEnd + CONTEXT_RADIUS)
        val contextText = line.substring(contextStart, contextEnd)
        val offsetShift = contextStart

        val matchRanges = matches.map { (start, end, _) ->
            MatchRange(start - offsetShift, end - offsetShift)
        }

        val mergedLocator = Locator(
            chapterIndex = matches.first().third.chapterIndex,
            startParagraphIndex = paragraphIndex,
            startTextOffset = firstStart,
            endParagraphIndex = paragraphIndex,
            endTextOffset = lastEnd,
            text = line.substring(firstStart, lastEnd),
            progression = -1.0,
        )

        val highlightLocators = matches.map { it.third }

        return SearchResultItem(
            locator = mergedLocator,
            chapterName = chapterName,
            contextText = contextText,
            matchRanges = matchRanges,
            highlightLocators = if (matches.size > 1) highlightLocators else emptyList(),
        )
    }
}

class SearchInBookUseCase @Inject constructor() {

    companion object {
        const val MAX_RESULTS = 1000
        const val CONTEXT_RADIUS = 40
        private const val MERGE_DISTANCE_THRESHOLD = CONTEXT_RADIUS * 2
        const val MAX_MERGED_MATCHES = 50
    }

    data class SearchProgress(
        val results: List<SearchResultItem> = emptyList(),
        val searchedChapters: Int = 0,
        val totalChapters: Int = 0,
        val isComplete: Boolean = false,
        val isTruncated: Boolean = false,
    )

    suspend fun search(
        context: Context,
        book: Book,
        chapters: List<BookChapter>,
        query: String,
        textParser: TextParser,
        appPrefsUtil: AppPreferencesUtil,
        onProgress: (SearchProgress) -> Unit,
        isActive: () -> Boolean,
    ) {
        if (query.trim().length < 2) return

        val allResults = mutableListOf<SearchResultItem>()
        var isTruncated = false

        for ((chapterIdx, chapter) in chapters.withIndex()) {
            if (!isActive()) break
            if (allResults.size >= MAX_RESULTS) {
                isTruncated = true
                break
            }

            val chapterResults = mutableListOf<SearchResultItem>()

            try {
                val rawContents = BookHelper.loadChapterContent(context, book, chapter, textParser)
                val contents = BookHelper.disposeContent(appPrefsUtil, chapter, rawContents)

                var currentMerged: MergedResultBuilder? = null
                var lastParagraphIndex = -1

                for ((index, content) in contents.withIndex()) {
                    if (content !is ReaderText.Text) continue
                    if (!isActive()) break
                    if (allResults.size + chapterResults.size >= MAX_RESULTS) {
                        isTruncated = true
                        break
                    }

                    val paragraphIndex = index
                    val line = content.line

                    if (paragraphIndex != lastParagraphIndex) {
                        currentMerged?.build(chapter.chapterName)?.let { chapterResults.add(it) }
                        currentMerged = null
                        lastParagraphIndex = paragraphIndex
                    }

                    var searchFrom = 0
                    while (searchFrom <= line.length - query.length) {
                        if (!isActive()) break
                        if (allResults.size + chapterResults.size >= MAX_RESULTS) {
                            isTruncated = true
                            break
                        }

                        val matchIndex = line.indexOf(query, searchFrom, ignoreCase = true)
                        if (matchIndex < 0) break

                        val matchEnd = matchIndex + query.length

                        val singleLocator = Locator(
                            chapterIndex = chapter.chapterIndex,
                            startParagraphIndex = paragraphIndex,
                            startTextOffset = matchIndex,
                            endParagraphIndex = paragraphIndex,
                            endTextOffset = matchEnd,
                            text = line.substring(matchIndex, matchEnd),
                            progression = -1.0,
                        )

                        val canMerge = currentMerged != null
                                && (matchIndex - currentMerged!!.lastMatchEnd) < MERGE_DISTANCE_THRESHOLD

                        if (canMerge) {
                            if (!currentMerged!!.tryAddMatch(matchIndex, matchEnd, singleLocator)) {
                                currentMerged!!.build(chapter.chapterName).let { chapterResults.add(it) }
                                currentMerged = MergedResultBuilder(
                                    line, paragraphIndex, matchIndex, matchEnd, singleLocator
                                )
                            }
                        } else {
                            currentMerged?.build(chapter.chapterName)?.let { chapterResults.add(it) }
                            currentMerged = MergedResultBuilder(
                                line, paragraphIndex, matchIndex, matchEnd, singleLocator
                            )
                        }

                        searchFrom = matchIndex + 1
                    }
                }

                currentMerged?.build(chapter.chapterName)?.let { chapterResults.add(it) }
            } catch (_: Exception) {
            }

            allResults.addAll(chapterResults)

            onProgress(
                SearchProgress(
                    results = allResults.toList(),
                    searchedChapters = chapterIdx + 1,
                    totalChapters = chapters.size,
                    isComplete = chapterIdx == chapters.lastIndex && !isTruncated,
                    isTruncated = isTruncated,
                )
            )
        }

        onProgress(
            SearchProgress(
                results = allResults.toList(),
                searchedChapters = chapters.size,
                totalChapters = chapters.size,
                isComplete = true,
                isTruncated = isTruncated,
            )
        )
    }
}

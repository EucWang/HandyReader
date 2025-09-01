package com.wxn.reader.domain.use_case.chapters

import com.wxn.reader.domain.repository.ChaptersRepository
import javax.inject.Inject

class UpdateChapterWordCountUserCase @Inject constructor(
    private val repository: ChaptersRepository
) {

    operator suspend fun invoke(bookId: Long, chapterIndex: Int, wordCount: Long, picCount: Long, progress: Float) {
        repository.updateChapterWordCount(bookId, chapterIndex, wordCount, picCount, progress)
    }
}
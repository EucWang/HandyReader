package com.wxn.reader.domain.use_case.chapters

import com.wxn.base.bean.BookChapter
import com.wxn.reader.domain.repository.ChaptersRepository
import javax.inject.Inject

class InsertChaptersUserCase @Inject constructor(
    private val repository: ChaptersRepository
) {
    suspend operator fun invoke(chapters: List<BookChapter>) {
        repository.insertChapters(chapters)
    }
}
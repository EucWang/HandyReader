package com.wxn.reader.domain.use_case.chapters

import com.wxn.reader.domain.repository.ChaptersRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChapterCountByBookIdUserCase @Inject constructor(
    private val repository: ChaptersRepository
) {
    operator fun invoke(bookId: Long): Flow<Int> = repository.getChapterCount(bookId)
}
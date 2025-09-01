package com.wxn.reader.domain.use_case.books

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    operator fun invoke(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>
    ): Flow<PagingData<Book>> = Pager(
        config = PagingConfig(
            pageSize = 9,
            enablePlaceholders = true,
        )
    ) {
        repository.getAllBooks(sortOption, isAscending, readingStatuses, fileTypes)
    }.flow

    fun getSortedBooks( sortOption: SortOption,
                        isAscending: Boolean,
                        readingStatuses: Set<ReadingStatus>,
                        fileTypes: Set<FileType>) : Flow<List<Book>> {
        return repository.getSortedBooks(sortOption, isAscending, readingStatuses, fileTypes)
    }
}
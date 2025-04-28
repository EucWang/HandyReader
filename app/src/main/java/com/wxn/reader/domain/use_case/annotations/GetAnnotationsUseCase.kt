package com.wxn.reader.domain.use_case.annotations

import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAnnotationsUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(bookId: Long): Flow<List<BookAnnotation>> {
        return repository.getAnnotations(bookId)
    }
}
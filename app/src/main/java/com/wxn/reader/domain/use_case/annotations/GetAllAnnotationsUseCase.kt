package com.wxn.reader.domain.use_case.annotations

import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllAnnotationsUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(): Flow<List<BookAnnotation>> {
        return repository.getAllAnnotations()
    }
}
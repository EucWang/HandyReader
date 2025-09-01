package com.wxn.reader.domain.use_case.annotations

import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AddAnnotationUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(annotation: BookAnnotation): Long = withContext(Dispatchers.IO) {
        repository.addAnnotation(annotation)
    }
}
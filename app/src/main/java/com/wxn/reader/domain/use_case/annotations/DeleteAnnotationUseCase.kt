package com.wxn.reader.domain.use_case.annotations

import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class DeleteAnnotationUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(annotation: BookAnnotation) {
        repository.deleteAnnotation(annotation)
    }
}
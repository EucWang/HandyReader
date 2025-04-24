package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetBookUrisUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(): List<String> = withContext(Dispatchers.IO) {
        repository.getAllBookUris()
    }
}
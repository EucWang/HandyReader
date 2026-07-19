package com.wxn.reader.domain.use_case.notes

import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNotesForBookUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(bookId: Long): Flow<List<Note>> {
        return repository.getNotesForBook(bookId)
    }
}
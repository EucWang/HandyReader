package com.wxn.reader.domain.use_case.notes

import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllNotesUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(): Flow<List<Note>> {
        return repository.getAllNotes()
    }
}
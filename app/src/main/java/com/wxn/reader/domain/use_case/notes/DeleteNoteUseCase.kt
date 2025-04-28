package com.wxn.reader.domain.use_case.notes

import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(note: Note) {
        repository.deleteNote(note)
    }
}
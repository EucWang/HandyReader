package com.wxn.reader.data.mapper.note

import com.wxn.reader.data.dto.NoteEntity
import com.wxn.reader.domain.model.Note
import javax.inject.Inject

class NoteMapperImpl @Inject constructor() : NoteMapper {
    override suspend fun toNoteEntity(note: Note): NoteEntity {
        return NoteEntity(
            id = note.id,
            locator = note.locator,
            selectedText = note.selectedText,
            note = note.note,
            color = note.color,
            bookId = note.bookId
        )
    }

    override suspend fun toNote(noteEntity: NoteEntity): Note {
        return Note(
            id = noteEntity.id,
            locator = noteEntity.locator,
            selectedText = noteEntity.selectedText,
            note = noteEntity.note,
            color = noteEntity.color,
            bookId = noteEntity.bookId
        )
    }
}
package com.wxn.reader.data.mapper.note

import com.wxn.reader.data.dto.NoteEntity
import com.wxn.reader.domain.model.Note

interface NoteMapper {

    suspend fun toNoteEntity(note:Note) : NoteEntity

    suspend fun toNote(noteEntity: NoteEntity) : Note
}
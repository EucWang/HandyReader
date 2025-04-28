package com.wxn.reader.data.mapper.annotation

import com.wxn.reader.data.dto.BookAnnotationEntity
import com.wxn.reader.domain.model.BookAnnotation
import javax.inject.Inject

class BookAnnotationMapperImpl @Inject constructor() : BookAnnotationMapper {
    override suspend fun toBookAnnotation(bookAnnotationEntity: BookAnnotationEntity): BookAnnotation {
        return BookAnnotation(
            id = bookAnnotationEntity.id,
            bookId = bookAnnotationEntity.bookId,
            locator = bookAnnotationEntity.locator,
            color = bookAnnotationEntity.color,
            note = bookAnnotationEntity.note,
            type = bookAnnotationEntity.type
        )
    }

    override suspend fun toBookAnnotationEntity(bookAnnotation: BookAnnotation): BookAnnotationEntity {
        return BookAnnotationEntity(
            id = bookAnnotation.id,
            bookId = bookAnnotation.bookId,
            locator = bookAnnotation.locator,
            color = bookAnnotation.color,
            note = bookAnnotation.note,
            type = bookAnnotation.type
        )
    }
}
package com.wxn.reader.data.mapper.annotation

import com.wxn.reader.data.dto.BookAnnotationEntity
import com.wxn.reader.domain.model.BookAnnotation

interface BookAnnotationMapper {

    suspend fun toBookAnnotation(bookAnnotationEntity: BookAnnotationEntity): BookAnnotation

    suspend fun toBookAnnotationEntity(bookAnnotation: BookAnnotation): BookAnnotationEntity
}
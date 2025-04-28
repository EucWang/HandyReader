package com.wxn.reader.data.mapper.bookshelf

import com.wxn.reader.data.dto.BookShelfEntity
import com.wxn.reader.domain.model.BookShelf

interface BookShelfMapper {

    suspend fun toBookShelfEntity(bookShelf: BookShelf) : BookShelfEntity

    suspend fun toBookShelf(bookShelfEntity: BookShelfEntity) : BookShelf
}
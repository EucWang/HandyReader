package com.wxn.reader.data.mapper.shelf

import com.wxn.reader.data.dto.ShelfEntity
import com.wxn.reader.domain.model.Shelf

interface ShelfMapper {

    suspend fun toShelfEntity(shelf: Shelf) : ShelfEntity

    suspend fun toShelf(shelfEntity: ShelfEntity) : Shelf
}
package com.wxn.reader.data.mapper.shelf

import com.wxn.reader.data.dto.ShelfEntity
import com.wxn.reader.domain.model.Shelf
import javax.inject.Inject

class ShelfMapperImpl @Inject constructor() : ShelfMapper {
    override suspend fun toShelfEntity(shelf: Shelf): ShelfEntity {
        return ShelfEntity(
            id = shelf.id,
            name = shelf.name,
            order = shelf.order
        )
    }

    override suspend fun toShelf(shelfEntity: ShelfEntity): Shelf {
        return Shelf(
            id = shelfEntity.id,
            name = shelfEntity.name,
            order = shelfEntity.order
        )
    }
}
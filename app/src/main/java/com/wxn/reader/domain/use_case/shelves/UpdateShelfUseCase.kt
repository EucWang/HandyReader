package com.wxn.reader.domain.use_case.shelves

import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.repository.ShelfRepository
import javax.inject.Inject

class UpdateShelfUseCase @Inject constructor(
    private val shelfRepository: ShelfRepository
) {
    suspend operator fun invoke(shelf: Shelf) {
        shelfRepository.updateShelf(shelf)
    }
}
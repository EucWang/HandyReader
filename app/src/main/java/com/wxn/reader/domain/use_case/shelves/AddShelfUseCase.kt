package com.wxn.reader.domain.use_case.shelves

import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.repository.ShelfRepository
import javax.inject.Inject

// AddShelfUseCase.kt
class AddShelfUseCase @Inject constructor(private val shelfRepository: ShelfRepository) {
    suspend operator fun invoke(shelfName: String, order: Int): Long {
        return shelfRepository.addShelf(Shelf(name = shelfName, order = order))
    }
}
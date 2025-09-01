package com.wxn.reader.domain.use_case.shelves

import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.repository.ShelfRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// GetShelvesUseCase.kt
class GetShelvesUseCase @Inject constructor(private val shelfRepository: ShelfRepository) {
    operator fun invoke(): Flow<List<Shelf>> = shelfRepository.getShelves()
}
package com.wxn.reader.domain.use_case.shelves

import com.wxn.reader.domain.repository.ShelfRepository
import javax.inject.Inject

class RemoveBooksFromShelfUseCase @Inject constructor(private val shelfRepository: ShelfRepository) {
    suspend operator fun invoke(bookId: Long, shelfId: Long) {
        shelfRepository.removeBookFromShelf(bookId, shelfId)
    }

}
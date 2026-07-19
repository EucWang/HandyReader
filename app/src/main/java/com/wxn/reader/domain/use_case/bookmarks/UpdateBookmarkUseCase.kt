package com.wxn.reader.domain.use_case.bookmarks

import com.wxn.base.bean.Bookmark
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateBookmarkUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(bookmark: Bookmark) {
        repository.updateBookmark(bookmark)
    }
}
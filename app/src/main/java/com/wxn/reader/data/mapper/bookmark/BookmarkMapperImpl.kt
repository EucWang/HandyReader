package com.wxn.reader.data.mapper.bookmark

import com.wxn.reader.data.dto.BookmarkEntity
import com.wxn.base.bean.Bookmark
import javax.inject.Inject

class BookmarkMapperImpl @Inject constructor() : BookmarkMapper {
    override suspend fun toBookmarkEntity(bookmark: Bookmark): BookmarkEntity {
        return BookmarkEntity(
            id = bookmark.id,
            bookId = bookmark.bookId,
            chapterIndex = bookmark.chapterIndex,
            locator = bookmark.locator,
            dateAndTime = bookmark.dateAndTime,
            color = bookmark.color
        )
    }

    override suspend fun toBookmark(bookmarkEntity: BookmarkEntity): Bookmark {
        return Bookmark(
            id = bookmarkEntity.id,
            bookId = bookmarkEntity.bookId,
            chapterIndex = bookmarkEntity.chapterIndex,
            locator = bookmarkEntity.locator,
            dateAndTime = bookmarkEntity.dateAndTime,
            color = bookmarkEntity.color
        )
    }
}
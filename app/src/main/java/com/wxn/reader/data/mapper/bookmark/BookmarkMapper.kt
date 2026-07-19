package com.wxn.reader.data.mapper.bookmark

import com.wxn.reader.data.dto.BookmarkEntity
import com.wxn.base.bean.Bookmark

interface BookmarkMapper {

    suspend fun toBookmarkEntity(bookmark: Bookmark): BookmarkEntity

    suspend fun toBookmark(bookmarkEntity: BookmarkEntity): Bookmark
}
package com.wxn.reader.data.mapper.book

import com.wxn.base.bean.BookChapter
import com.wxn.reader.data.dto.BookChapterEntity

interface ChapterMapper {

    suspend fun toChapterEntity(chapter: BookChapter): BookChapterEntity

    suspend fun toChapter(entity: BookChapterEntity?) : BookChapter?
}
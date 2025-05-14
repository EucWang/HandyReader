package com.wxn.reader.data.mapper.book

import com.wxn.base.bean.BookChapter
import com.wxn.reader.data.dto.BookChapterEntity
import javax.inject.Inject

class ChapterMapperImpl @Inject constructor()  : ChapterMapper{
    override suspend fun toChapterEntity(chapter: BookChapter): BookChapterEntity {
        return BookChapterEntity(
            id = chapter.id,
            bookId = chapter.bookId,
            chapterIndex = chapter.chapterIndex,
            chapterName = chapter.chapterName,
            createTimeValue = chapter.createTimeValue,
            updateDate = chapter.updateDate,
            updateTimeValue = chapter.updateTimeValue,
            chapterUrl = chapter.chapterUrl,
            cachedName = chapter.cachedName,
            chaptersSize = chapter.chaptersSize
        )
    }

    override suspend fun toChapter(entity: BookChapterEntity): BookChapter {
        return BookChapter(
            id = entity.id,
            bookId = entity.bookId,
            chapterIndex = entity.chapterIndex,
            chapterName = entity.chapterName,
            createTimeValue = entity.createTimeValue,
            updateDate = entity.updateDate,
            updateTimeValue = entity.updateTimeValue,
            chapterUrl = entity.chapterUrl,
            cachedName = entity.cachedName,
            chaptersSize = entity.chaptersSize
        )
    }
}
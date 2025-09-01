package com.wxn.reader.data.mapper.book

import com.wxn.base.bean.BookChapter
import com.wxn.reader.data.dto.BookChapterEntity
import javax.inject.Inject

class ChapterMapperImpl @Inject constructor()  : ChapterMapper{
    override suspend fun toChapterEntity(chapter: BookChapter): BookChapterEntity {
        return BookChapterEntity(
            id = chapter.id,
            chapterId = chapter.chapterId,
            bookId = chapter.bookId,
            chapterIndex = chapter.chapterIndex,
            chapterName = chapter.chapterName,
            createTimeValue = chapter.createTimeValue,
            updateDate = chapter.updateDate,
            updateTimeValue = chapter.updateTimeValue,
            chapterUrl = chapter.chapterUrl,
            srcName = chapter.srcName,
            chaptersSize = chapter.chaptersSize,
            parentChapterId = chapter.parentChapterId,
            wordCount = chapter.wordCount,
            picCount = chapter.picCount,
            chapterProgress = chapter.chapterProgress
        )
    }

    override suspend fun toChapter(entity: BookChapterEntity?): BookChapter? {
        entity ?: return null
        return BookChapter(
            id = entity.id,
            chapterId = entity.chapterId,
            bookId = entity.bookId,
            chapterIndex = entity.chapterIndex,
            chapterName = entity.chapterName,
            createTimeValue = entity.createTimeValue,
            updateDate = entity.updateDate,
            updateTimeValue = entity.updateTimeValue,
            chapterUrl = entity.chapterUrl,
            srcName = entity.srcName,
            chaptersSize = entity.chaptersSize,
            parentChapterId = entity.parentChapterId,
            wordCount = entity.wordCount,
            picCount = entity.picCount,
            count = entity.wordCount + entity.picCount,
            chapterProgress = entity.chapterProgress
        )
    }
}
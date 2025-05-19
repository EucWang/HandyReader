package com.wxn.reader.data.repository

import com.wxn.base.bean.BookChapter
import com.wxn.reader.data.mapper.book.ChapterMapper
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.domain.repository.ChaptersRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChaptersRepositoryImpl @Inject constructor(
    private val chapterDao: ChapterDao,
    private val chapterMapper: ChapterMapper
) : ChaptersRepository {
    override fun getAllChapters(bookId: Long): Flow<List<BookChapter>> =
        chapterDao.getChapters(bookId).map { entities ->
            entities.map { entity ->
                chapterMapper.toChapter(entity)
            }
        }

    override fun getChapter(bookId: Long, chapterIndex: Int): Flow<BookChapter> =
        chapterDao.getChapter(bookId, chapterIndex).map { entity ->
            chapterMapper.toChapter(entity)
        }

    override suspend fun insertChapters(chapters: List<BookChapter>) {
        chapters.map { chapter ->
            chapterMapper.toChapterEntity(chapter)
        }.let { entities ->
            chapterDao.insertChapters(entities)
        }
    }

    override fun getChapterCount(bookId:Long): Flow<Int> {
        return chapterDao.getChapterCount(bookId)
    }
}
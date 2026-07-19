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
            entities.mapNotNull { entity ->
                chapterMapper.toChapter(entity)
            }
        }

    override suspend fun updateChapterWordCount(bookId: Long, chapterIndex: Int, wordCount: Long, picCount: Long, chapterProgress: Float) {
        chapterDao.setChapterWordCount(bookId, chapterIndex, wordCount, picCount, chapterProgress)
    }

    override fun getChapter(bookId: Long, chapterIndex: Int): Flow<BookChapter?> =
        chapterDao.getChapter(bookId, chapterIndex).map { entity ->
            entity?.let { chapterMapper.toChapter(it) }
        }

    override suspend fun insertChapters(chapters: List<BookChapter>) {
        chapters.map { chapter ->
            chapterMapper.toChapterEntity(chapter)
        }.let { entities ->
            chapterDao.insertChapters(entities)
        }
    }

    override suspend fun deleteChaptersByBookId(bookId: Long) {
        chapterDao.deleteChaptersByBookId(bookId)
    }

    override suspend fun replaceChapters(bookId: Long, chapters: List<BookChapter>) {
        val entities = chapters.map { chapterMapper.toChapterEntity(it) }
        chapterDao.replaceChapters(bookId, entities)
    }

    override fun getChapterCount(bookId: Long): Flow<Int> {
        return chapterDao.getChapterCount(bookId)
    }
}
package com.wxn.reader.data.repository

import com.wxn.base.bean.Locator
import com.wxn.reader.data.dto.BookVocabularyEntity
import com.wxn.reader.data.source.local.dao.BookVocabularyDao
import com.wxn.reader.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VocabularyRepositoryImpl @Inject constructor(
    private val vocabularyDao: BookVocabularyDao
) : VocabularyRepository {

    override suspend fun saveEntry(
        bookId: Long,
        word: String,
        lang: String,
        locator: Locator,
        sentenceText: String
    ): Long {
        val entity = BookVocabularyEntity(
            bookId = bookId,
            lang = lang,
            word = word,
            sentenceText = sentenceText,
            chapterIndex = locator.chapterIndex,
            startParagraphIndex = locator.startParagraphIndex,
            startTextOffset = locator.startTextOffset,
            locator = locator.toJsonString(),
            uuid = java.util.UUID.randomUUID().toString()
        )
        return vocabularyDao.insert(entity)
    }

    override suspend fun softDelete(id: Long) {
        vocabularyDao.softDelete(id)
    }

    override fun getEntries(
        bookId: Long?,
        lang: String?,
        sortBy: String,
        isAsc: Boolean
    ): Flow<List<BookVocabularyEntity>> {
        return vocabularyDao.getVocabularyEntries(bookId, lang, sortBy, isAsc)
    }

    override fun getDistinctBookIds(): Flow<List<Long>> {
        return vocabularyDao.getDistinctBookIds()
    }

    override fun getDistinctLangs(): Flow<List<String>> {
        return vocabularyDao.getDistinctLangs()
    }

    override fun getActiveCount(): Flow<Int> {
        return vocabularyDao.getActiveCount()
    }
}

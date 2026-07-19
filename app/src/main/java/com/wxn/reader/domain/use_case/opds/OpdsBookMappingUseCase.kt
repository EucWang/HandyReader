package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.data.dto.OpdsBookMappingEntity
import com.wxn.reader.data.source.local.dao.OpdsBookMappingDao
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class OpdsBookMappingUseCase @Inject constructor(
    private val opdsBookMappingDao: OpdsBookMappingDao,
    private val booksRepository: BooksRepository
) {
    data class ImportInfo(
        val bookId: Long,
        val filePath: String,
        val fileType: String
    )

    suspend fun findImported(remoteUrl: String, catalogId: Long): ImportInfo? {
        val mapping = opdsBookMappingDao.getActiveByRemoteUrl(remoteUrl, catalogId)
            ?: return null
        val book = booksRepository.getBookById(mapping.bookId)
            ?: return null
        return ImportInfo(bookId = book.id, filePath = book.filePath, fileType = book.fileType)
    }

    suspend fun saveMapping(remoteUrl: String, catalogId: Long, bookId: Long) {
        opdsBookMappingDao.insert(
            OpdsBookMappingEntity(remoteUrl = remoteUrl, catalogId = catalogId, bookId = bookId)
        )
    }
}

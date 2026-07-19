package com.wxn.reader.domain.repository

import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    fun getDownloadHistory(): Flow<List<DownloadHistoryEntity>>

    suspend fun getDownloadHistoryByFileId(fileId: String): DownloadHistoryEntity?

    suspend fun insertDownloadHistory(history: DownloadHistoryEntity)

    suspend fun deleteDownloadHistory(fileId: String)


    suspend fun markDownloadAsDeleted(fileId: String)
}
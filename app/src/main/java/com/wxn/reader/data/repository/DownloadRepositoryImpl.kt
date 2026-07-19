package com.wxn.reader.data.repository

import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.data.source.local.dao.DownloadHistoryDao
import com.wxn.reader.domain.model.DownloadStatus
import com.wxn.reader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


class DownloadRepositoryImpl @Inject constructor(
    private val downloadHistoryDao: DownloadHistoryDao
) : DownloadRepository {
    override fun getDownloadHistory(): Flow<List<DownloadHistoryEntity>> {
        return downloadHistoryDao.getAllHistory()
    }

    override suspend fun getDownloadHistoryByFileId(fileId: String): DownloadHistoryEntity? {
        return downloadHistoryDao.getByFileId(fileId)
    }

    override suspend fun insertDownloadHistory(history: DownloadHistoryEntity) {
        downloadHistoryDao.insert(history)
    }

    override suspend fun deleteDownloadHistory(fileId: String) {
        downloadHistoryDao.deleteByFileId(fileId)
    }

    /****
     * 更新下载状态,
     * 主要是用于删除模型时, 标记标志状态为删除状态
     */
    override suspend fun markDownloadAsDeleted(
        fileId: String
    ) {
        downloadHistoryDao.updateStatusToFileId(fileId, DownloadStatus.DELETED)
    }
}
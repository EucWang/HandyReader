package com.wxn.reader.domain.use_case.download

import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject


class GetDownloadHistoryUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadHistoryEntity>> {
        return downloadRepository.getDownloadHistory()
    }
}
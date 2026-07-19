package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow


@Dao
interface DownloadHistoryDao {



    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun getAllHistory(): Flow<List<DownloadHistoryEntity>>


    @Query("SELECT * FROM download_history WHERE fileId = :fileId")
    suspend fun getByFileId(fileId: String): DownloadHistoryEntity?


    @Query("SELECT * FROM download_history WHERE fileType = :fileType ORDER BY downloadedAt DESC")
    fun getByFileType(fileType: String): Flow<List<DownloadHistoryEntity>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadHistoryEntity)


    @Query("DELETE FROM download_history WHERE downloadedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)


    @Query("DELETE FROM download_history WHERE status = 'FAILED' AND downloadedAt < :timestamp")
    suspend fun deleteFailedOlderThan(timestamp: Long)


    @Query("DELETE FROM download_history WHERE fileId = :fileId")
    suspend fun deleteByFileId(fileId: String)


    @Query("UPDATE download_history SET status = :status WHERE fileId = :fileId")
    suspend fun  updateStatusToFileId(fileId: String, status : DownloadStatus)
}
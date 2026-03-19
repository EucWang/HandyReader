package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.ReadingActiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingActivityDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(readingActivity: ReadingActiveEntity)

    @Query("SELECT * FROM reading_activities WHERE date = :date")
    suspend fun getReadingActivityByDate(date: Long): ReadingActiveEntity?

    @Query("SELECT SUM(readingTime) FROM reading_activities")
    suspend fun getTotalReadingTime(): Long?


    @Query("SELECT * FROM reading_activities")
    fun getAllReadingActivities(): Flow<List<ReadingActiveEntity>>

    /**
     * 原子性增加阅读活动时长
     * 使用 SQL 的增量更新避免读-修改-写竞态条件
     * 
     * @param date 日期（毫秒）
     * @param delta 增量（毫秒）
     * @return 影响的行数
     */
    @Query("UPDATE reading_activities SET readingTime = readingTime + :delta WHERE date = :date")
    suspend fun incrementReadingTime(date: Long, delta: Long): Int

}
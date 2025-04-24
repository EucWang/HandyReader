package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.model.ReadingActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingActivityDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(readingActivity: ReadingActivity)

    @Query("SELECT * FROM reading_activities WHERE date = :date")
    suspend fun getReadingActivityByDate(date: Long): ReadingActivity?

    @Query("SELECT SUM(readingTime) FROM reading_activities")
    suspend fun getTotalReadingTime(): Long?


    @Query("SELECT * FROM reading_activities")
    fun getAllReadingActivities(): Flow<List<ReadingActivity>>


}
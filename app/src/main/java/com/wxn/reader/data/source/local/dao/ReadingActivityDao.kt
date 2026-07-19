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

    /** 旧 API(单列 PK):已废弃,v9 起改用复合 PK (date, deviceId)。 */
    @Deprecated("v9 起复合 PK (date, deviceId),改用 getReadingActivityByDateAndDevice", ReplaceWith("getReadingActivityByDateAndDevice(date, deviceId)"))
    @Query("SELECT * FROM reading_activities WHERE date = :date LIMIT 1")
    suspend fun getReadingActivityByDate(date: Long): ReadingActiveEntity?

    /** v9 新 API:按 date + deviceId 取本机/某设备的活动记录。 */
    @Query("SELECT * FROM reading_activities WHERE date = :date AND deviceId = :deviceId LIMIT 1")
    suspend fun getReadingActivityByDateAndDevice(date: Long, deviceId: String): ReadingActiveEntity?

    @Query("SELECT * FROM reading_activities")
    fun getAllReadingActivities(): Flow<List<ReadingActiveEntity>>

    @Query("SELECT * FROM reading_activities WHERE date >= :sinceTimestamp")
    fun getReadingActivitiesSince(sinceTimestamp: Long): Flow<List<ReadingActiveEntity>>

    /** ★ v9 新增:一次性取全部(快照),备份导出用(避免循环内多次 getAll 漂移)。 */
    @Query("SELECT * FROM reading_activities")
    suspend fun getAll(): List<ReadingActiveEntity>

    /** ★ v9 新增:按 date 求所有设备行的累计时长(统计页 daily 聚合用)。 */
    @Query("SELECT COALESCE(SUM(readingTime), 0) FROM reading_activities WHERE date = :date")
    suspend fun getByDateSum(date: Long): Long

    /** ★ v9 新增:按 date 取所有设备行(合并后多设备)。 */
    @Query("SELECT * FROM reading_activities WHERE date = :date")
    suspend fun getAllByDate(date: Long): List<ReadingActiveEntity>

    /**
     * 原子性增加本机当天的阅读活动时长(v9 起按 deviceId 分行)。
     *
     * @param date 当天 0 点时间戳(本机时区)
     * @param deviceId 本机 UUID
     * @param delta 增量(毫秒)
     */
    @Query(
        "INSERT OR REPLACE INTO reading_activities (date, deviceId, readingTime) " +
                "VALUES(:date, :deviceId, " +
                "COALESCE((SELECT readingTime + :delta FROM reading_activities " +
                "  WHERE date = :date AND deviceId = :deviceId), :delta))"
    )
    suspend fun upsertReadingTime(date: Long, deviceId: String, delta: Long)

    /** 远端行 upsert(合并引擎用,直接 REPLACE)。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemote(entity: ReadingActiveEntity)
}
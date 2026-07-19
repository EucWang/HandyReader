package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.SyncQueueEntity

/**
 * sync_queue DAO。一期建表但不写入不读取(装饰器在 isSyncEnabled=false 时短路)。
 * 二期激活后,装饰器写脏标记 → SyncWorker 拉取消费。
 *
 * ★ 同步方案 §1.1.2 / §3.2.1。
 */
@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue ORDER BY queuedAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE stableId = :stableId AND scope = :scope")
    suspend fun delete(stableId: String, scope: String): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int
}

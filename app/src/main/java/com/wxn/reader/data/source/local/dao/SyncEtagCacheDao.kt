package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.SyncEtagCacheEntity

/**
 * sync_etag_cache DAO。一期建表不使用,二期 WebDAV ETag 乐观锁增量发现激活。
 *
 * ★ 同步方案 §1.1.2 / v2.6 §7.3.5。
 */
@Dao
interface SyncEtagCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncEtagCacheEntity)

    @Query("SELECT * FROM sync_etag_cache WHERE resourceKey = :key")
    suspend fun get(key: String): SyncEtagCacheEntity?

    @Query("DELETE FROM sync_etag_cache WHERE resourceKey = :key")
    suspend fun delete(key: String): Int

    @Query("DELETE FROM sync_etag_cache")
    suspend fun clear()
}

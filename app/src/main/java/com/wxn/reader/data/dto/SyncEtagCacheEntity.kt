package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * sync_etag_cache 表(一期建表不使用,二期 WebDAV ETag 乐观锁增量发现用)。
 *
 * 二期 SyncManager 通过远端 PROPFIND 拿到 ETag 后比对 cache,
 * 仅当 ETag 变化才下载,实现增量发现。一期建表占位,二期零 Migration 激活。
 *
 * ★ 同步方案文档 v2.6 §7.3.5;一期 §1.1.2 列入"建表不使用"。
 */
@Entity(
    tableName = "sync_etag_cache",
    indices = [
        Index(value = ["resourceKey"], unique = true)
    ]
)
data class SyncEtagCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val resourceKey: String,
    val etag: String?,
    val lastChecked: Long,
)

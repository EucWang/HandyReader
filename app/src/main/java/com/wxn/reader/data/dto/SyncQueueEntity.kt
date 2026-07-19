package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * sync_queue 表(一期建表不写入,二期激活同步用)。
 *
 * 装饰器 [com.wxn.reader.data.repository.SyncableBooksRepository] 在每次本地写操作后,
 * 若 `SyncPreferencesUtil.isSyncEnabled() == true`(二期开启),向此表 upsert 一条脏标记,
 * 供二期 SyncWorker 拉取后增量同步。一期 `isSyncEnabled()` 恒为 false,装饰器短路不写。
 *
 * stableId: 书籍稳定标识(由 fileType + contentHash 派生,见 [com.wxn.reader.data.backup.StableIdResolver])。
 * scope:   同步作用域(见 [com.wxn.reader.data.remote.sync.canonical.SyncScope])。
 * op:      操作类型 UPSERT / DELETE。
 *
 * ★ 同步方案文档 v2.6 §4.2;一期 §1.1.2 列入"建表不写入"。
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["stableId", "scope", "op"], unique = true)
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stableId: String,
    val scope: String,
    val op: String,
    val queuedAt: Long = System.currentTimeMillis(),
)

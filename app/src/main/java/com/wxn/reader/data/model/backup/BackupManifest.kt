package com.wxn.reader.data.model.backup

import com.wxn.base.bean.sync.HlcTimestamp
import kotlinx.serialization.Serializable

/** ★ v1.4 一般-F7:BackupManifest 版本常量,加字段时升。 */
const val CURRENT_BACKUP_SCHEMA = 5

/** 封面同步引入的 schema 版本(低于此版本无 covers/ entry,导入跳过封面分支)。 */
const val COVER_SYNC_SCHEMA = 3

/**
 * 一期专用备份 manifest(区别于二期 v2.6 §7.4.2 的多设备清单)。
 *
 * - [deviceId]:= 本机 UUID(P0-4 本机还原判定用,§3.3.1)。
 * - [sourceDeviceHlc]:备份时本机 HLC 快照(P0-2 还原 receive 用,§6.2.2)。
 * - [coverCount] / [coverTotalBytes]:封面同步方案新增,用于容量预估与 diff 展示。
 *
 * ★ v1.4 一般-F7:版本演进策略——任何字段新增/语义变更必须升 [CURRENT_BACKUP_SCHEMA]。
 * ★ schema 3 (封面同步方案):新增 covers/<stableId>.<ext> entry + 本字段。
 * ★ schema 4 (orphan 提升):BookIdentity 新增 crc 字段,用于 FAB 导入时按 crc+fileType+title 三键提升 sync_orphan 行。
 */
@Serializable
data class BackupManifest(
    val schemaVersion: Int = CURRENT_BACKUP_SCHEMA,
    val appVersion: String,
    val createdAt: Long,
    val deviceName: String,
    val deviceId: String,
    val sourceDeviceHlc: HlcTimestamp,
    val counts: BackupCounts,
    /** 封面同步:schema 3+ 备份包含的封面数。 */
    val coverCount: Int = 0,
    /** 封面同步:schema 3+ 备份中所有封面文件总字节数(容量预估用)。 */
    val coverTotalBytes: Long = 0L,
)

/** ★ v1.3 严重-5:拆 notes/annotations/bookmarks(对齐 RestoreConfirmContent 渲染)。 */
@Serializable
data class BackupCounts(
    val books: Int,
    val notes: Int,
    val annotations: Int,
    val bookmarks: Int,
    val shelves: Int,
    val bookShelfRelations: Int,
    val vocabulary: Int,
    val readingActivities: Int,
)

package com.wxn.reader.data.model.backup

import androidx.annotation.StringRes
import com.wxn.reader.R
import kotlinx.serialization.Serializable

/**
 * ★ 严重-6:一期专用 BackupErrorCode 枚举(排除 v2.6 WebDAV 相关码)。
 *
 * 每个码对应 [resId] 中的 string 资源 ID，UI 用 `stringResource(resId)` 渲染。
 *
 * ★ C1 修复(封面同步方案)：`resId` 从 `String` 改为 `@StringRes Int`，修复"11 种错误码在
 * UI 全部塌缩为 backup_err_unknown"的断链问题。新增 `COVER_SYNC_PARTIAL`(封面同步部分失败)。
 *
 * ★ P1-4:`@Serializable` 使 [BookSyncFailure] 可携带 errorCode 持久化到备份/还原结果。
 *
 * ★ 同步方案 §8.1 / §8.1.1。
 */
@Serializable
enum class BackupErrorCode(@StringRes val resId: Int) {
    SAF_PERMISSION_DENIED(R.string.backup_err_saf_permission),
    SAF_WRITE_FAILED(R.string.backup_err_saf_write),
    HASH_PARTIAL(R.string.backup_err_hash_partial),
    HASH_ALL_FAILED(R.string.backup_err_hash_all_failed),
    ZIP_CORRUPT(R.string.backup_err_zip_corrupt),
    MANIFEST_MISSING(R.string.backup_err_manifest_missing),
    MANIFEST_CORRUPT(R.string.backup_err_manifest_corrupt),
    SCHEMA_TOO_HIGH(R.string.backup_err_schema_too_high),
    MERGE_PARTIAL_FAILED(R.string.backup_err_merge_partial),
    STORAGE_INSUFFICIENT(R.string.backup_err_storage),
    COVER_SYNC_PARTIAL(R.string.backup_err_cover_sync_partial),
    UNKNOWN(R.string.backup_err_unknown);

    companion object {
        /** 从异常推断错误码(细化 IOException/SerializationException 等)。 */
        fun fromException(e: Throwable): BackupErrorCode = when (e) {
            is SecurityException -> SAF_PERMISSION_DENIED
            is java.util.zip.ZipException -> ZIP_CORRUPT
            is StorageInsufficientException -> STORAGE_INSUFFICIENT
            // TODO("细化 IOException:SAF provider 网络异常 vs 本地磁盘满 vs SAF 写失败")
            is java.io.IOException -> SAF_WRITE_FAILED
            is kotlinx.serialization.SerializationException -> MANIFEST_CORRUPT
            else -> UNKNOWN
        }
    }
}

/** 导入封面累计失败达阈值时抛出，触发 STORAGE_INSUFFICIENT 中止（S6）。 */
class StorageInsufficientException(message: String = "storage insufficient") : java.io.IOException(message)

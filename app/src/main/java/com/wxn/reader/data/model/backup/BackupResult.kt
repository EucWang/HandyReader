package com.wxn.reader.data.model.backup

import kotlinx.serialization.Serializable

/** ★ v1.3 严重-5:还原前 diff 报告(ConfirmRestore 态渲染)。 */
@Serializable
data class RestoreDiff(
    val deviceName: String,
    val createdAt: Long,
    val backupBooks: Int,
    val backupNotes: Int,
    val backupHighlights: Int,
    val backupBookmarks: Int,
    val localBooks: Int,
    val matched: Int,
    val newOrphan: Int,
    val deletedTombstones: Int,
)

/**
 * 单本书同步失败信息(用于 PartialFail)。
 *
 * ★ P1-4:`message`(系统语言 e.message,无 i18n)替换为 [errorCode](@StringRes 可 i18n),
 *   UI 用 `stringResource(errorCode.resId)` 渲染本地化文案。
 */
@Serializable
data class BookSyncFailure(
    val entryName: String,
    val errorCode: BackupErrorCode,
)

/** 备份/还原结果。 */
sealed interface BackupResult {
    data class Success(val manifest: BackupManifest, val skippedBooks: List<String> = emptyList()) : BackupResult
    data class PartialFail(val successCount: Int, val failures: List<BookSyncFailure>) : BackupResult
    data class Failed(val errorCode: BackupErrorCode, val message: String) : BackupResult
    data object Cancelled : BackupResult
}

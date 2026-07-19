package com.wxn.reader.data.backup

import com.wxn.reader.data.model.backup.BackupErrorCode
import com.wxn.reader.data.model.backup.BackupResult
import com.wxn.reader.data.model.backup.BookSyncFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ★ P1-4:验证 [BookSyncFailure] 数据模型从 `message:String` 改为 `errorCode:BackupErrorCode` 后的一致性。
 *
 * ★ Stage 0 顶层 catch 的完整集成测试(非 zip 文件 → Failed(ZIP_CORRUPT) 且 emitter 落到 Done)
 *   需要 ContentResolver + 自定义 ContentProvider,留作 instrumentation 测试。
 *   此处覆盖数据模型层 + fromException 映射的契约。
 */
@RunWith(RobolectricTestRunner::class)
class BackupImporterTest {

    @Test
    fun book_sync_failure_carries_error_code_not_message() {
        // ★ P1-4:BookSyncFailure 必须携带 errorCode(可 i18n),而非 message(系统语言)
        val failure = BookSyncFailure("books/test.json", BackupErrorCode.MANIFEST_CORRUPT)
        assertEquals("books/test.json", failure.entryName)
        assertEquals(BackupErrorCode.MANIFEST_CORRUPT, failure.errorCode)
    }

    @Test
    fun partial_fail_result_preserves_failure_list() {
        val failures = listOf(
            BookSyncFailure("books/a.json", BackupErrorCode.SAF_WRITE_FAILED),
            BookSyncFailure("books/b.json", BackupErrorCode.UNKNOWN),
        )
        val result = BackupResult.PartialFail(successCount = 3, failures = failures)
        assertEquals(3, result.successCount)
        assertEquals(2, result.failures.size)
        assertEquals(BackupErrorCode.SAF_WRITE_FAILED, result.failures[0].errorCode)
        assertEquals(BackupErrorCode.UNKNOWN, result.failures[1].errorCode)
    }

    @Test
    fun failed_result_carries_error_code_from_exception() {
        // ★ Stage 0:import() 顶层 catch 用 fromException 映射异常
        val code = BackupErrorCode.fromException(java.util.zip.ZipException("not a zip"))
        val result = BackupResult.Failed(code, "not a zip")
        assertEquals(BackupErrorCode.ZIP_CORRUPT, result.errorCode)
    }

    @Test
    fun every_backup_error_code_has_nonzero_res_id() {
        // ★ P1-4:UI 用 stringResource(errorCode.resId) 渲染,所有码必须有有效 resId
        BackupErrorCode.entries.forEach { code ->
            assertTrue(
                "errorCode $code must have non-zero resId",
                code.resId != 0,
            )
        }
    }
}

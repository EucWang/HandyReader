package com.wxn.reader.data.model.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.zip.ZipException

/**
 * ★ P1-4 / Stage 0:验证 [BackupErrorCode.fromException] 的异常→错误码映射。
 *
 * ★ 覆盖 Stage 0 顶层 catch 依赖的映射正确性(ZipException → ZIP_CORRUPT 等),
 *   确保非 zip 文件 / 权限丢失 / manifest 损坏等场景在 UI 正确显示 i18n 文案。
 */
@RunWith(RobolectricTestRunner::class)
class BackupErrorCodeTest {

    @Test
    fun security_exception_maps_to_saf_permission_denied() {
        assertEquals(
            BackupErrorCode.SAF_PERMISSION_DENIED,
            BackupErrorCode.fromException(SecurityException("denied")),
        )
    }

    @Test
    fun zip_exception_maps_to_zip_corrupt() {
        assertEquals(
            BackupErrorCode.ZIP_CORRUPT,
            BackupErrorCode.fromException(ZipException("bad zip")),
        )
    }

    @Test
    fun storage_insufficient_maps_before_io_exception() {
        // ★ StorageInsufficientException extends IOException — 必须先于 IOException 匹配
        assertEquals(
            BackupErrorCode.STORAGE_INSUFFICIENT,
            BackupErrorCode.fromException(StorageInsufficientException("disk full")),
        )
    }

    @Test
    fun io_exception_maps_to_saf_write_failed() {
        assertEquals(
            BackupErrorCode.SAF_WRITE_FAILED,
            BackupErrorCode.fromException(IOException("read failed")),
        )
    }

    @Test
    fun serialization_exception_maps_to_manifest_corrupt() {
        assertEquals(
            BackupErrorCode.MANIFEST_CORRUPT,
            BackupErrorCode.fromException(
                kotlinx.serialization.SerializationException("bad json"),
            ),
        )
    }

    @Test
    fun sqlite_exception_maps_to_unknown() {
        // ★ MVP 接受 UNKNOWN:SQLiteException extends AndroidRuntimeException, 非 IOException
        assertEquals(
            BackupErrorCode.UNKNOWN,
            BackupErrorCode.fromException(
                android.database.sqlite.SQLiteException("UNIQUE constraint"),
            ),
        )
    }

    @Test
    fun runtime_exception_maps_to_unknown() {
        assertEquals(
            BackupErrorCode.UNKNOWN,
            BackupErrorCode.fromException(RuntimeException("unexpected")),
        )
    }
}

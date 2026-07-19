package com.wxn.reader.data.source.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机 deviceId 持久化(应用自管 UUID,不用 AndroidId/IMEI)。
 *
 * - 首次惰性生成 `UUID.randomUUID().toString()`(8-4-4-4-12 带连字符),与运行时/Migration 同源。
 * - 持久化到 EncryptedSharedPreferences(清数据/卸载重装 = 视为新设备)。
 * - `prefs` 用 `by lazy`,首次 `getOrCreateLocalDeviceId()` 才构造,降低冷启动影响(建议-F3)。
 *
 * ★ 同步方案 §3.1.3;Q4 已定:重装=新设备,合理。
 *
 * 使用点:HLC 的 `now()`/`receive()`、reading_activities/book_reading_time 的本机行 deviceId、
 *         BackupManifest.deviceId、Migration_8_9 回填。
 */
@Singleton
class DeviceLocalStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /** `by lazy`:首次 `getOrCreateLocalDeviceId()` 才构造(密钥派生 50-200ms,建议-F3)。 */
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "device_local_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * 首次调用惰性生成 UUID 并持久化;之后每次读同一值。
     * `@Synchronized` 防首次并发调用生成两个 UUID。
     * Migration_8_9 也调此方法(同步调用安全)。
     */
    @Synchronized
    fun getOrCreateLocalDeviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString()
                .also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    private companion object {
        const val KEY_DEVICE_ID = "local_device_id"
    }
}

package com.wxn.reader.util.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wxn.base.bean.sync.HlcTimestamp
import com.wxn.reader.data.source.local.DeviceLocalStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * HLC(Hybrid Logical Clock)实现:跨设备因果关系排序。
 *
 * - `now()`:本地事件推进时钟,返回新 HlcTimestamp。
 * - `receive(remote)`:收到远端事件后,本地时钟向远端时钟对齐(防止远端事件被本地旧事件"覆盖")。
 * - 持久化:`l`/`c` 存 EncryptedSharedPreferences;`deviceId` 来自 [DeviceLocalStore]。
 * - 落盘时机(★ v1.4 一般-F8):App onStop 兜底 + 备份/还原结束。
 *   `dirty` flag 避免高频 `now()` 每次都写 SharedPreferences。
 *
 * ★ 同步方案 v2.6 §7.3.2 / 一期 §3.1.2。
 */
@Singleton
class HybridLogicalClock @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceLocalStore: DeviceLocalStore,
) {
    private val mutex = Mutex()

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "hlc_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile private var l: Long = prefs.getLong(KEY_L, 0L)

    @Volatile private var c: Int = prefs.getInt(KEY_C, 0)

    @Volatile private var deviceId: String = deviceLocalStore.getOrCreateLocalDeviceId()

    /** ★ v1.4 一般-F8:脏标记,避免高频 now() 每次都写 SharedPreferences。 */
    @Volatile private var dirty: Boolean = false

    /** 本地事件推进时钟。 */
    suspend fun now(): HlcTimestamp = mutex.withLock {
        val wallClock = System.currentTimeMillis()
        if (wallClock > l) {
            l = wallClock
            c = 0
        } else {
            c += 1
        }
        dirty = true
        HlcTimestamp(l, c, deviceId)
    }

    /** 收到远端事件后,本地时钟向远端时钟对齐。 */
    suspend fun receive(remote: HlcTimestamp) = mutex.withLock {
        val wallClock = System.currentTimeMillis()
        l = maxOf(l, remote.l, wallClock)
        c = when {
            l == remote.l && l == wallClock -> maxOf(c, remote.c) + 1
            l == remote.l -> maxOf(c, remote.c) + 1
            l == wallClock -> c + 1
            else -> remote.c + 1
        }
        dirty = true
    }

    /** 当前时钟快照(不推进)。 */
    suspend fun current(): HlcTimestamp = mutex.withLock { HlcTimestamp(l, c, deviceId) }

    /** ★ v1.4 一般-F8:仅脏时落盘。备份/还原 finally 块调用。 */
    suspend fun persistIfDirty() = mutex.withLock {
        if (dirty) {
            prefs.edit().putLong(KEY_L, l).putInt(KEY_C, c).apply()
            dirty = false
        }
    }

    /** 强制落盘(App onStop 兜底,无视 dirty)。 */
    suspend fun persist() = mutex.withLock {
        prefs.edit().putLong(KEY_L, l).putInt(KEY_C, c).apply()
        dirty = false
    }

    private companion object {
        const val KEY_L = "hlc_l"
        const val KEY_C = "hlc_c"
    }
}

/**
 * 计算两个 HLC 的"接收"合并(用于 mergeEngine 推进本地 HLC)。
 * 不是 [HybridLogicalClock.receive](那会改变本地时钟);这是纯函数,返回合并后的 HLC。
 */
fun hlcReceive(local: HlcTimestamp, remote: HlcTimestamp): HlcTimestamp {
    val wallClock = System.currentTimeMillis()
    val newL = maxOf(local.l, remote.l, wallClock)
    val newC = when {
        newL == local.l && newL == remote.l -> max(local.c, remote.c) + 1
        newL == local.l -> local.c + 1
        newL == remote.l -> remote.c + 1
        else -> 0
    }
    return HlcTimestamp(newL, newC, local.deviceId)
}

package com.wxn.reader.data.remote.sync.merge

import com.wxn.base.bean.sync.HlcTimestamp
import com.wxn.reader.data.remote.sync.merge.SyncMergeEngine.FieldWinner
import com.wxn.reader.data.remote.sync.merge.resolveReadingWinner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SyncMergeEngine.resolveReadingWinner] 单元测试。

 * 这是对「备份还原后阅读进度不更新」bug 的回归保护。
 *
 * 旧实现用 progression(阅读位置百分比)当主决胜键;由于 progression 非单调
 * (用户回翻/跳章/重读),设备 B 时间更新但 progression 更低时会被判输,
 * 导致还原后 locator/lastOpened/scrollIndex 全部不更新。
 *
 * 现统一为 HLC 时间戳决胜,与 meta/user 档语义对齐。
 *
 * 纯 JVM JUnit,无 Android/Room 依赖。
 */
class ReadingWinnerResolverTest {

    private fun hlc(l: Long, c: Int = 0, deviceId: String = "dev") = HlcTimestamp(l, c, deviceId)

    @Test
    fun `regression - remote newer but lower progression wins`() {
        // 用户场景:设备 A 之前读到 80%,设备 B 最近翻回 20%(时间更新)。
        // 修复前:remote progression(20) < local(80) → LOCAL 赢 → bug。
        // 修复后:remote HLC 更新 → REMOTE 赢。
        val localHlc = hlc(1_000L, deviceId = "A")
        val remoteHlc = hlc(2_000L, deviceId = "B")
        assertEquals(FieldWinner.REMOTE, resolveReadingWinner(remoteHlc, localHlc))
    }

    @Test
    fun `remote newer and any progression wins - baseline forward case`() {
        // 基线正向:remote 时间新,无论 progression 高低都该赢。
        val localHlc = hlc(1_000L, deviceId = "A")
        val remoteHlc = hlc(5_000L, deviceId = "B")
        assertEquals(FieldWinner.REMOTE, resolveReadingWinner(remoteHlc, localHlc))
    }

    @Test
    fun `local newer wins`() {
        // 本地时间更新 → 本地赢(远端不应覆盖更新数据)。
        val localHlc = hlc(3_000L, deviceId = "A")
        val remoteHlc = hlc(1_000L, deviceId = "B")
        assertEquals(FieldWinner.LOCAL, resolveReadingWinner(remoteHlc, localHlc))
    }

    @Test
    fun `equal HLC with different physical time but same counter - remote wins tie`() {
        // 同 l 同 c 同 deviceId(平局)→ 取 REMOTE(LWW 确定性 tie-break)。
        val localHlc = hlc(1_000L, c = 5, deviceId = "same")
        val remoteHlc = hlc(1_000L, c = 5, deviceId = "same")
        assertEquals(FieldWinner.REMOTE, resolveReadingWinner(remoteHlc, localHlc))
    }

    @Test
    fun `equal l and c, deviceId tie-break is deterministic`() {
        // 同 l 同 c 不同 deviceId → 按 deviceId 字典序决胜(Comparable 语义)。
        // remote deviceId "A" < local deviceId "B" → remote < local → LOCAL 赢。
        val localHlc = hlc(1_000L, c = 5, deviceId = "B")
        val remoteHlc = hlc(1_000L, c = 5, deviceId = "A")
        assertEquals(FieldWinner.LOCAL, resolveReadingWinner(remoteHlc, localHlc))
    }

    @Test
    fun `same l different c - higher counter wins`() {
        // 同一毫秒内,逻辑计数器高的赢。
        val localHlc = hlc(1_000L, c = 3)
        val remoteHlc = hlc(1_000L, c = 7)
        assertEquals(FieldWinner.REMOTE, resolveReadingWinner(remoteHlc, localHlc))

        val localHlc2 = hlc(1_000L, c = 9)
        val remoteHlc2 = hlc(1_000L, c = 2)
        assertEquals(FieldWinner.LOCAL, resolveReadingWinner(remoteHlc2, localHlc2))
    }

    @Test
    fun `both zero HLC - remote wins tie`() {
        // 新 orphan(本地无阅读记录,Hlc=ZERO)对远端 ZERO:平局取 REMOTE。
        val localHlc = HlcTimestamp.ZERO
        val remoteHlc = HlcTimestamp.ZERO
        assertEquals(FieldWinner.REMOTE, resolveReadingWinner(remoteHlc, localHlc))
    }
}

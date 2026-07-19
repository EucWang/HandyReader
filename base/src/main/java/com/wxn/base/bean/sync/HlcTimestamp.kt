package com.wxn.base.bean.sync

import kotlinx.serialization.Serializable

/**
 * HLC(Hybrid Logical Clock)时间戳,跨设备因果关系排序用。
 *
 * - [l]:wall clock 物理时间(毫秒),保证跨设备粗粒度因果。
 * - [c]:逻辑计数器,同一毫秒内单调递增,打破物理时钟平局。
 * - [deviceId]:本机 UUID,平局时按设备标识决胜(确定性)。
 *
 * 比较语义:先比 [l],再比 [c],最后比 [deviceId](字典序)。
 * 该类是纯数据类,放 base 模块以便 app/bookread 等多个模块共享;
 * 真正的 [com.wxn.reader.util.sync.HybridLogicalClock] 实现(含 Mutex + 持久化)位于 app 模块。
 *
 * ★ 同步方案文档:本期首次显式定义(v2.6 仅有隐式契约)。
 */
@Serializable
data class HlcTimestamp(
    val l: Long,
    val c: Int,
    val deviceId: String,
) : Comparable<HlcTimestamp> {
    override fun compareTo(other: HlcTimestamp): Int {
        val byL = l.compareTo(other.l)
        if (byL != 0) return byL
        val byC = c.compareTo(other.c)
        if (byC != 0) return byC
        return deviceId.compareTo(other.deviceId)
    }

    companion object {
        /** 零值(用于未初始化或回退默认值,deviceId 为空串)。 */
        val ZERO: HlcTimestamp = HlcTimestamp(0L, 0, "")
    }
}

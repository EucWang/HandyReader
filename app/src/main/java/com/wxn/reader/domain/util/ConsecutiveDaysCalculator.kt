package com.wxn.reader.domain.util

import com.wxn.base.util.DateUtil
import com.wxn.reader.domain.model.ReadingActive

/**
 * 计算从今天往前连续满足"每日≥阈值"的天数，遇不满足立即中断。今天可不足。
 *
 * 注：不处理跨时区旅行场景（date 按写入时本地时区，读取按当前时区归一化）。
 * 与 MainReadViewModel 写入侧保持同一 Calendar 实现，DST 偏移由 groupBy startOfDay 容错。
 * 多设备场景：同一天可能有多行记录（不同设备），按 date 聚合求和。
 */
object ConsecutiveDaysCalculator {
    fun calc(activities: List<ReadingActive>, minMillisPerDay: Long): Int {
        if (activities.isEmpty()) return 0
        val dailyTotals = activities.groupBy { DateUtil.startOfDay(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.readingTime } }
        val today = DateUtil.startOfDay(System.currentTimeMillis())
        val todayMs = dailyTotals[today] ?: 0L
        var cursor = if (todayMs >= minMillisPerDay) today else today - DateUtil.DAY_MS
        var consecutive = 0
        repeat(10) {
            val total = dailyTotals[cursor] ?: 0L
            if (total >= minMillisPerDay) {
                consecutive++; cursor -= DateUtil.DAY_MS
            } else return consecutive
        }
        return consecutive
    }
}

package com.wxn.base.util

import java.util.Calendar

/**
 * 日期工具。
 *
 * `reading_activities` 表的 day bucket 统一用本地时区午夜毫秒，
 * 写入侧（[com.wxn.reader.presentation.mainReader.MainReadViewModel]）与读取侧
 * （好评弹窗条件2 `ReviewPromptManager.checkConsecutiveDaysTrigger`）必须共用本函数，保证日界一致。
 *
 * 注：不处理跨时区/DST 切换，与 MainReadViewModel 写入侧保持同一 Calendar 实现。
 */
object DateUtil {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** 取 [timestampMs] 所在自然日的本地午夜毫秒（与 MainReadViewModel 写入侧一致）。 */
    fun startOfDay(timestampMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

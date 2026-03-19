package com.wxn.reader.domain.use_case.reading_activity

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

/**
 * 原子性增加阅读活动时长用例
 * 
 * 使用 SQL 原子性增量更新，避免读-修改-写竞态条件
 */
class IncrementReadingActivityTimeUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    /**
     * 原子性增加阅读活动时长
     * 
     * @param date 日期（毫秒）
     * @param deltaMs 时间增量（毫秒）
     * @return 影响的行数
     */
    suspend operator fun invoke(date: Long, deltaMs: Long): Int {
        return repository.incrementReadingActivityTime(date, deltaMs)
    }
}

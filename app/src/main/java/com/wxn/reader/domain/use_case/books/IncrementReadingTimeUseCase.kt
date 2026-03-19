package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

/**
 * 原子性增加书籍阅读时间用例
 * 
 * 使用 SQL 原子性增量更新，避免读-修改-写竞态条件
 */
class IncrementReadingTimeUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    /**
     * 原子性增加书籍阅读时间
     * 
     * @param bookId 书籍ID
     * @param deltaMs 时间增量（毫秒）
     * @return 影响的行数
     */
    suspend operator fun invoke(bookId: Long, deltaMs: Long): Int {
        return repository.incrementReadingTime(bookId, deltaMs)
    }
}

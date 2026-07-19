package com.wxn.reader.domain.use_case.chapters

import com.wxn.base.bean.BookChapter
import com.wxn.reader.domain.repository.ChaptersRepository
import javax.inject.Inject

/***
 * 原子替换指定书籍的全部章节(单事务 delete + insert)。
 *
 * 用于脏数据自动失效场景([MainReadViewModel] 加载分支):当检测到 DB 章节索引非法
 * (负值/重复/断号 —— 旧 vsplit bug 的典型特征)时,先从书文件重新解析得到新章节,
 * 再通过本 UseCase 原子替换,避免 delete/insert 中间态被并发 Flow 收集器读到。
 */
class ReplaceChaptersByBookIdUseCase @Inject constructor(
    private val repository: ChaptersRepository
) {
    suspend operator fun invoke(bookId: Long, chapters: List<BookChapter>) {
        repository.replaceChapters(bookId, chapters)
    }
}

package com.wxn.reader.domain.use_case.chapters

import com.wxn.reader.domain.repository.ChaptersRepository
import javax.inject.Inject

/***
 * 删除指定书籍的全部章节。
 * 用于章节结构陈旧（如解析器升级后旧章节 type 缺失）时，清空旧数据以便重新解析。
 */
class DeleteChaptersByBookIdUseCase @Inject constructor(
    private val repository: ChaptersRepository
) {
    suspend operator fun invoke(bookId: Long) {
        repository.deleteChaptersByBookId(bookId)
    }
}

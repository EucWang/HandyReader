package com.wxn.reader.domain.use_case.chapters

import com.wxn.base.bean.BookChapter

/***
 * 章节索引连续性校验。
 *
 * 合法的章节索引排序后必须严格等于 0,1,2,...,size-1(无负值、无重复、无断号)。
 *
 * 典型的非法场景:旧版 vsplit bug 导致 54 个虚拟切分章的 chapterIndex 全为 -1
 * (playOrder 未初始化,JNI 计算 playOrder-1 得到脏值)。此时 [isValid] 返回 false,
 * 触发上层「先解析后删写」的脏数据自动失效流程([ReplaceChaptersByBookIdUseCase])。
 */
object ChaptersIndexValidator {

    fun isValid(chapters: List<BookChapter>): Boolean {
        if (chapters.isEmpty()) return false
        var expected = 0
        return chapters
            .map { it.chapterIndex }
            .sorted()
            .all { it == expected++ }
    }
}

package com.wxn.reader.domain.use_case.chapters

import com.wxn.base.bean.BookChapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChaptersIndexValidator] 单元测试。
 *
 * 覆盖:空列表 / 含负值 / 含重复 / 含断号 / 合法连续 / 乱序输入(应排序后判合法)。
 * 这直接回归旧 vsplit bug 的脏数据特征(chapterIndex 全为 -1)。
 */
class ChaptersIndexValidatorTest {

    private fun chapter(index: Int) = BookChapter(bookId = 1L, chapterIndex = index, chapterName = "c$index")

    @Test
    fun `empty list is invalid`() {
        assertFalse(ChaptersIndexValidator.isValid(emptyList()))
    }

    @Test
    fun `single chapter at index 0 is valid`() {
        assertTrue(ChaptersIndexValidator.isValid(listOf(chapter(0))))
    }

    @Test
    fun `contiguous 0 to N-1 is valid`() {
        val list = (0..54).map { chapter(it) }
        assertTrue(ChaptersIndexValidator.isValid(list))
    }

    @Test
    fun `out-of-order but complete set is valid (sorted internally)`() {
        // 模拟 DB 返回顺序未必有序(虽然 DAO ORDER BY,这里防御性测试)
        val list = listOf(chapter(2), chapter(0), chapter(1))
        assertTrue(ChaptersIndexValidator.isValid(list))
    }

    @Test
    fun `negative indices are invalid - regression for stale vsplit bug`() {
        // 旧 vsplit bug 的典型特征:所有虚拟章 chapterIndex = -1
        val list = listOf(chapter(0)) + List(54) { chapter(-1) }
        assertFalse(ChaptersIndexValidator.isValid(list))
    }

    @Test
    fun `duplicate indices are invalid`() {
        val list = listOf(chapter(0), chapter(1), chapter(1), chapter(2))
        assertFalse(ChaptersIndexValidator.isValid(list))
    }

    @Test
    fun `gap in sequence is invalid`() {
        // 0,1,3 —— 缺 2
        val list = listOf(chapter(0), chapter(1), chapter(3))
        assertFalse(ChaptersIndexValidator.isValid(list))
    }

    @Test
    fun `does not start at 0 is invalid`() {
        val list = (1..5).map { chapter(it) }
        assertFalse(ChaptersIndexValidator.isValid(list))
    }
}

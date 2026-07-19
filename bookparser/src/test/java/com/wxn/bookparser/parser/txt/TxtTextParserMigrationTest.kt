package com.wxn.bookparser.parser.txt

import com.wxn.base.bean.BookChapter
import org.junit.Assert.*
import org.junit.Test

/**
 * 纯 JVM 单元测试（无 Robolectric 依赖），直接调用顶层 [needsRescanForMigration] 函数。
 *
 * **v2 修复**：删除原"镜像纯逻辑"反模式（原实现复制了一份相同逻辑到测试里，用注释提醒同步，
 * 但没有任何机械保护——历史上这种反模式曾导致 P0 bug 长期未被发现，详见
 * `docs/reviews/2026-07-18-txt-chapter-scanner-review-of-review.md §D2`）。
 *
 * **测试覆盖范围**：
 * - 顶层 [needsRescanForMigration] 的逻辑（本类直接调用）
 * - [TxtTextParser.needsRescanForMigration] override（内部转发到顶层 fun）
 * - [com.wxn.bookparser.impl.TextParserImpl.needsRescanForMigration] 转发链路（由 androidTest
 *   `TextParserImplMigrationTest` 补充端到端验证）
 *
 * 之所以不构造完整的 [TxtTextParser]：构造需要 Android Context（Hilt 注入），单元测试层
 * 难以提供；而 `needsRescanForMigration` 是纯函数（不读实例字段），抽到顶层后测试可绕过
 * Context 依赖直接验证逻辑。
 */
class TxtTextParserMigrationTest {

    private fun chapter(url: String?): BookChapter =
        BookChapter(bookId = 1L, chapterIndex = 0, chapterName = "ch").apply { chapterUrl = url }

    // ── needsRescanForMigration ──

    @Test
    fun needsRescan_bPrefix_returnsFalse() {
        val chapters = listOf(
            chapter("b:0:100"),
            chapter("b:100:200")
        )
        assertFalse(needsRescanForMigration(chapters))
    }

    @Test
    fun needsRescan_oldLineOffsetFormat_returnsTrue() {
        val chapters = listOf(
            chapter("0:50"),
            chapter("51:100")
        )
        assertTrue(needsRescanForMigration(chapters))
    }

    @Test
    fun needsRescan_mixedOldByteOffsetFormat_returnsTrue() {
        // 更老的 .idx 重建格式 "startByte:endByte"（无 b: 前缀），同样需要迁移
        val chapters = listOf(chapter("0:1024"))
        assertTrue(needsRescanForMigration(chapters))
    }

    @Test
    fun needsRescan_emptyList_returnsFalse() {
        assertFalse(needsRescanForMigration(emptyList()))
    }

    @Test
    fun needsRescan_nullChapterUrl_returnsFalse() {
        // 首次导入尚未写入 chapterUrl（null/空）——不算迁移目标（isEmpty 守卫会兜底）
        val chapters = listOf(chapter(null), chapter(null))
        assertFalse(needsRescanForMigration(chapters))
    }

    @Test
    fun needsRescan_emptyStringChapterUrl_returnsFalse() {
        val chapters = listOf(chapter(""))
        assertFalse(needsRescanForMigration(chapters))
    }

    @Test
    fun needsRescan_onlyChecksFirstChapter() {
        // 同一本书的 chapterUrl 是原子写入的，格式一致；只检查第一条（O(1)）。
        // 即便第二条是 b:，只要第一条不是就触发重扫（理论不应发生，但保守处理反而能修复脏数据）。
        val chapters = listOf(
            chapter("0:50"),          // 第一条非 b:
            chapter("b:100:200")      // 第二条 b:（脏数据）
        )
        assertTrue(needsRescanForMigration(chapters))
    }
}

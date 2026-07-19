package com.wxn.reader.data.backup

import com.wxn.reader.data.dto.BookEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBookFilterTest {

    private fun book(importStatus: Int): BookEntity = BookEntity(
        id = 1,
        uri = "",
        fileType = "epub",
        title = "t",
        authors = "",
        description = null,
        publishDate = null,
        publisher = null,
        language = null,
        numberOfPages = null,
        wordCount = 0L,
        subjects = null,
        coverPath = null,
        locator = "",
        progression = 0f,
        deleted = false,
        rating = 0f,
        isFavorite = false,
        readingStatus = null,
        readingTime = 0,
        scrollIndex = 0,
        scrollOffset = 0,
        cachedDir = "",
        crc = 0,
        importStatus = importStatus,
        source = "scan",
    )

    @Test
    fun normal_book_is_exported() {
        assertTrue(BackupBookFilter.shouldExport(book(importStatus = 0)))
    }

    @Test
    fun failed_book_is_skipped() {
        assertFalse(BackupBookFilter.shouldExport(book(importStatus = -1)))
    }

    @Test
    fun deleted_normal_book_still_exported_for_tombstone() {
        // deleted=true 但 importStatus=0 → 墓碑同步，仍需备份
        val tombstone = book(importStatus = 0).copy(deleted = true)
        assertTrue(BackupBookFilter.shouldExport(tombstone))
    }

    @Test
    fun deduped_book_is_skipped() {
        // 扫描去重行(source='deduped')不参与备份——见 docs/plans/2026-07-07-扫描导入同书去重.md
        val deduped = book(importStatus = -1).copy(source = "deduped")
        assertFalse(BackupBookFilter.shouldExport(deduped))
    }

    @Test
    fun deduped_book_skipped_even_if_importStatus_zero() {
        // 防御:即便未来规则调整误把 importStatus 改回 0,source='deduped' 仍应排除
        val dedupedBad = book(importStatus = 0).copy(source = "deduped")
        assertFalse(BackupBookFilter.shouldExport(dedupedBad))
    }
}

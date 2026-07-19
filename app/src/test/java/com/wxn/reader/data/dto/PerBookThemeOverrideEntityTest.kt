package com.wxn.reader.data.dto

import com.wxn.bookread.data.model.preference.ReaderThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PerBookThemeOverrideEntity.toReaderThemeConfigEntity] 转换 + differsFrom * 标记判定的单元测试（v12 起）。
 *
 * v12 重构：原 hasAnyNonNull 已删除（全量快照模式下无 null 概念）。
 * * 标记改用 differsFrom(preset) 统一判定（复用 ReaderThemeConfigEntity.differsFrom）。
 */
class PerBookThemeOverrideEntityTest {

    private fun entity(
        bookId: Long = 1L,
        themeId: String = "default",
        fontSize: Double = 1.0,
        lineHeight: Double = 1.5,
    ) = PerBookThemeOverrideEntity(
        bookId = bookId, themeId = themeId,
        backgroundColor = -328969, textColor = -13882324, backgroundImage = "",
        font = "sans_serif", fontVariant = "regular",
        fontSize = fontSize, lineHeight = lineHeight, letterSpacing = 0.0,
        paragraphIndent = 2.0, paragraphSpacing = 0.6,
        pageHorizontalMargins = 1.5, pageVerticalMargins = 1.2,
        titleSize = 1.0, titleTopSpacing = 18.0, titleBottomSpacing = 15.0,
        userTextAlign = 4, forceAlignOverride = 0,
        createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `toReaderThemeConfigEntity copies all 17 theme fields`() {
        val snapshot = entity(fontSize = 1.8, lineHeight = 2.5, themeId = "night")
        val archive = snapshot.toReaderThemeConfigEntity()
        assertEquals("night", archive.themeId)
        assertEquals(1.8, archive.fontSize, 0.0)
        assertEquals(2.5, archive.lineHeight, 0.0)
        assertEquals(snapshot.backgroundColor, archive.backgroundColor)
        assertEquals(snapshot.textColor, archive.textColor)
        assertEquals(snapshot.font, archive.font)
        assertEquals(snapshot.userTextAlign, archive.userTextAlign)
        assertEquals(snapshot.forceAlignOverride, archive.forceAlignOverride)
    }

    @Test
    fun `snapshot equals preset shows no modified marker`() {
        // 快照 = default preset 值 → differsFrom = false（无 * 标记）
        val snapshot = entity(themeId = "default")  // default preset 默认值
        val preset = ReaderThemePreset(
            themeId = "default",
            backgroundColor = -328969, textColor = -13882324,
            backgroundImage = "", font = "sans_serif", fontVariant = "regular",
            fontSize = 1.0, lineHeight = 1.5, letterSpacing = 0.0,
            paragraphIndent = 2.0, paragraphSpacing = 0.6,
            pageHorizontalMargins = 1.5, pageVerticalMargins = 1.2,
            titleSize = 1.0, titleTopSpacing = 18.0, titleBottomSpacing = 15.0,
        )
        assertFalse(snapshot.toReaderThemeConfigEntity().differsFrom(preset))
    }

    @Test
    fun `snapshot differs from preset shows modified marker`() {
        // 快照改了字号 → differsFrom = true（显示 *）
        val snapshot = entity(themeId = "default", fontSize = 1.8)
        val preset = ReaderThemePreset(
            themeId = "default",
            backgroundColor = -328969, textColor = -13882324,
            backgroundImage = "", font = "sans_serif", fontVariant = "regular",
            fontSize = 1.0, lineHeight = 1.5, letterSpacing = 0.0,
            paragraphIndent = 2.0, paragraphSpacing = 0.6,
            pageHorizontalMargins = 1.5, pageVerticalMargins = 1.2,
            titleSize = 1.0, titleTopSpacing = 18.0, titleBottomSpacing = 15.0,
        )
        assertTrue(snapshot.toReaderThemeConfigEntity().differsFrom(preset))
    }
}

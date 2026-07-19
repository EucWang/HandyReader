package com.wxn.reader.data.dto

import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * per-book 全量快照 mapper 单元测试（v12 起）。
 *
 * 覆盖 [toPerBookSnapshot]（saveCurrent）+ [toReaderPreferences]（loadSnapshot）双向转换：
 * - 往返一致性：prefs → snapshot → prefs' 应保留全部 17 主题字段
 * - 非主题字段保留：brightness/colorHistory 等从 current 保留，不被快照覆盖
 * - forceAlignOverride 类型转换（Int 0/1 ↔ Boolean）
 * - readerThemeId 正确映射（prefs 无 bookId/themeId，从 snapshot 参数取）
 *
 * 使用 Robolectric：[ReaderPreferences] 依赖 `android.graphics.Color`。
 */
@RunWith(RobolectricTestRunner::class)
class PerBookOverrideMapperTest {

    private fun baseline(): ReaderPreferences = ReaderPreferencesUtil.defaultPreferences.copy(
        readerThemeId = "default"
    )

    @Test
    fun `round-trip preserves all 17 theme fields`() {
        val original = baseline().copy(
            fontSize = 1.8,
            lineHeight = 2.5,
            letterSpacing = 0.5,
            pageHorizontalMargins = 3.0,
            pageVerticalMargins = 2.0,
            paragraphIndent = 1.5,
            paragraphSpacing = 2.0,
            textColor = 0xFF000000.toInt(),
            backgroundColor = 0xFFFFFFFF.toInt(),
            backgroundImage = "/path/to/bg.png",
            font = "/path/to/font",
            fontVariant = "bold",
            titleSize = 1.2,
            titleTopSpacing = 20.0,
            titleBottomSpacing = 16.0,
            userTextAlign = 2,
            forceAlignOverride = true,
        )
        val snapshot = original.toPerBookSnapshot(bookId = 1L, themeId = "default")
        val restored = snapshot.toReaderPreferences(baseline())
        // 17 主题字段应往返保持
        assertEquals(original.fontSize, restored.fontSize, 0.0)
        assertEquals(original.lineHeight, restored.lineHeight, 0.0)
        assertEquals(original.letterSpacing, restored.letterSpacing, 0.0)
        assertEquals(original.pageHorizontalMargins, restored.pageHorizontalMargins, 0.0)
        assertEquals(original.pageVerticalMargins, restored.pageVerticalMargins, 0.0)
        assertEquals(original.paragraphIndent, restored.paragraphIndent, 0.0)
        assertEquals(original.paragraphSpacing, restored.paragraphSpacing, 0.0)
        assertEquals(original.textColor, restored.textColor)
        assertEquals(original.backgroundColor, restored.backgroundColor)
        assertEquals(original.backgroundImage, restored.backgroundImage)
        assertEquals(original.font, restored.font)
        assertEquals(original.fontVariant, restored.fontVariant)
        assertEquals(original.titleSize, restored.titleSize, 0.0)
        assertEquals(original.titleTopSpacing, restored.titleTopSpacing, 0.0)
        assertEquals(original.titleBottomSpacing, restored.titleBottomSpacing, 0.0)
        assertEquals(original.userTextAlign, restored.userTextAlign)
        assertEquals(original.forceAlignOverride, restored.forceAlignOverride)
    }

    @Test
    fun `toReaderPreferences preserves non-theme fields from current`() {
        // current 提供 brightness/colorHistory 等非主题字段，快照不应覆盖它们
        val current = baseline().copy(brightness = 0.7f, colorHistory = listOf())
        val snapshot = baseline().copy(fontSize = 2.0).toPerBookSnapshot(1L, "default")
        val result = snapshot.toReaderPreferences(current)
        // 非主题字段应来自 current
        assertEquals(0.7f, result.brightness, 0.0f)
        assertEquals(current.colorHistory, result.colorHistory)
    }

    @Test
    fun `toReaderPreferences sets readerThemeId from snapshot themeId`() {
        val snapshot = baseline().toPerBookSnapshot(1L, "night")
        val result = snapshot.toReaderPreferences(baseline().copy(readerThemeId = "default"))
        assertEquals("night", result.readerThemeId)
    }

    @Test
    fun `forceAlignOverride boolean true maps to int 1 and back`() {
        val prefs = baseline().copy(forceAlignOverride = true)
        val snapshot = prefs.toPerBookSnapshot(1L, "default")
        assertEquals(1, snapshot.forceAlignOverride)
        val restored = snapshot.toReaderPreferences(baseline())
        assertTrue(restored.forceAlignOverride)
    }

    @Test
    fun `forceAlignOverride boolean false maps to int 0 and back`() {
        val prefs = baseline().copy(forceAlignOverride = false)
        val snapshot = prefs.toPerBookSnapshot(1L, "default")
        assertEquals(0, snapshot.forceAlignOverride)
        val restored = snapshot.toReaderPreferences(baseline())
        assertFalse(restored.forceAlignOverride)
    }

    @Test
    fun `snapshot bookId and themeId are set from parameters`() {
        val snapshot = baseline().toPerBookSnapshot(bookId = 42L, themeId = "sepia")
        assertEquals(42L, snapshot.bookId)
        assertEquals("sepia", snapshot.themeId)
    }

    /**
     * ★ 回归测试：模拟修复后的 saveSnapshot 写入循环（防 v12 Bug 回归）。
     *
     * Bug 根因：原 ViewModel 用 `global` 作 saveSnapshot baseline，导致连续两次改不同字段时，
     * 第二次写入会用 global 值冲刷掉第一次的设置（例如改字号后改字色，字号被重置）。
     *
     * 修复后 baseline = snapshot.toReaderPreferences(global)（读已存快照改单字段再存）。
     * 此测试在 Mapper 层验证该循环的字段保留性：snapshot → prefs → copy(单字段) → 新 snapshot 应保留其余 16 字段。
     */
    @Test
    fun `successive saveSnapshot preserves previously edited fields`() {
        // 初始：per-book 开启时冻结 global（含 X=2.0,Y=default）
        val global = baseline().copy(fontSize = 1.0, textColor = 0xFF111111.toInt())
        val snapshot1 = global.toPerBookSnapshot(1L, "default")

        // 第 1 次改字号 → 基准应是 snapshot1 而非 global
        // 模拟 currentEffectivePrefs(): snapshot.toReaderPreferences(global)
        val effective1 = snapshot1.toReaderPreferences(global)
        val edited1 = effective1.copy(fontSize = 2.0)
        val snapshot2 = edited1.toPerBookSnapshot(1L, "default")

        // 第 2 次改字色 → 基准应是 snapshot2（保留 fontSize=2.0）
        val effective2 = snapshot2.toReaderPreferences(global)
        val edited2 = effective2.copy(textColor = 0xFF000000.toInt())
        val snapshot3 = edited2.toPerBookSnapshot(1L, "default")

        // 验证：两次改动都应保留
        val final = snapshot3.toReaderPreferences(global)
        assertEquals(2.0, final.fontSize, 0.0)                  // 第 1 次改的字号仍在
        assertEquals(0xFF000000.toInt(), final.textColor)       // 第 2 次改的色也在
        // global 的值不应"漏"进来（这正是 Bug 时的症状）
        assertEquals(2.0, final.fontSize, 0.0)
        assertTrue("字色不应回退到 global 的 0xFF111111",
            final.textColor != 0xFF111111.toInt())
    }
}

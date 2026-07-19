package com.wxn.reader.data.dto

import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.model.preference.ReaderThemePreset
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v11 对齐字段纳入 reader_theme_configs 归档后的 mapper + differsFrom 单元测试（见设计方案 §二.0）。
 *
 * 验证项：
 * - mapper (a) toReaderThemeConfigEntity 写入对齐两列
 * - mapper (b) toReaderPreferences 读取对齐两列（不透传 current）
 * - mapper (c) ReaderThemePreset.toReaderThemeConfigEntity 填 defaultPreferences 对齐值
 * - differsFrom 跳过对齐两列（不参与 `*` 标记判定）
 * - Round-trip：saveCurrent → loadTarget 对齐值不被篡改
 */
@RunWith(RobolectricTestRunner::class)
class ReaderThemeConfigAlignTest {

    private fun basePrefs(): ReaderPreferences = ReaderPreferencesUtil.defaultPreferences.copy(
        readerThemeId = "default"
    )

    private val preset = ReaderThemePreset(
        themeId = "default",
        backgroundColor = 0xFFFFFFFF.toInt(),
        textColor = 0xFF000000.toInt(),
        font = "sans_serif",
        fontSize = 1.0,
        lineHeight = 1.5,
        paragraphSpacing = 1.0,
        pageHorizontalMargins = 1.0,
        pageVerticalMargins = 1.0,
    )

    // ---- mapper (a): toReaderThemeConfigEntity 写入对齐两列 ----

    @Test
    fun `mapper A writes userTextAlign from prefs`() {
        val prefs = basePrefs().copy(userTextAlign = 2)  // Right
        val entity = prefs.toReaderThemeConfigEntity("default")
        assertEquals(2, entity.userTextAlign)
    }

    @Test
    fun `mapper A writes forceAlignOverride as int 1 when true`() {
        val prefs = basePrefs().copy(forceAlignOverride = true)
        val entity = prefs.toReaderThemeConfigEntity("default")
        assertEquals(1, entity.forceAlignOverride)
    }

    @Test
    fun `mapper A writes forceAlignOverride as int 0 when false`() {
        val prefs = basePrefs().copy(forceAlignOverride = false)
        val entity = prefs.toReaderThemeConfigEntity("default")
        assertEquals(0, entity.forceAlignOverride)
    }

    // ---- mapper (b): toReaderPreferences 读取对齐两列（不透传 current）----

    @Test
    fun `mapper B reads userTextAlign from entity not current`() {
        // entity 的 userTextAlign=1，current 的 userTextAlign=4 → 结果应为 1（从 entity 读）
        val entity = basePrefs().toReaderThemeConfigEntity("default").copy(userTextAlign = 1)
        val current = basePrefs().copy(userTextAlign = 4)
        val result = entity.toReaderPreferences(current)
        assertEquals(1, result.userTextAlign)
    }

    @Test
    fun `mapper B reads forceAlignOverride from entity not current`() {
        // entity 的 forceAlignOverride=1（true），current 的 forceAlignOverride=false → 结果应为 true
        val entity = basePrefs().toReaderThemeConfigEntity("default").copy(forceAlignOverride = 1)
        val current = basePrefs().copy(forceAlignOverride = false)
        val result = entity.toReaderPreferences(current)
        assertTrue(result.forceAlignOverride)
    }

    @Test
    fun `mapper B forceAlignOverride int 0 reads as false`() {
        val entity = basePrefs().toReaderThemeConfigEntity("default").copy(forceAlignOverride = 0)
        val current = basePrefs().copy(forceAlignOverride = true)
        val result = entity.toReaderPreferences(current)
        assertFalse(result.forceAlignOverride)
    }

    // ---- mapper (c): ReaderThemePreset.toReaderThemeConfigEntity 填 defaultPreferences 对齐值 ----

    @Test
    fun `mapper C fills default userTextAlign from defaultPreferences`() {
        // defaultPreferences.userTextAlign = 4 (Justify)
        val entity = preset.toReaderThemeConfigEntity()
        assertEquals(ReaderPreferencesUtil.defaultPreferences.userTextAlign, entity.userTextAlign)
        assertEquals(4, entity.userTextAlign)
    }

    @Test
    fun `mapper C fills default forceAlignOverride as 0`() {
        val entity = preset.toReaderThemeConfigEntity()
        assertEquals(0, entity.forceAlignOverride)
    }

    // ---- differsFrom 跳过对齐两列 ----

    @Test
    fun `differsFrom returns false when only align columns differ from preset`() {
        // 构造一个 entity：视觉字段全等于 preset，但 userTextAlign/forceAlignOverride 与 preset-archive 不同
        // differsFrom 应返回 false（对齐列不参与比较）
        val entityFromPreset = preset.toReaderThemeConfigEntity()
        // 改 entity 的对齐列（模拟用户改了对齐）
        val entityWithAlignChanged = entityFromPreset.copy(userTextAlign = 1, forceAlignOverride = 1)
        assertFalse("对齐列变化不应判为已微调", entityWithAlignChanged.differsFrom(preset))
    }

    @Test
    fun `differsFrom returns true when visual field differs from preset`() {
        val entityFromPreset = preset.toReaderThemeConfigEntity().copy(fontSize = 2.0)
        assertTrue(entityFromPreset.differsFrom(preset))
    }

    @Test
    fun `differsFrom returns false when entity equals preset on all visual fields`() {
        val entityFromPreset = preset.toReaderThemeConfigEntity()
        assertFalse(entityFromPreset.differsFrom(preset))
    }

    // ---- Round-trip：saveCurrent → loadTarget 对齐值不被篡改 ----

    @Test
    fun `round-trip saveCurrent then loadTarget preserves userTextAlign`() {
        val original = basePrefs().copy(userTextAlign = 3, forceAlignOverride = true, fontSize = 1.2)
        // saveCurrent：prefs → entity
        val entity = original.toReaderThemeConfigEntity("default")
        // loadTarget：entity → prefs（用另一个 current 提供 colorHistory 等非主题字段）
        val current = basePrefs().copy(userTextAlign = 4, forceAlignOverride = false)
        val restored = entity.toReaderPreferences(current)
        assertEquals(3, restored.userTextAlign)
        assertTrue(restored.forceAlignOverride)
        assertEquals(1.2, restored.fontSize, 0.0)
    }

    @Test
    fun `migration DEFAULT 4 value round-trips through loadTarget`() {
        // 模拟存量 archive 行：迁移后 userTextAlign=DEFAULT 4（Justify）
        // loadTarget 读到的应为 4，不被构造兜底默认 1 篡改
        val entity = preset.toReaderThemeConfigEntity()  // mapper C 填 defaultPreferences.userTextAlign=4
        val current = basePrefs().copy(userTextAlign = 1)  // current 有不同值
        val restored = entity.toReaderPreferences(current)
        assertEquals(4, restored.userTextAlign)  // 从 entity 读 4，不从 current 透传 1
    }
}

package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v6 尺寸契约单元测试（非连续翻页首帧裁剪修复，Step 0）。
 *
 * 验证范围：
 * 1. [ChapterProvider.synchronouslyUpdateLayout] 派生尺寸正确性（visibleBottom/visibleRight 等式）
 *    —— [ChapterProvider.recomputeDerivedSizes] 是 private，经此 public 入口间接覆盖。
 * 2. [ChapterProvider.synchronouslyUpdateLayout] 去重行为（w==viewWidth && h==viewHeight 时 return）。
 * 3. [ChapterProvider.setViewSize] 返回值语义（尺寸变化返回 true，不变返回 false）—— Step 1 回归锚点。
 *
 * 运行：`./gradlew :bookread:testDebugUnitTest --tests "*ChapterProviderViewSetSizeContractTest"`
 *
 * 注意：[ChapterProvider] 是 `object` 单例，[setUp] 需重置字段（参照既有 DualColumnLayoutTest）。
 * [setViewSize] 需要 context（内部 Coroutines.mainScope().launch { upVisibleSize(context) } 访问
 * context.resources），故用 Robolectric 而非纯 JVM。context 取自 [RuntimeEnvironment.getApplication]
 * （robolectric 自带，无需额外 androidx.test:core 依赖）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Robolectric 4.14.1 最高支持 SDK 34；compileSdk=36 需显式锁定避免 DefaultSdkPicker 失败
class ChapterProviderViewSetSizeContractTest {

    @Before
    fun setUp() {
        // 重置单例字段，避免测试间状态泄漏
        ChapterProvider.viewWidth = 0
        ChapterProvider.viewHeight = 0
        ChapterProvider.paddingHorizontal = 0
        ChapterProvider.paddingVertical = 0
        ChapterProvider.visibleWidth = 0
        ChapterProvider.visibleHeight = 0
        ChapterProvider.visibleRight = 0
        ChapterProvider.visibleBottom = 0
    }

    // ---- 1. synchronouslyUpdateLayout 派生尺寸正确性（首次布局 oldw<=0，不缩放 padding）----

    @Test
    fun `synchronouslyUpdateLayout first layout computes derived sizes correctly`() {
        // 首次布局：oldw=0，不缩放 padding，沿用既有值（此处为 0）
        ChapterProvider.synchronouslyUpdateLayout(w = 1080, h = 1920, oldw = 0, oldh = 0)

        assertEquals(1080, ChapterProvider.viewWidth)
        assertEquals(1920, ChapterProvider.viewHeight)
        // paddingHorizontal=0, paddingVertical=0（isVScrollMode=false 默认，但 oldw<=0 不进缩放分支）
        assertEquals(1080, ChapterProvider.visibleWidth)
        assertEquals(1920, ChapterProvider.visibleHeight)
        assertEquals(1080, ChapterProvider.visibleRight)   // paddingHorizontal(0) + visibleWidth(1080)
        assertEquals(1920, ChapterProvider.visibleBottom)  // paddingVertical(0) + visibleHeight(1920)
    }

    @Test
    fun `synchronouslyUpdateLayout preserves existing padding on first layout`() {
        // 预设非零 padding（模拟 upVisibleSize 已计算）
        ChapterProvider.paddingHorizontal = 80
        ChapterProvider.paddingVertical = 60

        ChapterProvider.synchronouslyUpdateLayout(w = 1080, h = 1920, oldw = 0, oldh = 0)

        // 首次布局不缩放 padding
        assertEquals(80, ChapterProvider.paddingHorizontal)
        assertEquals(60, ChapterProvider.paddingVertical)
        // 派生尺寸等式：visibleBottom == viewHeight - paddingVertical*2
        assertEquals(1920 - 60 * 2, ChapterProvider.visibleHeight)
        assertEquals(60 + (1920 - 60 * 2), ChapterProvider.visibleBottom)
        assertEquals(60, ChapterProvider.paddingVertical)
        // 核心契约：visibleBottom == paddingVertical + visibleHeight
        assertEquals(
            ChapterProvider.paddingVertical + ChapterProvider.visibleHeight,
            ChapterProvider.visibleBottom
        )
    }

    // ---- 2. synchronouslyUpdateLayout 去重行为 ----

    @Test
    fun `synchronouslyUpdateLayout no-op when size unchanged`() {
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920
        ChapterProvider.visibleWidth = 999 // 故意设一个非派生值，验证不被覆盖

        // w==viewWidth && h==viewHeight → 直接 return，不改任何字段
        ChapterProvider.synchronouslyUpdateLayout(w = 1080, h = 1920, oldw = 720, oldh = 1280)

        assertEquals(999, ChapterProvider.visibleWidth) // 未被 recomputeDerivedSizes 覆盖
    }

    @Test
    fun `synchronouslyUpdateLayout ignores invalid dimensions`() {
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920
        ChapterProvider.visibleWidth = 999

        // w<=0 → return
        ChapterProvider.synchronouslyUpdateLayout(w = 0, h = 1920, oldw = 0, oldh = 0)
        assertEquals(999, ChapterProvider.visibleWidth)

        // h<=0 → return
        ChapterProvider.synchronouslyUpdateLayout(w = 1080, h = 0, oldw = 0, oldh = 0)
        assertEquals(999, ChapterProvider.visibleWidth)
    }

    // ---- 3. synchronouslyUpdateLayout 旋转缩放（oldw>0）----

    @Test
    fun `synchronouslyUpdateLayout scales padding proportionally on rotation`() {
        // 初始竖屏 1080x1920，padding 80x60
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920
        ChapterProvider.paddingHorizontal = 80
        ChapterProvider.paddingVertical = 60

        // 旋转到横屏 1920x1080
        ChapterProvider.synchronouslyUpdateLayout(w = 1920, h = 1080, oldw = 1080, oldh = 1920)

        assertEquals(1920, ChapterProvider.viewWidth)
        assertEquals(1080, ChapterProvider.viewHeight)
        // paddingHorizontal = 80 * 1920 / 1080 = 142.2 → 142
        assertEquals((80f * 1920 / 1080).toInt(), ChapterProvider.paddingHorizontal)
        // paddingVertical = 60 * 1080 / 1920 = 33.75 → 33（isVScrollMode=false）
        assertEquals((60f * 1080 / 1920).toInt(), ChapterProvider.paddingVertical)
        // 派生尺寸等式仍成立
        assertEquals(
            ChapterProvider.paddingVertical + ChapterProvider.visibleHeight,
            ChapterProvider.visibleBottom
        )
        assertEquals(
            ChapterProvider.paddingHorizontal + ChapterProvider.visibleWidth,
            ChapterProvider.visibleRight
        )
    }

    // ---- 4. setViewSize 返回值语义（Step 1 回归锚点）----

    @Test
    fun `setViewSize returns true when size changes`() {
        val context = RuntimeEnvironment.getApplication()
        ChapterProvider.viewWidth = 0
        ChapterProvider.viewHeight = 0

        val changed = ChapterProvider.setViewSize(context, width = 1080, height = 1920)

        assertTrue("尺寸从 0 变为 1080x1920 应返回 true", changed)
        // 同步部分（recomputeDerivedSizes）已执行
        assertEquals(1080, ChapterProvider.viewWidth)
        assertEquals(1920, ChapterProvider.viewHeight)
    }

    @Test
    fun `setViewSize returns false when size unchanged`() {
        val context = RuntimeEnvironment.getApplication()
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920

        val changed = ChapterProvider.setViewSize(context, width = 1080, height = 1920)

        assertFalse("尺寸未变应返回 false", changed)
    }

    @Test
    fun `setViewSize returns false for invalid dimensions`() {
        val context = RuntimeEnvironment.getApplication()
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920

        // width<=0：无效参数，不更新状态，返回 false（避免无意义重排）
        var changed = ChapterProvider.setViewSize(context, width = 0, height = 1920)
        assertFalse("width=0 无效应返回 false", changed)
        assertEquals(1080, ChapterProvider.viewWidth) // 未被更新

        // height<=0：同理
        changed = ChapterProvider.setViewSize(context, width = 1080, height = 0)
        assertFalse("height=0 无效应返回 false", changed)
        assertEquals(1920, ChapterProvider.viewHeight) // 未被更新
    }
}

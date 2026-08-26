package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.TextDirection
import com.wxn.bookread.jni.SheenBidiNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (设备/模拟器): 段落基调契约测试 —— T1「JNI 基级取回」的直接断言。
 *
 * 方案: docs/plans/2026-08-23-plan-rtl-listdot-base-direction.md §3.1 (R2-2)
 *
 * 为什么是 androidTest 而非 JVM 单测（decisions.md TC-E-001 教训）:
 *   基调逻辑依赖 JNI + SheenBidi .so，纯 JVM 下 available=false 恒走 LTR 降级，
 *   测不出接线 bug。本测试走 SheenBidiNative.bidiRunsNative 真实链路。
 *
 * 双层断言：
 *   - SheenBidiNative.bidiRuns → BidiParagraph（JNI ABI: [baseLevel, run×3...]）
 *   - RTLSegmenter.segment → SegmentResult（fast path / 混合段的 Kotlin 契约）
 *
 * 运行(需连接设备):
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RTLSegmenterInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RTLSegmenterInstrumentedTest {

    @Before
    fun setUp() {
        // .so 随 APK 分发，设备上必须可用；不可用 = 打包/ABI 问题，直接失败
        assertTrue(
            "SheenBidiNative.available=false —— .so 未打进测试 APK（ABI/打包问题），本测试失去意义",
            SheenBidiNative.available
        )
        println("SheenBidi version = ${SheenBidiNative.version}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B1: "123 نص" —— G3 修复目标的直接断言。
    //     ★ 实测（2026-08-25, Mi 10）：SheenBidi runs 为【视觉序，左→右】，
    //       因此 runs[0] 是视觉最左 run（本例为阿语），数字 run 在 runs[1]。
    //     修复前旧逻辑 runs[0].isRtl 反推在此例恰好得 true（视觉最左=阿语）；
    //     真正翻车的是 B2（逻辑末尾是数字 → 视觉最左=LTR run → 旧逻辑误判 LTR）。
    //     修复后: baseLevel 来自 SBParagraphGetBaseLevel（P2-P3 首强 = 阿语 → 奇数级）。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun B1_digitLeading_arabicBase() {
        val text = "123 نص"

        val bidi = SheenBidiNative.bidiRuns(text, baseRtl = false)
        println("B1: bidi = $bidi")
        assertTrue("B1 失败: baseLevel=${bidi.baseLevel} 应为奇数（阿语首强 → RTL 基调）",
            (bidi.baseLevel and 1) == 1)
        assertEquals("B1 失败: 应为 2 个 run（EN + 阿语）", 2, bidi.runs.size)
        // 视觉序（左→右）：runs[0] = " نص"（level 1，RTL），runs[1] = "123"（EN level 2，视觉 LTR）
        assertTrue("B1 失败: runs[0] 应为阿语 RTL run（视觉最左），实际 ${bidi.runs[0]}",
            bidi.runs[0].isRtl && bidi.runs[0].level == 1 && bidi.runs[0].offset == 3)
        assertTrue("B1 失败: runs[1] 应为 EN 的 level 2 视觉 LTR run，实际 ${bidi.runs[1]}",
            bidi.runs[1].isLtr && bidi.runs[1].level == 2 && bidi.runs[1].offset == 0)

        val seg = RTLSegmenter.segment(text)
        println("B1: segment → direction=${seg.direction} baseRtl=${seg.baseRtl} runs=${seg.runs.size}")
        assertTrue("B1 失败: segment().baseRtl 应为 true（基调来自基级而非 run 反推）",
            seg.baseRtl)
        assertEquals("B1 失败: direction 应为 RTL（基调）", TextDirection.RTL, seg.direction)
        assertEquals("B1 失败: 混合段 runs 应保留", 2, seg.runs.size)
        println("B1 ★ 通过: 数字开头+阿语结尾段基调 = RTL（基级权威）")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B2: "نص 123" —— ★ G3 bug 的真实显现场景（实测修正）。
    //     视觉序（左→右）：runs[0] = "123"（EN level 2 视觉 LTR）——
    //     旧逻辑 runs[0].isRtl 反推得 false → RTL 段被误判 LTR 基调（对齐/行方向全错）。
    //     即：翻车集合 = 逻辑末尾为 LTR 块（数字/URL/英文）的 RTL 段。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun B2_arabicLeading_control() {
        val text = "نص 123"

        val bidi = SheenBidiNative.bidiRuns(text, baseRtl = false)
        println("B2: bidi = $bidi")
        assertTrue("B2 失败: baseLevel=${bidi.baseLevel} 应为奇数", (bidi.baseLevel and 1) == 1)
        assertEquals("B2 失败: 应为 2 个 run", 2, bidi.runs.size)
        assertTrue("B2 失败: runs[0] 应为 EN 视觉 LTR run（视觉最左=逻辑末尾的数字），实际 ${bidi.runs[0]}",
            bidi.runs[0].isLtr && bidi.runs[0].level == 2 && bidi.runs[0].offset == 3)
        assertTrue("B2 失败: runs[1] 应为阿语 RTL run，实际 ${bidi.runs[1]}",
            bidi.runs[1].isRtl && bidi.runs[1].level == 1 && bidi.runs[1].offset == 0)

        val seg = RTLSegmenter.segment(text)
        assertTrue("B2 失败: baseRtl 应为 true（旧逻辑此处为 false —— G3 bug 现场已修复）",
            seg.baseRtl)
        assertEquals("B2 失败: direction 应为 RTL", TextDirection.RTL, seg.direction)
        assertEquals("B2 失败: 混合段 runs 应保留", 2, seg.runs.size)
        println("B2 ★ 通过: 阿语开头+数字结尾段基调 = RTL（旧 runs[0] 反推 bug 的真实现场，已修复）")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B3: "١٢٣ نص" —— 阿拉伯-印度数字（AN，弱型）不参与 P2-P3 首强判定，基调仍 RTL。
    //     ★ 实测修正（2026-08-25）：UAX#9 I1 规则下 AN 在奇数基级得 level+1=2
    //       （偶数 = 视觉 LTR run），与 EN 同级——因此这是混合段（runs 保留），
    //       不是纯 RTL fast path。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun B3_arabicIndicDigits_weakType() {
        val text = "١٢٣ نص"

        val bidi = SheenBidiNative.bidiRuns(text, baseRtl = false)
        println("B3: bidi = $bidi")
        assertTrue("B3 失败: AN 非强字符，首强仍为阿语 → baseLevel=${bidi.baseLevel} 应为奇数",
            (bidi.baseLevel and 1) == 1)
        assertTrue("B3 失败: AN run 应为 level 2（I1: 奇数基级 +1 → 偶数视觉 LTR），实际 ${bidi.runs.firstOrNull { it.offset == 0 }}",
            bidi.runs.any { it.offset == 0 && it.isLtr && it.level == 2 })

        val seg = RTLSegmenter.segment(text)
        assertTrue("B3 失败: baseRtl 应为 true", seg.baseRtl)
        assertEquals("B3 失败: direction 应为 RTL", TextDirection.RTL, seg.direction)
        assertEquals("B3 失败: AN 得偶数级（视觉 LTR run）→ 混合段 runs 应保留，实际 ${seg.runs.size}",
            2, seg.runs.size)
        println("B3 ★ 通过: 阿拉伯-印度数字（AN 弱型）不改基调；AN run level 2 混合段形态正确")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B4: "123 456" —— 全弱字符（EN only）→ SBLevelDefaultLTR 兜底 baseLevel=0。
    //     维持现状语义：纯数字段是 LTR 段（在 RTL 书里按 LTR 渲染）。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun B4_neutralOnly_ltrFallback() {
        val text = "123 456"

        val bidi = SheenBidiNative.bidiRuns(text, baseRtl = false)
        println("B4: bidi = $bidi")
        assertEquals("B4 失败: 无强字符应兜底 LTR，baseLevel=${bidi.baseLevel} 应为 0",
            0, bidi.baseLevel)

        val seg = RTLSegmenter.segment(text)
        assertEquals("B4 失败: direction 应为 LTR", TextDirection.LTR, seg.direction)
        assertTrue("B4 失败: baseRtl 应为 false", !seg.baseRtl)
        assertTrue("B4 失败: 无 RTL run 应走纯 LTR fast path（runs 清空），实际 ${seg.runs.size}",
            seg.runs.isEmpty())
        println("B4 ★ 通过: 纯数字段维持 LTR 兜底语义")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B5: "hello نص" —— 首强为 L → 基调 LTR 的混合段（T4 谓词红线场景：
    //     这类列表行的圆点必须在左侧，renderGroup 启发式会误判到右侧）。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun B5_ltrBase_mixedParagraph() {
        val text = "hello نص"

        val bidi = SheenBidiNative.bidiRuns(text, baseRtl = false)
        println("B5: bidi = $bidi")
        assertEquals("B5 失败: 首强为 L → baseLevel=${bidi.baseLevel} 应为 0", 0, bidi.baseLevel)
        assertTrue("B5 失败: runs 应包含 RTL run（阿语），实际 ${bidi.runs}",
            bidi.runs.any { it.isRtl })

        val seg = RTLSegmenter.segment(text)
        assertEquals("B5 失败: direction 应为 LTR（基调，非 MIXED）", TextDirection.LTR, seg.direction)
        assertTrue("B5 失败: baseRtl 应为 false", !seg.baseRtl)
        assertEquals("B5 失败: 混合段 runs 应保留", 2, seg.runs.size)
        println("B5 ★ 通过: LTR 基调混合段契约正确（列表圆点左侧场景的地基）")
    }
}

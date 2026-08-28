package com.wxn.mobi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wxn.mobi.inative.NativeLib
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented (设备): head 内嵌 <style> CSS 收集链路端到端测试（CDATA-1 回归钉）。
 *
 * 方案: docs/plans/2026-08-28-plan-inline-style-cdata-css-fix.md §6.2
 *
 * 走完整生产链: NativeLib.getChapters/getChapter(type=4, HTML) → html_util::getChapter
 * → tidy(TidyXmlOut 给 style 内容包 <![CDATA[...]]>) → removeHtmlTagWrap
 * → parse_css(修复点: 入口剥离 CDATA) → handle_tags 合并 → JNI tagInfos → ParagraphData.tags。
 * 正式补上 MIX-1 轮 C1-C5 全部手工构造 TextTag、绕过 C++ 收集段的测试盲区（方案 G4）。
 *
 * 与 DirInheritChainInstrumentedTest 的分工：C 系列测「annotations 解析 → segment 融合」，
 * 本测试测「真实文件 → C++ 收集/合并 → TextTag.params」的 C++ 段。
 *
 * 运行(需连接设备，MIUI 走 am instrument 而非 gradle UTP):
 *   gradlew :mobi:assembleDebugAndroidTest
 *   adb install -r mobi\build\outputs\apk\androidTest\debug\mobi-debug-androidTest.apk
 *   adb shell am instrument -w -e class com.wxn.mobi.InlineStyleCssChainInstrumentedTest \
 *     com.wxn.mobi.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class InlineStyleCssChainInstrumentedTest {

    /** D0: appmobi.so 必须打进测试 APK，否则本测试失去意义（回退预案见方案 §6.2） */
    @Test
    fun D0_nativeLibAvailable() {
        assertTrue("D0 失败: appmobi.so 未打进 mobi androidTest APK（ABI/打包问题）——" +
            "按方案回退：将本测试类移至 app/src/androidTest（app 必含 .so）", NativeLib.tryLoad())
        println("D0 ★ 通过: NativeLib 可用")
    }

    // ── 断言工具：键值对级解析（方案 RV-3——value 可能带空白，禁止裸 contains）──

    private fun paramPairs(params: String): Map<String, String> =
        params.split('&')
            .filter { it.contains('=') }
            .associate {
                val idx = it.indexOf('=')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }

    private fun findParagraph(paragraphs: Array<com.wxn.mobi.data.model.ParagraphData>,
                              needle: String) =
        paragraphs.firstOrNull { String(it.line, Charsets.UTF_8).contains(needle) }

    private fun chapterOf(html: String): Array<com.wxn.mobi.data.model.ParagraphData> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("前置失败: NativeLib 不可用", NativeLib.tryLoad())
        val file = File(context.cacheDir, "cdata1_${System.nanoTime()}.html")
        file.writeText(html, Charsets.UTF_8)
        try {
            val bookId = System.nanoTime()
            val chapters = NativeLib.getChapters(context, bookId, file.absolutePath, 4)
            assertNotNull("前置失败: getChapters(type=4) 返回 null（文件解析失败）", chapters)
            assertTrue("前置失败: getChapters 返回空", chapters!!.isNotEmpty())
            val paragraphs = NativeLib.getChapter(context, file.absolutePath, chapters[0], 4)
            assertNotNull("前置失败: getChapter 返回 null", paragraphs)
            NativeLib.closeBook(bookId, file.absolutePath, 4)
            return paragraphs!!
        } finally {
            file.delete()
        }
    }

    /** D1: tidy 包裹路径（A5 场景 1 同构）——.dir-css 规则经 CDATA 剥离后合并进 div.params */
    @Test
    fun D1_tidyCdataWrap_cssDirectionReachesDivParams() {
        val paragraphs = chapterOf(
            "<html><head><title>t</title>" +
                "<style>.dir-css{direction:rtl}</style>" +
                "</head><body>" +
                "<div class=\"dir-css\"><p>Hello CSS rule declared RTL</p></div>" +
                "</body></html>"
        )
        val p = findParagraph(paragraphs, "Hello CSS rule declared RTL")
        assertNotNull("D1 失败: 未找到目标段落", p)

        val divTag = p!!.tags.firstOrNull { it.name == "div" }
        assertNotNull("D1 失败: 段落无 div 祖先标签", divTag)
        val kv = paramPairs(divTag!!.params)
        assertEquals("D1 失败: .dir-css 的 direction:rtl 未合并进 div.params（CDATA 剥离未生效）",
            "rtl", kv["direction"])
        println("D1 ★ 通过: head 内嵌 style → tidy CDATA → parse_css 剥离 → direction=rtl 进入 div.params")
    }

    /** D1b: 源生 CDATA 包裹（手写 XHTML 常见）——tidy 保留 CDATA，同样由入口剥离兜住 */
    @Test
    fun D1b_sourceCdataWrap_sameResult() {
        val paragraphs = chapterOf(
            "<html><head><title>t</title>" +
                "<style type=\"text/css\"><![CDATA[.dir-css{direction:rtl}]]></style>" +
                "</head><body>" +
                "<div class=\"dir-css\"><p>Hello source CDATA declared RTL</p></div>" +
                "</body></html>"
        )
        val p = findParagraph(paragraphs, "Hello source CDATA declared RTL")
        assertNotNull("D1b 失败: 未找到目标段落", p)
        val divTag = p!!.tags.firstOrNull { it.name == "div" }
        assertNotNull("D1b 失败: 段落无 div 祖先标签", divTag)
        assertEquals("D1b 失败: 源生 CDATA 包裹的规则未生效",
            "rtl", paramPairs(divTag!!.params)["direction"])
        println("D1b ★ 通过: 源生 CDATA 包裹路径同样恢复")
    }

    /** D2: 存量属性恢复回归——修复收益覆盖 direction 之外的全部 CSS 属性 */
    @Test
    fun D2_otherPropertiesAlsoRecovered() {
        val paragraphs = chapterOf(
            "<html><head><title>t</title>" +
                "<style>.sty{font-size:2em;color:#FF0000}</style>" +
                "</head><body>" +
                "<p class=\"sty\">Styled paragraph for regression</p>" +
                "</body></html>"
        )
        val p = findParagraph(paragraphs, "Styled paragraph for regression")
        assertNotNull("D2 失败: 未找到目标段落", p)
        val pTag = p!!.tags.firstOrNull { it.name == "p" }
        assertNotNull("D2 失败: 段落无 p 标签", pTag)
        val kv = paramPairs(pTag!!.params)
        assertEquals("D2 失败: font-size 未合并", "2em", kv["font-size"])
        assertEquals("D2 失败: color 未合并", "#ff0000", kv["color"]?.lowercase())
        println("D2 ★ 通过: font-size/color 等存量属性经同一修复恢复")
    }
}

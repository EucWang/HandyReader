package com.wxn.mobi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 嵌套有序列表注解链诊断（验收缺陷定位用，非回归钉）。
 *
 * 现象：epub-rtl-listdot/EPUB-B「قائمة مرتبة متداخلة」嵌套 ol，
 * 真机序号呈扁平 1,2,3,4（子列表未从 1 重计、父列表未续接），且子项无二级缩进。
 *
 * 本测试走完整生产链（html → C++ parse → JNI tagInfos → ParagraphData.tags），
 * 打印每段真实注解链，并按 ChapterProvider 消费端同构算法
 * （liTag = firstOrNull{name=="li"}、parent = uuid==liTag.parentUuid、
 *   listLevel = count{ul|ol}、nextOrder 模拟）复算序号与层级，
 * 定位「链在哪一环丢失嵌套信息」。
 *
 * 运行(需连接设备):
 *   gradlew :mobi:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.mobi.NestedListChainDiagInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class NestedListChainDiagInstrumentedTest {

    private fun chapterOf(html: String): Array<ParagraphData> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("前置失败: NativeLib 不可用", NativeLib.tryLoad())
        val file = File(context.cacheDir, "nested_list_diag_${System.nanoTime()}.html")
        file.writeText(html, Charsets.UTF_8)
        try {
            val bookId = System.nanoTime()
            val chapters = NativeLib.getChapters(context, bookId, file.absolutePath, 4)
            assertNotNull("前置失败: getChapters(type=4) 返回 null", chapters)
            assertTrue("前置失败: getChapters 返回空", chapters!!.isNotEmpty())
            val paragraphs = NativeLib.getChapter(context, file.absolutePath, chapters[0], 4)
            assertNotNull("前置失败: getChapter 返回 null", paragraphs)
            NativeLib.closeBook(bookId, file.absolutePath, 4)
            return paragraphs!!
        } finally {
            file.delete()
        }
    }

    /** 消费端 nextOrder 同构模拟（ListOrderCalculator.nextOrder：value/start 属性 + 按 parent uuid 计数） */
    private fun simulateOrder(paragraphs: Array<ParagraphData>): List<Triple<String, Int, Int>> {
        val counters = mutableMapOf<String, Int>()
        val result = mutableListOf<Triple<String, Int, Int>>()   // (text, order, level)
        for (p in paragraphs) {
            val text = String(p.line, Charsets.UTF_8)
            val liTag = p.tags.firstOrNull { it.name == "li" } ?: continue
            val parent = p.tags.firstOrNull { it.uuid == liTag.parentUuid }
            val level = p.tags.count { it.name == "ul" || it.name == "ol" }
            if (parent?.name != "ol") {
                result.add(Triple(text, 0, level))
                continue
            }
            val current = counters.getOrPut(parent.uuid) { 1 }
            counters[parent.uuid] = current + 1
            result.add(Triple(text, current, level))
        }
        return result
    }

    @Test
    fun D1_nestedOl_chainShapeAndOrder() {
        // 与 EPUB-B「قائمة مرتبة متداخلة」同构（拉丁文本便于定位），保留嵌套 <ol> 与换行空白
        val html = "<html><head><title>t</title></head><body>\n" +
            "<h2>Nested ordered</h2>\n" +
            "<ol>\n" +
            "<li>main one\n" +
            "  <ol>\n" +
            "  <li>sub alpha</li>\n" +
            "  <li>sub beta</li>\n" +
            "  </ol>\n" +
            "</li>\n" +
            "<li>main two</li>\n" +
            "</ol>\n" +
            "</body></html>"
        val paragraphs = chapterOf(html)

        println("NESTED-DIAG ===== 全部段落注解链 =====")
        paragraphs.forEachIndexed { i, p ->
            val text = String(p.line, Charsets.UTF_8)
            val chain = p.tags.joinToString(" -> ") {
                "${it.name}#${it.uuid.take(8)}(parent=${it.parentUuid.take(8)})"
            }
            println("NESTED-DIAG 段[$i] text='$text'")
            println("NESTED-DIAG        chain=$chain")
        }

        println("NESTED-DIAG ===== 消费端同构复算 (text, order, level) =====")
        simulateOrder(paragraphs).forEachIndexed { i, (text, order, level) ->
            println("NESTED-DIAG 复算[$i] order=$order level=$level text='$text'")
        }

        // 观测断言：子项段落的注解链形态
        val sub = paragraphs.firstOrNull { String(it.line, Charsets.UTF_8).contains("sub alpha") }
        assertNotNull("未找到 'sub alpha' 段落", sub)
        val liTag = sub!!.tags.firstOrNull { it.name == "li" }
        assertNotNull("sub 段无 li 标签", liTag)
        val parent = sub.tags.firstOrNull { it.uuid == liTag!!.parentUuid }
        val level = sub.tags.count { it.name == "ul" || it.name == "ol" }
        println("NESTED-DIAG sub: liTag=${liTag?.name}#${liTag?.uuid?.take(8)} " +
            "parent=${parent?.name}#${parent?.uuid?.take(8)} listLevel=$level")

        // 预期（正确语义）：liTag=自身 li、parent=内层 ol、listLevel=2
        // 任一不符即为链路缺陷证据（不在此修复，输出供方案使用）
        if (parent?.name != "ol") {
            println("NESTED-DIAG ★ 缺陷证据: sub 段 li 的直接父 = ${parent?.name}（预期 ol）")
        }
        if (liTag != sub.tags.lastOrNull { it.name == "li" }) {
            println("NESTED-DIAG ★ 缺陷证据: firstOrNull{name==li} 命中祖先 li 而非自身 li")
        }
        if (level != 2) {
            println("NESTED-DIAG ★ 缺陷证据: sub 段 listLevel=$level（预期 2）")
        }
    }
}

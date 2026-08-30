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
 * U5「SheenBidi 覆盖不全」告警来源诊断（验收排查项证据采集，非回归钉）。
 *
 * 现象：U5 第一章排版期出现唯一一条 `layoutMixedRun: SheenBidi 覆盖不全，截断处理`
 * （TextLayoutProvider:77-79 守卫：runs 不覆盖 [0, text.length)）。
 *
 * 本测试把 U5 第一章 xhtml 原文喂给真机完整解析链（tidy → C++ parse → JNI →
 * ParagraphData.line），逐段扫描 B 类字符（\n \r \u2028 \u2029）的**位置与上下文**，
 * 并对比 UTF-16 长度 vs 码点长度（offset 口径错位假设的排除/确认），
 * 判定告警段落属「段尾 B 类」（降噪即可）还是「段中 B 类」（内容截断风险，升级独立缺陷）。
 *
 * 运行(需连接设备):
 *   gradlew :mobi:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.mobi.U5BClassCharDiagInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class U5BClassCharDiagInstrumentedTest {

    /** 与 docs/tests/gen-ltr-unify-acceptance-epubs.py 生成物逐字一致的 U5 第一章 */
    private val u5Chapter1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml" lang="zh" xml:lang="zh" dir="ltr">
        <head>
          <title>U5 Mixed Chinese Arabic</title>
          <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
        </head>
        <body>

        <h1>第一章 中阿混排</h1>
        <p>这是第一段中文，正常从左到右排版，用于混排基线对照。</p>
        <p>هذه فقرة عربية طويلة تحتوي على كلمات كثيرة لاختبار اتجاه النص من اليمين إلى اليسار في القارئ الإلكتروني مع مزيج من الأرقام 123 والكلمات الإنجليزية مثل technology.</p>
        <p>这是第二段中文，出现在阿语段之后，观察其排版方向与对齐。</p>
        <p>وهذه فقرة عربية أخرى بجملة طويلة إضافية للتأكد من أن نسبة الفقرات العربية تتجاوز الثلث في هذا الفصل.</p>
        <p>中文第三段。双列模式下整章的切列方向应保持一致：本章阿语段占比超过三分之一，判定为 RTL 章节，从右列开始排，包含纯中文段在内。</p>
        <p>فقرة عربية أخيرة قصيرة لنهاية الفصل المختلط.</p>

        </body>
        </html>
    """.trimIndent()

    private fun chapterOf(html: String): Array<ParagraphData> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("前置失败: NativeLib 不可用", NativeLib.tryLoad())
        val file = File(context.cacheDir, "u5_bclass_diag_${System.nanoTime()}.xhtml")
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

    @Test
    fun D1_u5Chapter1_bClassCharCensus() {
        val paragraphs = chapterOf(u5Chapter1)
        val bClass = charArrayOf('\n', '\r', '\u2028', '\u2029')

        println("U5BC-DIAG ===== 段落清单与 B 类字符普查 =====")
        var hits = 0
        paragraphs.forEachIndexed { i, p ->
            val text = String(p.line, Charsets.UTF_8)
            val utf16 = text.length
            val codePoints = text.codePointCount(0, utf16)
            val positions = mutableListOf<Int>()
            text.forEachIndexed { idx, ch ->
                if (bClass.contains(ch)) positions.add(idx)
            }
            println("U5BC-DIAG 段[$i] utf16=$utf16 codePoint=$codePoints " +
                "bClassCount=${positions.size} text='${text.take(40)}…'")
            positions.forEach { idx ->
                hits++
                val from = (idx - 12).coerceAtLeast(0)
                val to = (idx + 12).coerceAtMost(utf16)
                val kind = if (idx == utf16 - 1) "段尾" else "段中"
                println("U5BC-DIAG   ★ B类[$kind] U+${text[idx].code.toString(16).uppercase()} " +
                    "idx=$idx/$utf16 上下文='${text.substring(from, to)}'")
            }
            if (utf16 != codePoints) {
                println("U5BC-DIAG   ★ 非BMP字符存在：utf16 与码点口径差 ${utf16 - codePoints}（offset 口径需核对）")
            }
        }
        println("U5BC-DIAG ===== 普查完成：B类字符命中 $hits 处 =====")
    }
}

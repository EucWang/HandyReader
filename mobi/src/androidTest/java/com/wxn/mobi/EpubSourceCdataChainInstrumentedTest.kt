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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Instrumented (设备): EPUB 链路「源生 CDATA + 多声明规则 + 块级 class」全量进 params
 * 的端到端回归钉（2026-08-28 M5 验收问题调查后固化，docs/investigations/ 见同名调查）。
 *
 * 背景结论（M5-1 调查）：源生 CDATA 包裹的三规则经 parse_css 入口剥离后，
 * font-size/color/text-align/direction **全部完整到达 TextTag.params**；
 * 段级 font-size/color 不渲染是渲染层 display=block 门控的既有设计
 * （RenderResources.kt:174 / ChapterProvider.kt:1171），与本收集链路无关。
 * 本测试锁定「params 到达」不被回归，同时验证首段/中后段（[N=1] vs [N=1341]）一致性。
 *
 * 书体与 docs/test-assets/cdata-split-acceptance.epub 同构（单章 >500KB + 24 个 h2
 * 切分点 + head style 源生 CDATA 包裹三规则），测试内自建 zip，无外部依赖。
 *
 * 运行(需连接设备，MIUI 走 am instrument 而非 gradle UTP):
 *   gradlew :mobi:assembleDebugAndroidTest
 *   adb install -r mobi\build\outputs\apk\androidTest\debug\mobi-debug-androidTest.apk
 *   adb shell am instrument -w -e class com.wxn.mobi.EpubSourceCdataChainInstrumentedTest \
 *     com.wxn.mobi.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class EpubSourceCdataChainInstrumentedTest {

    /** 键值对级解析（value 可能带空白，禁止裸 contains——同 D 系列 RV-3 约定） */
    private fun paramPairs(params: String): Map<String, String> =
        params.split('&')
            .filter { it.contains('=') }
            .associate {
                val idx = it.indexOf('=')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }

    private fun chapterXhtml(): String {
        val style = "<style type=\"text/css\">\n<![CDATA[\n" +
            ".big { font-size: 2em; color: #FF0000; }\n" +
            ".rtl { direction: rtl; }\n" +
            ".ctr { text-align: center; }\n" +
            "]]>\n</style>"
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n")
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
        sb.append("xmlns:epub=\"http://www.idpf.org/2007/ops\">\n")
        sb.append("<head><title>cdata-epub-chain</title>").append(style).append("</head>\n<body>\n")
        sb.append("<h1>CDATA epub chain</h1>\n")
        val filler = "CDATA inline style acceptance filler. This paragraph exists to " +
            "push the single chapter beyond the 500KB split threshold so that later " +
            "pages render via renderSplitSegment. 填充文本用于把单章推过 500KB 切分阈值。"
        for (s in 1..24) {
            sb.append("<h2>sec ").append(s).append("</h2>\n")
            for (i in 1..100) {
                val n = (s - 1) * 100 + i
                val text = "Segment paragraph $i/2400: $filler"
                when (n % 10) {
                    1 -> sb.append("<p class=\"big\">[big $n] $text</p>\n")
                    2 -> sb.append("<p class=\"ctr\">[ctr $n] $text</p>\n")
                    3 -> sb.append("<p class=\"rtl\">[rtl $n] $text</p>\n")
                    else -> sb.append("<p>[plain $n] $text</p>\n")
                }
            }
        }
        sb.append("</body>\n</html>\n")
        return sb.toString()
    }

    private fun writeEpub(file: File) {
        val chapter = chapterXhtml()
        val chapterBytes = chapter.toByteArray(Charsets.UTF_8)
        assertTrue(
            "章体 ${chapterBytes.size}B 未过 500KB 切分阈值",
            chapterBytes.size > 500 * 1024
        )
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            val mtBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mt = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mtBytes.size.toLong()
                crc = java.util.zip.CRC32().apply { update(mtBytes) }.value
            }
            z.putNextEntry(mt); z.write(mtBytes); z.closeEntry()
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put(
                "META-INF/container.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                    "  <rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
                    "media-type=\"application/oebps-package+xml\"/></rootfiles>\n</container>\n"
            )
            put(
                "OEBPS/content.opf",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\">\n" +
                    "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                    "    <dc:identifier id=\"uid\">urn:uuid:cdata-epub-chain</dc:identifier>\n" +
                    "    <dc:title>CDATA epub chain</dc:title><dc:language>zh</dc:language>\n" +
                    "    <meta property=\"dcterms:modified\">2026-08-28T00:00:00Z</meta>\n" +
                    "  </metadata>\n  <manifest>\n" +
                    "    <item id=\"c01\" href=\"c01.xhtml\" media-type=\"application/xhtml+xml\"/>\n" +
                    "  </manifest>\n  <spine>\n    <itemref idref=\"c01\"/>\n  </spine>\n</package>\n"
            )
            put("OEBPS/c01.xhtml", chapter)
        }
    }

    private fun pTagParams(
        paragraphs: Array<com.wxn.mobi.data.model.ParagraphData>,
        needle: String
    ): Map<String, String> {
        val p = paragraphs.firstOrNull { String(it.line, Charsets.UTF_8).contains(needle) }
        assertNotNull("未找到目标段落 '$needle'", p)
        val tag = p!!.tags.firstOrNull { it.name == "p" }
        assertNotNull("段落 '$needle' 无 p 标签", tag)
        return paramPairs(tag!!.params)
    }

    /** D3: 源生 CDATA 三规则（多声明/单声明 × 块级 class）全量到达 TextTag.params，首尾一致。
     *  兼容两种章列表形态：getChapters 可能返回单章（type=0，切分懒触发前）或含切分子章
     *  （type=1，切分已落盘——与 D 系列同进程连跑时观察到的形态），遍历全部章节聚合查找。 */
    @Test
    fun D3_epubSourceCdata_allRulesReachParams_firstAndLateConsistent() {
        assertTrue("NativeLib 不可用（appmobi.so 未打包）", NativeLib.tryLoad())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "cdata_epub_${System.nanoTime()}.epub")
        writeEpub(file)
        try {
            val bookId = System.nanoTime()
            val chapters = NativeLib.getChapters(context, bookId, file.absolutePath, 2)
            assertNotNull("getChapters(type=2) 返回 null", chapters)
            assertTrue("getChapters 返回空", chapters!!.isNotEmpty())
            val allParas = ArrayList<com.wxn.mobi.data.model.ParagraphData>()
            for ((i, ch) in chapters.withIndex()) {
                val ps = NativeLib.getChapter(context, file.absolutePath, ch, 2) ?: continue
                println(
                    "D3 ch[$i] type=${ch.type} splitSeq=${ch.splitSeq} " +
                        "idx=${ch.chapterIndex} paragraphs=${ps.size}"
                )
                allParas.addAll(ps)
            }
            NativeLib.closeBook(bookId, file.absolutePath, 2)
            assertTrue("聚合段落为空（getChapter 全部失败）", allParas.isNotEmpty())

            // 中后段三类标记段 + 基准段
            val bigLate = pTagParams(allParas.toTypedArray(), "[big 1341]")
            assertEquals("font-size 未进 params（源生 CDATA 剥离回归）",
                "2em", bigLate["font-size"])
            assertEquals("color 未进 params（多声明规则回归）",
                "#ff0000", bigLate["color"]?.lowercase())
            assertEquals("text-align 未进 params",
                "center", pTagParams(allParas.toTypedArray(), "[ctr 1342]")["text-align"])
            assertEquals("direction 未进 params",
                "rtl", pTagParams(allParas.toTypedArray(), "[rtl 1343]")["direction"])
            assertTrue("plain 段不应携带样式 params",
                pTagParams(allParas.toTypedArray(), "[plain 1344]").isEmpty())

            // 首尾一致（M5-5 params 级）
            assertEquals("首段 [big 1] 与中后段 [big 1341] params 不一致",
                bigLate, pTagParams(allParas.toTypedArray(), "[big 1]"))

            println("D3 ★ 通过: 源生 CDATA 三规则全量进 params，[big 1] ≡ [big 1341]（chapters=${chapters.size}）")
        } finally {
            file.delete()
        }
    }
}

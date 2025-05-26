package com.wxn.bookparser.parser.xml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.util.fastFilter
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.exts.clearMarkdown
import com.wxn.bookparser.exts.containsVisibleText
import com.wxn.bookparser.parser.base.DocumentParser
import com.wxn.bookparser.parser.base.MarkdownParser
import com.wxn.bookparser.util.FileUtil
import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject


private const val XML_TAG = "XML Parser"

/***
 * fb2 文件内容解析
 */
class XmlTextParser @Inject constructor(
    private val context: Context,
    private val documentParser: DocumentParser,
    private val markdownParser: MarkdownParser
) : TextParser {

    suspend fun parse(bookId: Long, cachedFile: CachedFile): List<ReaderText> {
        Log.i(XML_TAG, "Started XML parsing: ${cachedFile.name}.")

        return try {
            val readerText = cachedFile.openInputStream()?.use { stream ->
                val doc = Jsoup.parse(stream, null, "", Parser.xmlParser())
                documentParser.parseDocument(context, bookId, doc)
            }

            yield()

            if (
                readerText.isNullOrEmpty() ||
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                Log.e(XML_TAG, "Could not extract text from XML.")
                return emptyList()
            }

            Log.i(XML_TAG, "Successfully finished XML parsing.")
            readerText
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /***
     * 解析得到章节列表， 这里的章节 BookChapter 都没有bookId，需要后面组装
     */
    override suspend fun parseChapterInfo(bookId:Long, cachedFile: CachedFile): List<BookChapter> {
        return try {
            val chapters = arrayListOf<BookChapter>()

            cachedFile.openInputStream()?.use { stream ->
                val doc = Jsoup.parse(stream, null, "", Parser.xmlParser())
                val sections = doc.select("body > section")
                val chapterSize = sections.size

                sections.forEachIndexed { index, element ->
                    var title = element.select("p").fastFilter {
                        it.text().trim().isNotEmpty()
                    }.first().text().trim()
                    if (title.isEmpty()) {
                        title = context.getString(com.wxn.bookparser.R.string.chapter_default_name)
                    }
                    chapters.add(
                        BookChapter(
                            bookId = 0,
                            chapterId = "",
                            chapterIndex = index,
                            chapterName = title,
                            chaptersSize = chapterSize
                        )
                    )
                }
            }

            yield()

            chapters
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        return emptyList()
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter): List<ReaderText> {
        return try {
            val texts = arrayListOf<ReaderText>()
            cachedFile.openInputStream()?.use { stream ->
                val doc = Jsoup.parse(stream, null, "", Parser.xmlParser())
                val section = doc.select("body > section").get(chapter.chapterIndex)

                section.apply {
                    // Remove manual line breaks from all <p>, <a>
                    select("p").forEach { element ->
                        yield()
                        element.html(element.html().replace(Regex("\\n+"), " "))        //替换掉<p> 内部的手动换行
                        element.append("\n") //在<p> 内部的尾部加上一个换行符
                    }
                    select("a").forEach { element ->
                        yield()
                        element.html(element.html().replace(Regex("\\n+"), ""))         //替换掉<a> 内部的手动换行
                    }

                    // Remove <head>'s title
                    select("title").remove()                                            //移除掉<body>中的<title> 标签内容

                    // Markdown
                    select("hr").append("\n---\n")                                      //hr 标签 解析成 markdown 的 Section separator(---)
                    select("b").append("**").prepend("**")                              //b 标签 解析 成 markdown 的 Bold(**)
                    select("h1").append("**").prepend("**")                             //h1 标签 解析 成 markdown 的 Bold(**)
                    select("h2").append("**").prepend("**")                             //h2 标签 解析 成 markdown 的 Bold(**)
                    select("h3").append("**").prepend("**")                             //h3 标签 解析 成 markdown 的 Bold(**)
                    select("strong").append("**").prepend("**")                         //strong 标签 解析 成 markdown 的 Bold(**)
                    select("em").append("_").prepend("_")                               //em 标签 （斜体） 解析成 markdown 的 _
                    select("a").forEach { element ->                                    //a 标签 解析成 makrdown 的链接标签 [text](link)
                        var link = element.attr("href")
                        if (!link.startsWith("http") || element.wholeText().isBlank()) return@forEach

                        if (link.startsWith("http://")) {
                            link = link.replace("http://", "https://")
                        }

                        element.prepend("[")
                        element.append("]($link)")
                    }

                    // Image (<img>)
                    select("img").forEach { element ->
                        val src = element.attr("src")
                            .trim()
                            .substringAfterLast(File.separator)
                            .lowercase()
                            .takeIf {
                                it.containsVisibleText() && (it.startsWith("#") && null != doc.selectFirst("binary[id=${it.substring(1)}]"))
                            } ?: return@forEach

                        val alt = element.attr("alt").trim().takeIf {
                            it.clearMarkdown().containsVisibleText()
                        } ?: src.substringBeforeLast(".")
                        Logger.d("DocumentParser:innerParse:img:src=$src,alt=$alt")

                        element.append("\n[[$src|$alt]]\n")
                    }

                    // Image (<image>)
                    select("image").forEach { element ->
                        val eleImage = element.select("image")
                        val src = if (eleImage.hasAttr("xlink:href")) {
                            eleImage.attr("xlink:href")
                        } else if (eleImage.hasAttr("href")) {
                            eleImage.attr("href")
                        } else if (eleImage.hasAttr("l:href")) {
                            eleImage.attr("l:href")
                        } else {
                            ""
                        }
                            .trim()
                            .substringAfterLast(File.separator)
                            .lowercase()
                            .takeIf {
                                it.containsVisibleText() && (it.startsWith("#") && null != doc.selectFirst("binary[id=${it.substring(1)}]"))
                            } ?: return@forEach

                        val alt = src.substringBeforeLast(".")
                        Logger.d("DocumentParser:innerParse:image:src=$src,alt=$alt")

                        element.append("\n[[$src|$alt]]\n")
                    }
                }.wholeText().lines().forEach { line ->
                    yield()

                    val formattedLine = line.replace(
                        Regex("""\*\*\*\s*(.*?)\s*\*\*\*"""), "_**$1**_"
                    ).replace(
                        Regex("""\*\*\s*(.*?)\s*\*\*"""), "**$1**"
                    ).replace(
                        Regex("""_\s*(.*?)\s*_"""), "_$1_"
                    ).trim()

                    val imageRegex = Regex("""\[\[(.*?)\|(.*?)]]""")

                    if (line.containsVisibleText()) {
                        when {
                            imageRegex.matches(line) -> {
                                val trimmedLine = line.removeSurrounding("[[", "]]")
                                val src = trimmedLine.substringBefore("|")
                                val alt = "_${trimmedLine.substringAfter("|")}_"

                                Logger.d("DocumentParser::image[$src,$alt]")

                                val srcName = src.substring(1)
                                val image: Bitmap = try {
                                    val binary = doc.selectFirst("binary[id=${srcName}]")
                                    //清理所有空白字符（包括换行、缩进、空格）
                                    binary?.text()?.trim()?.replace("\\s+", "")?.let { data ->
                                        val inputStream = ByteArrayInputStream(android.util.Base64.decode(data, android.util.Base64.DEFAULT))
                                        BitmapFactory.decodeStream(inputStream)
                                    }
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    null
                                } ?: return@forEach

                                val width = image.width.coerceAtLeast(0)
                                val height = image.height.coerceAtLeast(0)
                                val targetFile = PathUtil.getChapterResourcePath(context, bookId, "${srcName}.jpg")

                                if (!targetFile.exists() && targetFile.length() <= 0) {
                                    if (FileUtil.saveBitmapToFile(context, image, targetFile.absolutePath)) {
                                        texts.add(ReaderText.Image(path = targetFile.absolutePath, width = width, height = height))// Adding image
                                    }
                                } else {
                                    texts.add(ReaderText.Image(path = targetFile.absolutePath, width = width, height = height))// Adding image
                                }
                                image.recycle()
                                yield()
                            }

                            line == "---" || line == "***" -> texts.add(ReaderText.Separator)

                            else -> {
                                if (formattedLine.clearMarkdown().containsVisibleText()) {
                                    texts.add(ReaderText.Text(line = markdownParser.parse(formattedLine).toString()))
                                }
                            }
                        }
                    }
                }
            }
            texts
        } catch (ex: Exception) {
            emptyList<ReaderText>()
        }
    }
}
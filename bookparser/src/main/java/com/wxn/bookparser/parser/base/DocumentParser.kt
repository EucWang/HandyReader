package com.wxn.bookparser.parser.base

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wxn.base.util.Logger
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.PathUtil
import com.wxn.bookparser.exts.clearAllMarkdown
import com.wxn.bookparser.exts.clearMarkdown
import com.wxn.bookparser.exts.containsVisibleText
import com.wxn.bookparser.util.FileUtil
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

class DocumentParser @Inject constructor(
    private val markdownParser: MarkdownParser
) {
    /**
     * Parses document to get it's text.
     * Fixes issues such as manual line breaking in <p>.
     * Applies Markdown to the text: Bold(**), Italic(_), Section separator(---), and Links(a > href).
     *
     * @return Parsed text line by line with Markdown(all lines are not blank).
     */
    suspend fun parseDocument(
        context: Context,
        bookId: Long,
        document: Document,
        zipFile: ZipFile? = null,
        imageEntries: List<ZipEntry>? = null,
        includeChapter: Boolean = true
    ): List<ReaderText> {
        yield()

        val readerText = mutableListOf<ReaderText>()
        var chapterAdded = false

        document.selectFirst("body")
            .run { this ?: document.body() }
            .apply {
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
                            it.containsVisibleText() && (imageEntries?.any { image ->
                                it == image.name.substringAfterLast(File.separator).lowercase()
                            } == true || (it.startsWith("#") && null != document.selectFirst("binary[id=${it.substring(1)}]")))
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
                            it.containsVisibleText() && (imageEntries?.any { image ->
                                it == image.name.substringAfterLast(File.separator).lowercase()
                            } == true || (it.startsWith("#") && null != document.selectFirst("binary[id=${it.substring(1)}]")))
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
                            val image : Bitmap = if (imageEntries != null) {
                               try {
                                    val imageEntry = imageEntries.find { image ->
                                        src == image.name.substringAfterLast(File.separator).lowercase()
                                    } ?: return@forEach
                                    zipFile?.getImage(imageEntry)//?.asImageBitmap()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                } ?: return@forEach
                            } else {
                                try {
                                    val binary = document.selectFirst("binary[id=${srcName}]")
                                    //清理所有空白字符（包括换行、缩进、空格）
                                    binary?.text()?.trim()?.replace("\\s+", "")?.let { data ->
                                        val inputStream =
//                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                                            ByteArrayInputStream(Base64.getDecoder().decode(data))
//                                        } else {
                                            ByteArrayInputStream(android.util.Base64.decode(data, android.util.Base64.DEFAULT))
//                                        }
                                        BitmapFactory.decodeStream(inputStream)//?.asImageBitmap()
                                    }
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    null
                                } ?: return@forEach
                            }
                            val width = image.width.coerceAtLeast(0)
                            val height = image.height.coerceAtLeast(0)
                            val targetFile = PathUtil.getChapterResourcePath(context, bookId, "${srcName}.jpg")
                            if(FileUtil.saveBitmapToFile(context, image, targetFile.absolutePath)) {
                                readerText.add(ReaderText.Image(path = targetFile.absolutePath, width = width, height = height))// Adding image
                            }

//                            image.prepareToDraw()
//                            readerText.add( // Adding image
//                                ReaderText.Image(
//                                    imageBitmap = image
//                                )
//                            )
//                            readerText.add( // Adding alternative text (caption) for image
//                                ReaderText.Text(
//                                    markdownParser.parse(alt)
//                                )
//                            )
                        }

                        line == "---" || line == "***" -> readerText.add(ReaderText.Separator)

                        else -> {
                            if (
                                !chapterAdded &&
                                formattedLine.clearAllMarkdown().containsVisibleText() &&
                                includeChapter
                            ) {
                                readerText.add(
                                    0, ReaderText.Chapter(
                                        title = formattedLine.clearAllMarkdown(),
                                        nested = false
                                    )
                                )
                                chapterAdded = true
                            } else if (
                                formattedLine.clearMarkdown().containsVisibleText()
                            ) {
                                readerText.add(
                                    ReaderText.Text(
                                        line = markdownParser.parse(formattedLine).toString()
                                    )
                                )
                            }
                        }
                    }
                }
            }

        yield()

        if (
            readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
            (includeChapter && readerText.filterIsInstance<ReaderText.Chapter>().isEmpty())
        ) {
            return emptyList()
        }

        return readerText
    }

    /**
     * Getting bitmap from [ZipFile] with compression
     * that depends on the [imageEntry] size.
     */
    private fun ZipFile.getImage(imageEntry: ZipEntry): Bitmap? {
        fun getBitmapFromInputStream(compressionLevel: Int = 1): Bitmap? {
            return getInputStream(imageEntry).use { inputStream ->
                BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inSampleSize = compressionLevel
                    }
                )
            }
        }


        val uncompressedBitmap = getBitmapFromInputStream() ?: return null
        return uncompressedBitmap
    }
}
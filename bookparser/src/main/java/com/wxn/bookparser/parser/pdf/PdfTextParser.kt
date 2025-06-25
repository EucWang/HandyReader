package com.wxn.bookparser.parser.pdf

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.exts.clearAllMarkdown
import com.wxn.bookparser.parser.base.MarkdownParser
import kotlinx.coroutines.yield
import javax.inject.Inject


private const val PDF_TAG = "PDF Parser"

class PdfTextParser @Inject constructor(
    private val markdownParser: MarkdownParser,
    private val application: Application
) : TextParser {

    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        return emptyList()
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId:Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText> {

        return emptyList()
    }

    suspend fun parse(bookId:Long, cachedFile: CachedFile): List<ReaderText> {
        Log.i(PDF_TAG, "Started PDF parsing: ${cachedFile.name}.")

        return try {
            yield()

            PDFBoxResourceLoader.init(application)

            yield()

            val oldText: String

            val pdfStripper = PDFTextStripper()
            pdfStripper.paragraphStart = "</br>"

            PDDocument.load(cachedFile.openInputStream()).use {
                oldText = pdfStripper.getText(it)
                    .replace("\r", "")
            }

            yield()

            val readerText = mutableListOf<ReaderText>()
            val text = oldText.filterIndexed { index, c ->
                yield()

                if (c == ' ') {
                    oldText[index - 1] != ' '
                } else {
                    true
                }
            }

            yield()

            val unformattedLines = text.split("${pdfStripper.paragraphStart}|\\n".toRegex())
                .filter { it.isNotBlank() }

            yield()

            val lines = mutableListOf<String>()
            unformattedLines.forEachIndexed { index, string ->
                try {
                    yield()

                    val line = string.trim()

                    if (index == 0) {
                        lines.add(line)
                        return@forEachIndexed
                    }

                    if (line.all { it.isDigit() }) {
                        return@forEachIndexed
                    }

                    if (line.first().isLowerCase()) {
                        val currentLine = lines[lines.lastIndex]

                        if (currentLine.last() == '-') {
                            if (currentLine[currentLine.lastIndex - 1].isLowerCase()) {
                                lines[lines.lastIndex] = currentLine.dropLast(1) + line
                                return@forEachIndexed
                            }
                        }

                        lines[lines.lastIndex] += " $line"
                        return@forEachIndexed
                    }

                    if (line.first().isUpperCase() || line.first().isDigit()) {
                        lines.add(line)
                        return@forEachIndexed
                    }

                    if (line.first().isLetter()) {
                        lines[lines.lastIndex] += " $line"
                        return@forEachIndexed
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    return@forEachIndexed
                }
            }

            yield()

            var chapterAdded = false
            lines.forEach { line ->
                yield()

                if (line.isNotBlank()) {
                    when (line) {
                        "***", "---" -> readerText.add(
                            ReaderText.Separator
                        )

                        else -> {
                            if (!chapterAdded && line.clearAllMarkdown().isNotBlank()) {
                                readerText.add(
                                    0, ReaderText.Chapter(
                                        title = line.clearAllMarkdown(),
                                        nested = false
                                    )
                                )
                                chapterAdded = true
                            } else readerText.add(
                                ReaderText.Text(
                                    line = markdownParser.parse(line).toString()
                                )
                            )
                        }
                    }
                }
            }

            yield()

            if (
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                Log.e(PDF_TAG, "Could not extract text from PDF.")
                return emptyList()
            }

            Log.i(PDF_TAG, "Successfully finished PDF parsing.")
            readerText
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun parseCss(bookId: Long,cachedFile: CachedFile,  cssNames: List<String>, tagNames: List<String>, ids: List<String>): List<CssInfo> {
        return emptyList()
    }

    override suspend fun getWordCount(bookId:Long, cachedFile: CachedFile):  List<Triple<Int, Int, Int>> {
        return emptyList()
    }

    override suspend fun close(bookId:Long, cachedFile: CachedFile) {

    }
}
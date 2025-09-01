package com.wxn.bookparser.parser.txt

import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.exts.clearAllMarkdown
import com.wxn.bookparser.parser.base.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject

private const val TXT_TAG = "TXT Parser"

class TxtTextParser @Inject constructor(
    private val markdownParser: MarkdownParser
) : TextParser {

    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId:Long, cachedFile: CachedFile): List<BookChapter> {
        return arrayListOf<BookChapter>(
            BookChapter(
                bookId = 0,
                chapterIndex = 0,
                chapterName = "",
                chaptersSize = 1
            )
        )
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId:Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText> {
        if (chapter.chapterIndex == 0) {
            return parse(bookId, cachedFile)
        } else {
            return emptyList()
        }
    }

    suspend fun parse(bookId:Long, cachedFile: CachedFile): List<ReaderText> {
        Log.i(TXT_TAG, "Started TXT parsing: ${cachedFile.name}.")

        return try {
            val readerText = mutableListOf<ReaderText>()
            var chapterAdded = false

            withContext(Dispatchers.IO) {
                cachedFile.openInputStream()?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
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
                }
            }

            yield()

            if (
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                Log.e(TXT_TAG, "Could not extract text from TXT.")
                return emptyList()
            }

            Log.i(TXT_TAG, "Successfully finished TXT parsing.")
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
        var count = 0
        return try {
            cachedFile.openInputStream()?.bufferedReader()?.use { reader ->
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        count += line.length
                    }
                }
            }
            listOf<Triple<Int, Int, Int>>(
                Triple(1, count, 0), //第一章节的字数
                Triple(-1, count, 0)    //总字数
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            emptyList()
        }
    }

    override suspend fun close(bookId:Long, cachedFile: CachedFile) {

    }
}
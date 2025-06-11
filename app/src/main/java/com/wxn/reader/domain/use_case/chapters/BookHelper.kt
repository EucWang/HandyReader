package com.wxn.reader.domain.use_case.chapters

import android.content.Context
import androidx.core.net.toUri
import com.spreada.utils.chinese.ZHConverter
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.source.local.AppPreferencesUtil

object BookHelper {

    /***
     * 从文件中解析得到章节信息
     */
    suspend fun getChapters(context: Context, bookId: Long, bookUri: String?, textParser: TextParser): List<BookChapter> {
        bookUri?.let { uri ->
            val cachedFile = CachedFileCompat.fromUri(context, uri.toUri())
            val chapters = textParser.parseChapterInfo(bookId, cachedFile)
            chapters.forEach { chapter ->
                chapter.bookId = bookId
            }
            return chapters
        }
        return emptyList()
    }

    suspend fun loadChapterContent(context: Context, book: Book, chapter: BookChapter, textparser: TextParser): List<ReaderText> {
        val encodedUri =  book.filePath.toUri()
        val cachedFile = CachedFileCompat.fromUri(context, encodedUri)
        val bookId = book.id
        if (chapter.bookId != bookId){
            chapter.bookId = bookId
        }
        return textparser.parsedChapterData(bookId, cachedFile, chapter)
    }

    suspend fun disposeContent(
        appPreferencesUtil: AppPreferencesUtil,
        chapter: BookChapter,
        contents: List<ReaderText>
    ): List<ReaderText> {
        val chineseConverterType = appPreferencesUtil.chineseConverterType()
        //得到简繁体对应的章节名称
        chapter.chapterName = when (chineseConverterType) {
            1 -> ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(chapter.chapterName)
            2 -> ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(chapter.chapterName)
            else -> chapter.chapterName
        }
        var title1: String = chapter.chapterName
        var content1: List<ReaderText> = contents
        try {
            when (chineseConverterType) {
                1 -> {
                    ZHConverter.getInstance(ZHConverter.SIMPLIFIED)
                }

                2 -> {
                    ZHConverter.getInstance(ZHConverter.TRADITIONAL)
                }

                else -> {
                    null
                }
            }?.let { converter ->
                title1 = converter.convert(title1)
                content1.forEach { content ->
                    if (content is ReaderText.Text) {
                        content.line = converter.convert(content.line)
                    } else if (content is ReaderText.Chapter) {
                        content.title = converter.convert(content.title)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e)
        }
        content1.forEach { content ->
            if (content is ReaderText.Text) {
                val line = content.line.replace("^[\\n\\s\\r]+".toRegex(), "")
                    .replace("&nbsp;", " ") //不换行空格
                    .replace("&ensp;", " ")          //半角空格
                    .replace("&emsp;", " ")          //全角空格
                    .replace("&thinsp;", " ")        //窄空格
                    .replace("&zwnj;", "")          //零宽不连字，不打印字符，放在电子文本的两个字符之间，抑制本来会发生的连字
                    .replace("&zwj", "-")           //零宽连字，zero width joiner, 不打印字符，放在某些需要复杂排版语言（阿拉伯语，印地语）的两个字符之间，使得两个本不会发生连字的字符产生连字效果，零宽字符的Unicode码位是U+200D(HTML: &#8205; &zwj;)
                    .replace("&#x0020;", " ")        //空格
                    .replace("&#x0009;", " ")        //制表位
                    .replace("&#x000A;", "")        //换行
                    .replace("&#x000D;", "")        //回车
                    .replace("&#12288;", "")        //
                content.line = line

                if (!content.line.isEmpty() && content.isText) {
                    content.line = "${ChapterProvider.paragraphIndent}$line"
                    val offset = ChapterProvider.paragraphIndent.length
                    content.annotations.forEach { anno ->
                        anno.start += offset
                        anno.end += offset
                    }
                }
            }
        }
        return content1
    }
}
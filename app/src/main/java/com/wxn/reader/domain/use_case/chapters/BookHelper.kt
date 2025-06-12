package com.wxn.reader.domain.use_case.chapters

import android.content.Context
import androidx.core.net.toUri
import com.spreada.utils.chinese.ZHConverter
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CSS_ITEM
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.RuleData
import com.wxn.base.bean.TextTag
import com.wxn.base.bean.format
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.bookread.data.model.TextChapter
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

    suspend fun loadChpaterCsses(context: Context, book: Book, allTags: Map<Int,List<TextTag>>?, textParser: TextParser): Map<String, CssInfo> {
        val encodedUri =  book.filePath.toUri()
        val cachedFile = CachedFileCompat.fromUri(context, encodedUri)
        val bookId = book.id

        val cssClassNames = hashSetOf<String>()
        allTags?.forEach { index, tags ->
            tags.forEach { tag ->
                tag.paramsPairs().filter {
                    it.first == "class" && it.second.isNotEmpty()
                }.map {
                    it.second
                }.forEach { name ->
                    cssClassNames.add(name)
                }
            }
        }
        val cssInfos = textParser.parseCss(bookId, cachedFile, cssClassNames.toList())
        val retVal = hashMapOf<String, CssInfo>()
        cssInfos.forEach { cssInfo ->
            retVal[cssInfo.identifier] = cssInfo
        }
        return retVal
    }

    suspend fun disposeContent(
        appPreferencesUtil: AppPreferencesUtil,
        chapter: BookChapter,
        contents: List<ReaderText>,
        csssheets: Map<String, CssInfo>
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

                val indent = getTextIntent(content.annotations, csssheets)

                if (!content.line.isEmpty() && content.isText) {
                    val lineStrBuilder = StringBuilder()
                    if (indent > 0) {
                        repeat(indent) {
                            lineStrBuilder.append(ChapterProvider.oneParagraphIndent)
                        }
                    }
                    lineStrBuilder.append(line)

                    content.line = lineStrBuilder.toString()
                    content.annotations.forEach { anno ->
                        anno.start += indent
                        anno.end += indent
                    }
                }
            }
        }
        return content1
    }
}

fun getTextIntent(annotations: List<TextTag>, csssheets: Map<String, CssInfo>): Int {
    val cssClasses = arrayListOf<String>()
    annotations.forEach { tag ->
        cssClasses.addAll(tag.cssClasses())
    }
    var indentRuleData : RuleData? = null
    for(css in cssClasses) {
        val ruleData = csssheets[css]?.datas?.firstOrNull { ruleData ->
            ruleData.format() == CSS_ITEM.CSS_TEXT_INDENT
        }
        if (ruleData != null) {
            indentRuleData = ruleData
            break
        }
    }
    var indent = 2
    if (indentRuleData != null) {
        Logger.d("BookHelper::disposeContent:indent=${indentRuleData}")
        if (indentRuleData.value.endsWith("em")) {
            val indentStr = indentRuleData.value.substring(0, indentRuleData.value.length - "em".length)
            indentStr.toIntOrNull()?.let { indentInt ->
                indent = indentInt
                Logger.d("BookHelper::disposeContent:change indent to $indent")
            }
        }
    }
    return indent
}
package com.wxn.bookread.data.model

import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import kotlin.math.min

/****
 * 用于显示的章节内容，包含若干的TextPage
 */
data class TextChapter(
    val position: Int,              //章节索引
    val title: String,              //章节名称
    val chapterId: Long,            //章节id，对应数据库中的id
    val pages: List<TextPage>,      //章节中一共有多少页
    val pageLines: List<Int>,       //章节中每一页显示的行数
    val pageLengths: List<Int>,     //章节中每一页显示的字符数
    val chaptersSize: Int,          //总章节数,
    var annotations: Map<Int, List<TextTag>> = emptyMap<Int, List<TextTag>>(),    //每个自然段包含的文本标记
    var textCssInfos: Map<Int, TextCssInfo> = emptyMap<Int, TextCssInfo>(),       //每个自然段包含的css标记
    var readerTexts: List<ReaderText> = emptyList<ReaderText>(),                  //按自然段分布的文本内容，
    var wordCount: Long = 0L,                                                       //字数
    var picCount: Long = 0L,                                                        //图片数
    var chapterProgress: Float = 0f,
    var totalWordCount: Long = 0L,
) {

    fun page(index: Int): TextPage? {
        return pages.getOrNull(index)
    }

    val lastPage: TextPage? get() = pages.lastOrNull()

    val lastIndex: Int get() = pages.lastIndex

    val pageSize: Int get() = pages.size

    fun isLastIndex(index: Int): Boolean {
        return index >= pages.size - 1
    }

    fun getReadLength(pageIndex: Int): Int {
        var length = 0
        val maxIndex = min(pageIndex, pages.size)
        for (index in 0 until maxIndex) {
            length += pageLengths[index]
        }
        return length
    }

    fun getUnRead(pageIndex: Int): String {
        val stringBuilder = StringBuilder()
        if (pages.isNotEmpty()) {
            for (index in pageIndex..pages.lastIndex) {
                stringBuilder.append(pages[index].text)
            }
        }
        return stringBuilder.toString()
    }

    fun getContent(): String {
        val stringBuilder = StringBuilder()
        pages.forEach {
            stringBuilder.append(it.text)
        }
        return stringBuilder.toString()
    }

}
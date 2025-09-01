package com.wxn.bookread.ext

import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextChapter

/****
 * 根据段落索引，和段落内的文字偏移，计算整体的进度百分比
 */
fun TextChapter?.calcProgress(paragraphIndex: Int, innerTextOffset: Int) : Double {
    val textChapter = this
    textChapter ?: return 0.0
    var progress = textChapter.chapterProgress.toDouble() //一个章节从全部内容的多少比值之后开始

    val chapterWordCount = textChapter.wordCount.toDouble()
    val totalWordCount = textChapter.totalWordCount.toDouble()
    if (totalWordCount <= 0.0 || chapterWordCount <= 0.0) {
        return progress
    }

    val chapterPercent = chapterWordCount / totalWordCount
    var words = 0
    for((index, paragraph) in textChapter.readerTexts.withIndex()) {
        if (index < paragraphIndex) {
            words += if (paragraph is ReaderText.Text) {
                paragraph.line.length
            } else if (paragraph is ReaderText.Chapter) {
                paragraph.title.length
            } else if (paragraph is ReaderText.Image) {
                1
            } else {
                0
            }
        } else if (index == paragraphIndex) {
            words += innerTextOffset
        } else {
            break
        }
    }
    progress += (words / chapterWordCount) * chapterPercent
    return progress
}
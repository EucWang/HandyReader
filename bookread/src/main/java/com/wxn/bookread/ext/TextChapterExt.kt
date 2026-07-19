package com.wxn.bookread.ext

import com.wxn.base.bean.Locator
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

/****
 * 返回-1, 没有找到对应的页面, 不属于同一个章节
 */
fun TextChapter.getPageIndexFromLocator(locator: Locator): Int {
    if (this.position != locator.chapterIndex) return -1

    val paragraphIndex = locator.startParagraphIndex
    val startTextIndex = locator.startTextOffset
    val pages = this.pages

    var bestMatchIndex = -1
    var bestMatchLevel = 0  // 0=no match, 1=paragraph range, 2=boundary, 3=exact(returned)

    for ((index, page) in pages.withIndex()) {
        val lines = page.textLines
        val pageStartParagraphIndex = lines.firstOrNull()?.paragraphIndex ?: -1
        val pageEndParagraphIndex = lines.lastOrNull()?.paragraphIndex ?: -1
        if (pageStartParagraphIndex < 0 || pageEndParagraphIndex < 0) continue

        // Level 1: Exact text offset within a line (immediate return)
        if (paragraphIndex in pageStartParagraphIndex..pageEndParagraphIndex) {
            for (line in lines) {
                if (paragraphIndex != line.paragraphIndex) continue
                if (startTextIndex >= line.charStartOffset && startTextIndex < line.charEndOffset) {
                    return index
                }
            }
        }

        // Level 2: Paragraph in page range (base fallback)
        if (bestMatchLevel < 1 && paragraphIndex in pageStartParagraphIndex..pageEndParagraphIndex) {
            bestMatchIndex = index
            bestMatchLevel = 1
        }

        // Level 3: Paragraph boundary match (better than range)
        if (bestMatchLevel < 2 && paragraphIndex in pageStartParagraphIndex..pageEndParagraphIndex && startTextIndex == 0) {
            bestMatchIndex = index
            bestMatchLevel = 2
        }
    }

    return bestMatchIndex
}
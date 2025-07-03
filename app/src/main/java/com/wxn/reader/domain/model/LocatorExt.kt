package com.wxn.reader.domain.model

import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag


fun Locator.toTextTags(tagid: String,
                       tagtypename: String,
                       tagcolor: String,
                       chapterIndex: Int,
                       readerTexts: List<ReaderText>) : Map<Int, List<TextTag>>{
    val locator = this
    val tagMap = hashMapOf<Int, MutableList<TextTag>>()
    if (locator.chapterIndex == chapterIndex) {   //相同章节
        val startParagraphIndex = locator.startParagraphIndex
        val endParagraphIndex = locator.endParagraphIndex
        val startTextOffset = locator.startTextOffset
        val endTextOffset = locator.endTextOffset
        if (startParagraphIndex >= 0 && endParagraphIndex >= 0 &&
            startParagraphIndex < readerTexts.size && endParagraphIndex < readerTexts.size &&
            startTextOffset >= 0 && endTextOffset >= 0
        ) {
            if (startParagraphIndex == endParagraphIndex) { //起点终点位于同一个自然段中
                val annos = arrayListOf<TextTag>()
                annos.add(
                    TextTag(
                        uuid = tagid,
                        name = tagtypename,
                        start = startTextOffset,
                        end = endTextOffset,
                        params = "color=${tagcolor}"
                    )
                )
                tagMap[startParagraphIndex] = annos
            } else { //起点和终点位于不同的自然段中
                for (i in startParagraphIndex..endParagraphIndex) {
                    val content = readerTexts[i]
                    var start = 0
                    var end = 0
                    if (i == startParagraphIndex) { //起始自然段，终点需要确认
                        start = startTextOffset
                        end = if (content is ReaderText.Text) {
                            content.line.length
                        } else if (content is ReaderText.Chapter) {
                            content.title.length
                        } else {
                            startTextOffset
                        }
                    } else if (i == endParagraphIndex) {
                        start = 0
                        end = endTextOffset
                    } else {
                        start = 0
                        end = if (content is ReaderText.Text) {
                            content.line.length
                        } else if (content is ReaderText.Chapter) {
                            content.title.length
                        } else {
                            0
                        }
                    }

                    var annos = tagMap.get(i)?.toMutableList()
                    if (annos == null) {
                        annos = arrayListOf<TextTag>()
                    }
                    annos.add(
                        TextTag(
                            uuid = tagid,
                            name = tagtypename,
                            start = start,
                            end = end,
                            params = "color=${tagcolor}"
                        )
                    )
                    tagMap[i] = annos
                }
            }
        }
    }
    return tagMap
}

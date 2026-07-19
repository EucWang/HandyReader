package com.wxn.reader.domain.model

import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag


private fun ReaderText.length(): Int = when (this) {
    is ReaderText.Text -> line.length
    is ReaderText.Chapter -> title.length
    else -> 0
}

fun Locator.toTextTags(tagid: String,
                       tagtypename: String,
                       tagcolor: String,
                       chapterIndex: Int,
                       readerTexts: List<ReaderText>) : Map<Int, List<TextTag>>{
    val locator = this
    val tagMap = hashMapOf<Int, MutableList<TextTag>>()
    if (locator.chapterIndex == chapterIndex) {
        val startParagraphIndex = locator.startParagraphIndex
        val endParagraphIndex = locator.endParagraphIndex
        val startTextOffset = locator.startTextOffset
        val endTextOffset = locator.endTextOffset
        if (startParagraphIndex >= 0 && endParagraphIndex >= 0 &&
            startParagraphIndex < readerTexts.size && endParagraphIndex < readerTexts.size &&
            startTextOffset >= 0 && endTextOffset >= 0
        ) {
            val endOffsetExclusive = endTextOffset + 1
            if (startParagraphIndex == endParagraphIndex) {
                val paragraphLength = readerTexts[startParagraphIndex].length()
                val annos = arrayListOf<TextTag>()
                annos.add(
                    TextTag(
                        uuid = tagid,
                        name = tagtypename,
                        start = startTextOffset,
                        end = endOffsetExclusive.coerceAtMost(paragraphLength),
                        params = "color=${tagcolor}"
                    )
                )
                tagMap[startParagraphIndex] = annos
            } else {
                for (i in startParagraphIndex..endParagraphIndex) {
                    val content = readerTexts[i]
                    val paragraphLength = content.length()
                    var start = 0
                    var end = 0
                    if (i == startParagraphIndex) {
                        start = startTextOffset
                        end = paragraphLength
                    } else if (i == endParagraphIndex) {
                        start = 0
                        end = endOffsetExclusive.coerceAtMost(paragraphLength)
                    } else {
                        start = 0
                        end = paragraphLength
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

package com.wxn.reader.presentation.mainReader.helpers

import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger


/****
 * 辅助章节跳转/位置跳转的帮助类
 */
object JumpHelper {

    /****
     * 根据 targetSrcName, targetAnchorId， fullHref 从 chapters 中找到目标章节
     */
    internal fun findTargetChapter(
        chapters: List<BookChapter>,
        targetSrcName: String,
        targetAnchorId: String,
        fullHref: String
    ): BookChapter? {
        chapters.find { it.srcName == fullHref }?.let { return it }

        if (targetSrcName.isNotEmpty()) {
            chapters.find { chapter ->
                val src = chapter.srcName?.substringBefore("#") ?: return@find false
                src.equals(targetSrcName, ignoreCase = true) ||
                        src.endsWith("/$targetSrcName", ignoreCase = true) ||
                        targetSrcName.endsWith("/$src", ignoreCase = true)
            }?.let { return it }
        }

        if (targetSrcName.isNotEmpty() && targetAnchorId.isNotEmpty()) {
            val combined = "$targetSrcName#$targetAnchorId"
            chapters.find { it.srcName?.equals(combined, ignoreCase = true) == true }?.let { return it }
        }

        if (targetAnchorId.isNotEmpty()) {
            val suffix = "#$targetAnchorId"
            chapters.find { it.srcName?.endsWith(suffix, ignoreCase = true) == true }?.let { return it }
        }

        if (targetAnchorId.length >= 4) {
            chapters.find { chapter ->
                val src = chapter.srcName ?: return@find false
                val hashIdx = src.indexOf("#")
                hashIdx >= 0 && src.substring(hashIdx + 1).contains(targetAnchorId, ignoreCase = true)
            }?.let { return it }
        }

        if (targetAnchorId.isNotEmpty() && targetSrcName.isNotEmpty()) {
            val targetPos = targetAnchorId.toLongOrNull()
            if (targetPos != null) {
                val sameFileChapters = chapters.filter { chapter ->
                    val src = chapter.srcName ?: return@filter false
                    val filePart = src.substringBefore("#")
                    filePart.equals(targetSrcName, ignoreCase = true)
                }.sortedBy { it.chapterIndex }

                for (i in sameFileChapters.indices) {
                    val chapter = sameFileChapters[i]
                    val src = chapter.srcName ?: continue
                    val startAnchor = src.substringAfter("#", "").toLongOrNull() ?: 0L
                    val nextChapter = sameFileChapters.getOrNull(i + 1)
                    val endAnchor = nextChapter?.srcName
                        ?.substringAfter("#", "")?.toLongOrNull()
                        ?: Long.MAX_VALUE
                    if (targetPos in startAnchor until endAnchor) {
                        Logger.d("MainReaderViewModel::findTargetChapter:S5 range match, chapter=${chapter.chapterIndex}, range=[$startAnchor, $endAnchor)")
                        return chapter
                    }
                }
            }
        }

        if (targetAnchorId.isEmpty() && targetSrcName.isNotEmpty()) {
            chapters.find { it.srcName == targetSrcName }?.let { return it }
        }

        if (targetAnchorId.isEmpty() && targetSrcName.isNotEmpty()) {
            chapters.find { it.srcName?.endsWith(targetSrcName, ignoreCase = true) == true }?.let { return it }
        }

        return null
    }


    /***
     * 遍历一个章节的全部自然段内容，找到锚点对应的段落索引
     * @return  锚点对应的段落索引
     */
    internal fun findAnchorParagraphIndex(texts: List<ReaderText>, anchorId: String): Int {
        for (index in texts.indices) {
            val paragraph = texts[index]
            if (paragraph is ReaderText.Text) {
                val tag = paragraph.annotations.firstOrNull {
                    it.anchorId.isNotEmpty() && it.anchorId == anchorId
                }
                if (tag != null) {
                    return index
                }
            }
        }
        return -1
    }


    /**
     * 链接是标注完整的链接，则返回解析之后的两部分，前面是资源文件名，后面是锚点名称
     */
    internal fun parseHrefForFileCheck(href: String): Pair<String, String> {
        return if (href.contains("#")) {
            val parts = href.split("#")
            if (parts.size == 2) {
                Pair(parts[0], parts[1])
            } else {
                Pair("", "")
            }
        } else {
            Pair(href, "")
        }
    }

    /****
     * 锚点的资源文件名是否和当前章节的资源是相同的，
     * 锚点的资源文件名 为空，则表示这个锚点就是当前章节中的锚点
     */
    internal fun isSameFile(hrefSrcName: String, currentChapterSrc: String?): Boolean {
        if (hrefSrcName.isEmpty()) return true
        if (currentChapterSrc.isNullOrEmpty()) return false

        val currentFile = currentChapterSrc.substringBefore("#")
        if (currentFile.isEmpty()) return false

        return currentFile.equals(hrefSrcName, ignoreCase = true) ||
                currentFile.endsWith("/$hrefSrcName", ignoreCase = true) ||
                hrefSrcName.endsWith("/$currentFile", ignoreCase = true)
    }
}
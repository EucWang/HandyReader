package com.wxn.bookread.ui

import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.model.SpeekBookStatus
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.format

class TextPageFactory(dataSource: IDataSource, val provider: PageViewDataProvider) :
    IPageFactory<TextPage>(dataSource) {

    /***
     * 是否有上页
     */
    override fun hasPrev(): Boolean = with(dataSource) {
        if (currentChapter == null) return hasPrevChapter()
        return hasPrevChapter() || pageIndex > 0
    }

    /***
     * 是否有下页
     */
    override fun hasNext(): Boolean = with(dataSource) {
        if (currentChapter == null) return false
        return hasNextChapter() || currentChapter?.isLastIndex(pageIndex) != true
    }

    /***
     * 是否有下下页
     */
    override fun hasNextPlus(): Boolean = with(dataSource) {
        if (currentChapter == null) return false
        return hasNextChapter() || pageIndex < (currentChapter?.pageSize ?: 1) - 2
    }


    override fun moveToFirst() {
        provider.setPageIndex(0)
    }

    override fun moveToLast() = with(dataSource) {
        currentChapter?.let {
            if (it.pageSize == 0) {
                provider.setPageIndex(0)
            } else {
                provider.setPageIndex(it.pageSize.minus(1))
            }
        } ?: provider.setPageIndex(0)
    }

    /***
     * 移动到下一页
     */
    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasNext()) {
            if (currentChapter?.isLastIndex(pageIndex) == true) {
                provider.moveToNextChapter(upContent)
            } else {
                provider.setPageIndex(pageIndex.plus(1))
            }
            if (upContent && currentChapter != null) upContent(resetPageOffset = false)
            true
        } else
            false
    }


    override fun moveToPrev(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasPrev()) {
            if (pageIndex <= 0) {
                provider.moveToPrevChapter(upContent)
            } else {
                provider.setPageIndex(pageIndex.minus(1))
            }
            if (upContent && currentChapter != null) upContent(resetPageOffset = false)
            true
        } else
            false
    }


    override val currentPage: TextPage
        get() = with(dataSource) {
            provider.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                return@with it.page(pageIndex)
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPage: TextPage
        get() = with(dataSource) {
            provider.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                if (pageIndex < it.pageSize - 1) {
                    return@with it.page(pageIndex + 1) //?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
            }
            if (!hasNextChapter()) {
                return@with TextPage(text = "")
            }
            nextChapter?.let {
                return@with it.page(0) //?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val prevPage: TextPage
        get() = with(dataSource) {
            provider.msg?.let {
                return@with TextPage(text = it).format()
            }
            if (pageIndex > 0) {
                currentChapter?.let {
                    return@with it.page(pageIndex - 1) //?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
            }
            prevChapter?.let {
                return@with it.lastPage //?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPagePlus: TextPage
        get() = with(dataSource) {
            currentChapter?.let {
                if (pageIndex < it.pageSize - 2) {
                    return@with it.page(pageIndex + 2) //?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                nextChapter?.let { nc ->
                    if (pageIndex < it.pageSize - 1) {
                        return@with nc.page(0) //?.removePageAloudSpan()
                            ?: TextPage(title = nc.title).format()
                    }
                    return@with nc.page(1) //?.removePageAloudSpan()
                        ?: TextPage(title = nc.title).format()
                }
            }
            return TextPage().format()
        }

    /***
     * 根据chapterIndex, paragraphIndex, lineStartOffset, lineEndOffset
     * 得到当前行可能会使用到的TextTag, TextCssInfo
     * @param chapterIndex      章节索引
     * @param paragraphIndex    段落索引
     * @param lineStartOffset   行开始字符偏移索引
     * @param lineEndOffset     行结束字符偏移索引
     */
    override fun getPagesAnnotation(
        chapterIndex: Int,
        paragraphIndex: Int,
        lineStartOffset: Int,
        lineEndOffset: Int
    ):  Pair<List<TextTag>, TextCssInfo?> {
        val curTextChapter = provider.textChapter(0) //?.annotations.orEmpty()
        val preTextChapter = provider.textChapter(-1)
        val nextTextChapter = provider.textChapter(1)

        val chapter = when (chapterIndex) {
            curTextChapter?.position -> {
                curTextChapter
            }

            preTextChapter?.position -> {
                preTextChapter
            }

            nextTextChapter?.position -> {
                nextTextChapter
            }

            else -> dataSource.findChapterByPosition(chapterIndex)
        }
        val textTagMaps: Map<Int, List<TextTag>> = chapter?.annotations.orEmpty()
        val textCssInfos = chapter?.textCssInfos.orEmpty()

        var textTagList = textTagMaps.get(paragraphIndex).orEmpty()
        val textCssInfo = textCssInfos.get(paragraphIndex)

        val effectedTextTags = arrayListOf<TextTag>()
        for (textTag in textTagList) {
            if (textTagAffectsLine(textTag, lineStartOffset, lineEndOffset)) {
                effectedTextTags.add(textTag)
            }
        }
        return Pair(effectedTextTags, textCssInfo)
    }

    override fun getSpeekBookStatus(): SpeekBookStatus {
        return provider.getSpeakBookStatus()
    }

    /**
     * F5 新增:按 chapterIndex + paragraphIndex 反查原始 ReaderText(供绘制层取 inlineFontSizes)。
     *
     * - 跨章选择逻辑与 [getPagesAnnotation] 一致(预取 -1/0/+1 章)
     * - 找不到章节/段落越界 → 返回 null(绘制层降级为段落默认字号)
     *
     * 时序安全:readerTexts 在 PageViewController.loadChapter L976 回填 → L997 赋给 curTextChapter →
     * L1057 upContent 触发绘制,严格顺序、单线程(limitedParallelism(1)),绘制期访问必非空。
     *
     * 注:本方法加到 TextPageFactory 子类(非抽象父类 IPageFactory),因为 ContentTextView.pageFactory
     * 类型是 TextPageFactory?(已核实),可直接调用。
     */
    fun getReaderText(chapterIndex: Int, paragraphIndex: Int): ReaderText? {
        val curTextChapter = provider.textChapter(0)
        val preTextChapter = provider.textChapter(-1)
        val nextTextChapter = provider.textChapter(1)

        val chapter = when (chapterIndex) {
            curTextChapter?.position -> curTextChapter
            preTextChapter?.position -> preTextChapter
            nextTextChapter?.position -> nextTextChapter
            else -> dataSource.findChapterByPosition(chapterIndex)   // 兜底(IDataSource 接口方法)
        } ?: return null

        val readerTexts = chapter.readerTexts
        if (paragraphIndex < 0 || paragraphIndex >= readerTexts.size) return null
        return readerTexts[paragraphIndex]
    }
}

/** 行-[tag] 匹配谓词：半开区间严格相交（TextTag [start, end) 契约）。
 *  旧实现的闭区间端点匹配会把「行终点 == tag 起点」的相邻行误判命中
 * （نهاية 案例：行 [19,24) 误命中 tag [24,29)），空行/边框行 [0,0) 也会命中
 * tag.start==0 的段落——严格相交同时消除这两族过绘。 */
internal fun textTagAffectsLine(tag: TextTag, lineStartOffset: Int, lineEndOffset: Int): Boolean =
    tag.start < lineEndOffset && tag.end > lineStartOffset
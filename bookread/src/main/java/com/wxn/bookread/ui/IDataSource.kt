package com.wxn.bookread.ui

import com.wxn.bookread.data.model.TextChapter

interface IDataSource : PageChangeCallback {
    /***
     * 当前章节中正在显示的页面的索引
     */
    var pageIndex: Int  // =  ReadBook.durChapterPos()

    val currentChapter: TextChapter?

    val nextChapter: TextChapter?

    val prevChapter: TextChapter?

    fun hasNextChapter(): Boolean

    fun hasPrevChapter(): Boolean

    /**
     * 根据章节绝对位置查找 TextChapter
     *
     * 默认返回 null。连续滚动模式下由 ContinuousPageProvider 重写，
     * 同时检查 3 槽位缓存和 preloadedChapters，确保预加载章节的
     * 注解数据可被 getPagesAnnotation 正确查找。
     */
    fun findChapterByPosition(position: Int): TextChapter? = null

}

interface PageChangeCallback {

//    fun upSelectedRange(startCharX: Float, startCharY: Float, endCharX: Float, endCharY: Float)

    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)

    fun upStyle()

    fun upTipStyle()

    fun upBg()

    fun cancelTextSelected()

    fun moveToPrevPage()

    fun moveToNextPage()

}
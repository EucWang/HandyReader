package com.wxn.bookread.ui

import com.wxn.bookread.data.model.TextChapter

interface IDataSource {
    /***
     * 当前章节中正在显示的页面的索引
     */
    val pageIndex: Int get() = 0 // ReadBook.durChapterPos() TODO

    val currentChapter: TextChapter?

    val nextChapter: TextChapter?

    val prevChapter: TextChapter?

    fun hasNextChapter(): Boolean

    fun hasPrevChapter(): Boolean

    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)
}
package com.wxn.bookread.ui

import com.wxn.bookread.data.model.TextChapter

interface PageViewDataProvider : PageViewCallback{

    /**
     * chapterOnDur: 0为当前页,1为下一页,-1为上一页
     */
    fun textChapter(index: Int) : TextChapter?

    var durChapterIndex: Int

    var chapterSize: Int

    var msg: String?

    /**
     * 加载章节内容
     */
    fun loadContent(resetPageOffset: Boolean)

    fun setPageIndex(index: Int)

    fun moveToNextChapter(upContent: Boolean)

    fun moveToPrevChapter(upContent: Boolean)
}
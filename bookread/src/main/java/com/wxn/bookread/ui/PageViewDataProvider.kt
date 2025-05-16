package com.wxn.bookread.ui

import com.wxn.bookread.data.model.TextChapter

interface PageViewDataProvider {

    fun textChapter(index: Int) : TextChapter?

    var durChapterIndex: Int

    var chapterSize: Int

    fun loadContent(resetPageOffset: Boolean)
}
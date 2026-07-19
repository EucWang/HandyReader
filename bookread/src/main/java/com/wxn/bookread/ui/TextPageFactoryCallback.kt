package com.wxn.bookread.ui

import com.wxn.base.bean.Book
import com.wxn.base.bean.Locator

interface TextPageFactoryCallback {

    var pageFactory : TextPageFactory?

    var book: Book?

    fun getSearchHighlights(): List<Locator> = emptyList()
}
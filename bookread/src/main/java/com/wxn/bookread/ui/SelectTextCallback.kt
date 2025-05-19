package com.wxn.bookread.ui

import com.wxn.base.bean.Book

interface SelectTextCallback : TextPageFactoryCallback {
    fun upSelectedStart(x: Float, y: Float, top: Float)

    fun upSelectedEnd(x: Float, y: Float)

    fun onCancelSelect()

    var headerHeight: Int

    var isScroll: Boolean
}

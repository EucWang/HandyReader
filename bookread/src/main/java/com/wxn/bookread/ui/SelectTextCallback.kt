package com.wxn.bookread.ui

import kotlinx.coroutines.CoroutineScope


interface SelectTextCallback {
    fun upSelectedStart(x: Float, y: Float, top: Float)

    fun upSelectedEnd(x: Float, y: Float)

    fun onCancelSelect()

    val headerHeight: Int

    val pageFactory: TextPageFactory

    val scope: CoroutineScope

    val isScroll: Boolean
}

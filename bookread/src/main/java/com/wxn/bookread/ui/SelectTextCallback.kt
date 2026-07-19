package com.wxn.bookread.ui

import com.wxn.base.bean.Locator

interface SelectTextCallback : TextPageFactoryCallback {
    fun upSelectedStart(x: Float, y: Float, top: Float, paragraphIndex: Int, innerTextOffset: Int)

    fun upSelectedEnd(x: Float, y: Float, paragraphIndex: Int, innerTextOffset: Int)

    fun onCancelSelect()

    fun getSelectionLocator(): Locator?

    /***
     * 系统状态栏高度
     */
    var headerHeight: Int

    var isScroll: Boolean
}

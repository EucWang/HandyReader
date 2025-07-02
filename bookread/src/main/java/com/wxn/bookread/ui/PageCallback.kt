package com.wxn.bookread.ui

import com.wxn.base.bean.Book
import com.wxn.base.bean.Locator

/***
 * 页面回调， 通过PageCallback，来控制界面刷新
 */
interface PageCallback  : PageChangeCallback{
    fun loadChapterList(book: Book)
    fun upView()

    /***
     * 通知界面刷新进度
     */
    fun pageChanged()
    fun contentLoadFinish()
    fun upPageAnim()

    fun getSelectedText() : String
}

package com.wxn.bookread.ui

interface PageViewCallback  : TextPageFactoryCallback {

    /***
     *
     */
    var isInitFinish: Boolean

    /***
     *
     */
    var isAutoPage: Boolean

    /***
     *
     */
    var autoPageProgress: Int

    /***
     *
     */
    fun clickCenter()

    /***
     *
     */
    fun screenOffTimerStart()

    /***
     *
     */
    fun showTextActionMenu()

    /***
     * 当前章节中正在显示的页面的索引
     */
    fun durChapterPos(): Int
}
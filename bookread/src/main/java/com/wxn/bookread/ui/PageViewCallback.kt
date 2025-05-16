package com.wxn.bookread.ui

interface PageViewCallback {

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

}
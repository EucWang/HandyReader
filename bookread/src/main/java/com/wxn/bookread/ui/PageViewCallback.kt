package com.wxn.bookread.ui

interface PageViewCallback {

    /***
     *
     */
    val isInitFinish: Boolean

    /***
     *
     */
    val isAutoPage: Boolean

    /***
     *
     */
    val autoPageProgress: Int

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
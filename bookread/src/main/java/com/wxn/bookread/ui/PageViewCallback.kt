package com.wxn.bookread.ui

import com.wxn.base.bean.TextTag

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

    /***
     * click href link
     */
    fun clickLink(tag: TextTag, clickX: Float, clickY: Float)

    /****
     * click annotation like underline/highlight/note..
     */
    fun clickedAnnotation(annotationIds: List<String>)

    fun clickedNote(noteId: String)
}
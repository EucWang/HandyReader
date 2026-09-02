package com.wxn.bookread.data.model

import android.text.Layout
import android.text.StaticLayout
import com.wxn.base.util.Logger
import com.wxn.bookread.provider.ChapterProvider
import java.text.DecimalFormat


/****
 * 一页显示的书籍文案内容
 * 包含若干TextLine
 */
data class TextPage(
    /***
     * 页面索引
     */
    var index: Int = 0,
    var text: String = "", //App.INSTANCE.getString(R.string.data_loading),

    /***
     * 标题
     */
    var title: String = "",
    /***
     * 行数据列表
     */
    val textLines: ArrayList<TextLine> = arrayListOf(),
    /***
     * 当前所在章节包含的页面数
     */
    var pageSize: Int = 0,
    /***
     * 章节数
     */
    var chapterSize: Int = 0,
    /***
     * 章节索引
     */
    var chapterIndex: Int = 0,
    /***
     * 高度
     */
    var height: Float = 0f,

    var bookmarkId: Long = -1,
) {

}
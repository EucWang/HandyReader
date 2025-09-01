package com.wxn.bookread.data.model.preference

/****
 * 阅读额外部分设置
 */
data class ReadTipPreferences constructor(
    var headerPaddingBottom: Int,
    var headerPaddingLeft: Int,
    var headerPaddingRight: Int,
    var headerPaddingTop: Int,

    var footerPaddingBottom: Int,
    var footerPaddingLeft: Int,
    var footerPaddingRight: Int,
    var footerPaddingTop: Int,

    val tipHeaderLeft: Int,
    val tipHeaderMiddle: Int,
    val tipHeaderRight: Int,

    val tipFooterLeft: Int,
    val tipFooterMiddle: Int,
    val tipFooterRight: Int,

    val hideHeader: Boolean,
    val hideFooter: Boolean,

    /****
     * 页面切换动画方式
     *  0   -> pageDelegate = CoverPageDelegate(this) 覆盖
     *  1   -> pageDelegate = SlidePageDelegate(this)   滑动
     *  2   -> pageDelegate = SimulationPageDelegate(this) 仿真
     *  3   -> pageDelegate = ScrollPageDelegate(this)  滚动
     *  else -> pageDelegate = NoAnimPageDelegate(this) 无动画
     */
//    val pageAnim: Int,

//    val hideStatusBar: Boolean,
//    val hideNavigationBar: Boolean,

    val clickTurnPage: Boolean,
    val clickAllNext: Boolean,
    val textFullJustify: Boolean,
    val textBottomJustify: Boolean
) {
}
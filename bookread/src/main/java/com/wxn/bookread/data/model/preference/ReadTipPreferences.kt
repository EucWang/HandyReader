package com.wxn.bookread.data.model.preference

/****
 * 阅读额外部分设置
 */
data class ReadTipPreferences constructor(
    val tipHeaderLeft: Int,
    val tipHeaderMiddle: Int,
    val tipHeaderRight: Int,

    val tipFooterLeft: Int,
    val tipFooterMiddle: Int,
    val tipFooterRight: Int,

    val hideHeader: Boolean,
    val hideFooter: Boolean
) {
}
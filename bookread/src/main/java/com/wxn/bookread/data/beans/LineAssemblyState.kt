package com.wxn.bookread.data.beans

import com.wxn.bookread.data.model.TextLine


/***
 * 行装配状态：
 * 挂起行 + 该行块记录；
 * 行关闭时（新行创建或段落结束）执行粘合段重锚
 */
data class LineAssemblyState(
    var pendingLine: TextLine? = null,
    val blocks: ArrayList<LineBlockRecord> = ArrayList<LineBlockRecord>()
)
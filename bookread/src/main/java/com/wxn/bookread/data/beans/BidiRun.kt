package com.wxn.bookread.data.beans

data class BidiRun(
    val offset: Int,
    val length: Int,
    val level: Int,
) {
    val isRtl: Boolean get() = (level and 1) == 1
    val isLtr: Boolean get() = !isRtl
    val end: Int get() = offset + length

    override fun toString(): String =
        "BidiRun[offset=$offset,len=$length,end=$end,level=$level,${if (isRtl) "RTL" else "LTR"}]"
}
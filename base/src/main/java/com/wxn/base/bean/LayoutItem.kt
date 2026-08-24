package com.wxn.base.bean

sealed class LayoutItem {

    data class Run(val run: RunLayout) : LayoutItem()       // 文本 bidi（子）run

    data class Image(val tag: TextTag) : LayoutItem()       // 行内图片标记
}
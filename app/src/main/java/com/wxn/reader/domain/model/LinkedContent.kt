package com.wxn.reader.domain.model

/***
 * 界面上显示链接的内容
 */
data class LinkedContent(
    val content: String,
    val clickX: Float,
    val clickY: Float
)
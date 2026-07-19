package com.wxn.mobi.data.model

import com.wxn.base.bean.TextTag

class ParagraphData(
    val line : ByteArray,
    var tags: List<TextTag> = emptyList<TextTag>()
)
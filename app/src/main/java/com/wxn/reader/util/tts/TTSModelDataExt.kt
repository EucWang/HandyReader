package com.wxn.reader.util.tts

import com.wxn.base.util.toLocale
import com.wxn.reader.domain.model.TTSModelData


/***
 * 对 locale 字段的显示加强
 */
fun TTSModelData.displayLocales() : String {

    if (this.locale.isEmpty() || this.locale.isBlank()) {
        return this.locale
    }

    if (!this.locale.contains(",")) {
        this.locale.toLocale()?.displayName ?: this.locale
    }

    this.locale.split(",").map { item ->
        item.toLocale()?.displayName ?: item
    }.let {
        return it.joinToString(",")
    }
}
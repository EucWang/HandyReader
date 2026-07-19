package com.wxn.reader.util

import com.spreada.utils.chinese.ZHConverter
import com.wxn.reader.data.source.local.AppPreferencesUtil
import javax.inject.Inject

class BookChineseConverter @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil
) {
    suspend fun convert(text: String): String {
        if (text.isEmpty()) return text
        val type = appPreferencesUtil.chineseConverterType()
        return when (type) {
            1 -> ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(text)
            2 -> ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(text)
            else -> text
        }
    }
}

package com.wxn.reader.util

import android.content.Context
import com.wxn.reader.data.model.TranslatorItem

object DictionaryHelper {

    private val KNOWN_DICT_PACKAGES = setOf(
        "com.eusoft.eudic",
        "com.eusoft.eudic.free",
        "com.mdict.android",
        "com.dictcn.dict",
        "cn.zzzk.dict",
        "com.qianfan.dict",
        "com.dreyer.dict",
        "com.hjeng.djdict",
        "com.leo.dictionary",
        "com.merriamwebster.dictionary",
        "com.oxford.dictionary",
        "com.wordwebsoftware.android",
        "com.youdao.dict",
        "com.youdao.hindict",
        "com.iciba.powerspeak.translator",
    )

    private val DICT_KEYWORDS = listOf(
        "dict",
        "dictionary",
        "eudic",
        "mdict",
        "cidian",
    )

    private fun isDictionaryApp(packageName: String): Boolean {
        if (packageName in KNOWN_DICT_PACKAGES) return true
        val lowerPkg = packageName.lowercase()
        return DICT_KEYWORDS.any { keyword -> lowerPkg.contains(keyword) }
    }

    fun isAppAvailable(context: Context, dictId: String): Boolean {
        return TextProcessAppHelper.isAppAvailable(
            context,
            dictId,
            com.wxn.reader.data.remote.api.Constants.BUILT_IN_DICTIONARY
        )
    }

    fun getInstalledDictionaryItems(context: Context): List<TranslatorItem> {
        return TextProcessAppHelper.getInstalledItems(context, ::isDictionaryApp)
    }

    fun sendTextToDictAppById(context: Context, dictId: String, text: String): Boolean {
        return TextProcessAppHelper.sendTextToAppById(
            context, dictId, text,
            com.wxn.reader.R.string.dict_app_not_found
        )
    }
}

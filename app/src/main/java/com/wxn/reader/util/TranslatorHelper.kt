package com.wxn.reader.util

import android.content.Context
import android.content.pm.ResolveInfo
import com.wxn.reader.data.model.TranslatorItem

object TranslatorHelper {

    private val KNOWN_TRANSLATOR_PACKAGES = setOf(
        "com.google.android.apps.translate",
        "com.deepl.mobiletranslator",
        "com.deepl.mobiletranslator.intune",
        "com.youdao.dict",
        "com.youdao.hindict",
        "com.baidu.baidutranslate",
        "com.tencent.translator",
        "com.microsoft.translator",
        "com.naver.labs.translator",
        "com.yandex.translate",
        "ru.yandex.translate",
        "com.samsung.android.app.translate",
        "com.softissimo.reverso.context",
        "com.sonicomobile.itranslate",
        "com.ticktalk.translatevoice",
        "com.bkms.translator",
        "com.iciba.powerspeak.translator",
    )

    private val TRANSLATOR_KEYWORDS = listOf(
        "translate",
        "translator",
        "translation",
        "deepl",
        "papago",
        "reverso",
    )

    private fun isTranslatorApp(packageName: String): Boolean {
        if (packageName in KNOWN_TRANSLATOR_PACKAGES) return true
        val lowerPkg = packageName.lowercase()
        return TRANSLATOR_KEYWORDS.any { keyword -> lowerPkg.contains(keyword) }
    }

    fun getTextProcessApps(context: Context) = TextProcessAppHelper.getTextProcessApps(context)

    fun sendTextToApp(context: Context, text: String?, resolveInfo: ResolveInfo?) =
        TextProcessAppHelper.sendTextToApp(context, text, resolveInfo)

    fun shareText(context: Context, text: String?) =
        TextProcessAppHelper.shareText(context, text)

    fun isAppAvailable(context: Context, translatorId: String): Boolean {
        return TextProcessAppHelper.isAppAvailable(
            context,
            translatorId,
            com.wxn.reader.data.remote.api.Constants.AI_TRANSILATOR
        )
    }

    fun getInstalledTranslatorItems(context: Context): List<TranslatorItem> {
        return TextProcessAppHelper.getInstalledItems(context, ::isTranslatorApp)
    }

    fun sendTextToAppById(context: Context, translatorId: String, text: String): Boolean {
        return TextProcessAppHelper.sendTextToAppById(
            context, translatorId, text,
            com.wxn.reader.R.string.translator_app_not_found
        )
    }
}

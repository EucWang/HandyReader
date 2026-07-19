package com.wxn.base.util.numReplacer

import com.wxn.base.util.Logger
import java.util.Locale

object NumberReplaceHelper {

    private var numReplacer: INumberReplacer? = null

    fun replace(text: String, locale: Locale): String {
        Logger.i("NumberReplaceHelper:replace:text=${text},locale=$locale")
        val lang = locale.language
        val cacheHit = numReplacer != null && (
            (lang == "zh" && numReplacer is ZhNumberReplacer) ||
            (lang == "en" && numReplacer is EnNumberReplacer) ||
            (lang == "es" && numReplacer is EsNumberReplacer) ||
            (lang == "pt" && numReplacer is PtNumberReplacer) ||
            (lang == "fr" && numReplacer is FrNumberReplacer)
        )
        if (!cacheHit) {
            numReplacer = when (lang) {
                "zh" -> ZhNumberReplacer()
                "en" -> EnNumberReplacer()
                "es" -> EsNumberReplacer()
                "pt" -> PtNumberReplacer()
                "fr" -> FrNumberReplacer()
                else -> null
            }
        }

        return numReplacer?.replace(text) ?: text
    }

}
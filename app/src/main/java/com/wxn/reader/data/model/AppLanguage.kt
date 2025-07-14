package com.wxn.reader.data.model

import java.util.Locale


enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "System Default"),
    ENGLISH("en-US", "English"),           //英语
    FRENCH("fr", "Français"),           //法语
    GERMAN("de", "Deutsch"),            //德语
    SPANISH("es", "Español"),           //西班牙语
    PORTUGUESE("pt", "Português"),      //葡萄牙语
    CHINESES_SIMPLE("zh-CN", "中文（繁体）"),               //中文
    CHINESE_TRADITIONAL("zh-TW", "中文（繁体）"),               //中文
    JAPANESE("ja", "日本語"),            //日语
    RUSSIAN("ru", "Русский"),           //俄罗斯语
    ARABIC("ar", "العربية"),            //阿拉伯语
    HINDI("hi", "हिन्दी");                 //印地语
    //如下几种语言暂不支持
//    SWEDISH("sv", "Svenska"),         //瑞典语
//    DUTCH("nl", "Nederlands"),        //荷兰语
//    ITALIAN("it", "Italiano"),          //意大利语
//    TURKISH("tr", "Türkçe"),            //土耳其语
//    KOREAN("ko", "한국어"),               //韩语

    val locale: Locale by lazy {
        java.util.Locale.forLanguageTag(code)
    }

    val isRegional:Boolean by lazy {
        locale.country.isNotEmpty()
    }

    fun removeRegion(): AppLanguage {
        return AppLanguage.fromCode(code.split("-", limit = 2).first())
    }

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: SYSTEM

        fun fromLocale(locale: Locale) : AppLanguage {
            return fromCode(locale.toLanguageTag())
        }
    }
}
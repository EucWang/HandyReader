package com.wxn.reader.util

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.wxn.base.util.Coroutines
import com.wxn.reader.data.source.local.AppPreferencesUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

data class LanguageInfo(
    val id: Long = 0,
    val lang: String,
    val country: String,
    val code: String,
    val locale: Locale,
    val displayName: String
) {

    companion object {
        fun fromCode(code: String): LanguageInfo? {
            if (code.isEmpty()) {
                return null
            }
            var lang = ""
            var country = ""
            val index = code.indexOfFirst { ch -> ch == '-' }
            if (index >= 0) {
                lang = code.substring(0, index)
                country = code.substring(index + 1)
            } else {
                lang = code
            }
            if (lang.isEmpty()) {
                return null
            }
            val ret = when(lang) {
                "en" -> LanguageUtil.LANG_EN
                "fr" -> LanguageUtil.LANG_FR
                "de" -> LanguageUtil.LANG_DE

                "es" -> LanguageUtil.LANG_ES
                "pt" -> LanguageUtil.LANG_PT
                "zh" -> LanguageUtil.LANG_ZH

                "ja" -> LanguageUtil.LANG_JA
                "ru" -> LanguageUtil.LANG_RU
                "ar" -> LanguageUtil.LANG_AR
                "hi" -> LanguageUtil.LANG_HI

                else -> {
                    LanguageInfo(
                        id = Random.nextLong() + System.currentTimeMillis(),
                        lang = lang,
                        country = country,
                        code = code,
                        locale = Locale.forLanguageTag(code),
                        displayName = Locale.forLanguageTag(code).displayName
                    )
                }
            }
            return ret
        }
    }

}

object LanguageUtil {

    val LANG_EN = LanguageInfo(1, "en", "", "en", java.util.Locale.ENGLISH, "English")
    val LANG_FR = LanguageInfo(2, "fr", "", "fr", java.util.Locale.FRENCH, "Français")
    val LANG_DE = LanguageInfo(3, "de", "", "de", Locale.GERMAN, "Deutsch")

    val LANG_ES =  LanguageInfo(4, "es", "", "es", Locale.forLanguageTag("es"), "Español")

    val LANG_PT = LanguageInfo(5, "pt", "", "pt", Locale.forLanguageTag("pt"), "Português")

    val LANG_ZH = LanguageInfo(6, "zh", "", "zh", Locale.CHINESE, "中文")

    val LANG_JA = LanguageInfo(7, "ja", "", "ja", Locale.JAPANESE, "日本語")

    val LANG_RU = LanguageInfo(8, "ru", "", "ru", Locale.forLanguageTag("ru"), "Русский")

    val LANG_AR = LanguageInfo(9, "ar", "", "ar", Locale.forLanguageTag("ar"), "العربية")

    val LANG_HI =  LanguageInfo(10, "hi", "", "hi", Locale.forLanguageTag("hi"), "हिन्दी")

    val languageMaps: HashMap<Int, LanguageInfo> = hashMapOf(
        1 to LANG_EN,                      //英语
        2 to LANG_FR,                      //法语
        3 to LANG_DE,                                         //德语
        4 to LANG_ES,           //西班牙语
        5 to LANG_PT,         //葡萄牙语
        6 to LANG_ZH,                                          //中文

        7 to LANG_JA,                                       //日语
        8 to LANG_RU,           //俄罗斯语
        9 to LANG_AR,           //阿拉伯语

        10 to LANG_HI              //印地语
    )

    /**
     * 1.2 版本新增
     * 配置默认的语言, 只配置一次
     */
    fun initDefaultLanguage(context: Context) {
        Coroutines.scope().launch {
            val appPrefs = AppPreferencesUtil(context)
            val prefs = appPrefs.appPrefsFlow.firstOrNull() ?: return@launch
            val curLanguage = prefs.language   //如果为空则是没有配置过语言的,则是新版本

            if (curLanguage.isEmpty()) {
                val sysLocale = getLocale()
                val lang = when (sysLocale.language) {
                    languageMaps[1]?.locale?.language -> {
                        "en"
                    }

                    languageMaps[2]?.locale?.language -> {
                        "fr"
                    }

                    languageMaps[3]?.locale?.language -> {
                        "de"
                    }

                    languageMaps[4]?.locale?.language -> {
                        "es"
                    }

                    languageMaps[5]?.locale?.language -> {
                        "pt"
                    }

                    languageMaps[6]?.locale?.language -> {
                        "zh"
                    }

                    languageMaps[7]?.locale?.language -> {
                        "ja"
                    }

                    languageMaps[8]?.locale?.language -> {
                        "ru"
                    }

                    languageMaps[9]?.locale?.language -> {
                        "ar"
                    }

                    languageMaps[10]?.locale?.language -> {
                        "hi"
                    }

                    else -> {
                        "en"
                    }
                }
                //重新获取语言, 并设置local
                changeLanguage(context, lang)
            }
        }
    }


    private fun updateLocale(context: Context, newLocale: Locale) {
        val config = context.resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(newLocale))
        } else {
            config.setLocale(newLocale)
        }
        context.resources.updateConfiguration(config, null)
    }

    private fun getLocale(): Locale {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Resources.getSystem().configuration.locale
        }
        return locale
    }

    fun changeLanguage(
        context: Context?,
        language: String?,
        updatePrefs: Boolean = true,
        onFinished: (() -> Unit)? = null
    ) {
        if (context == null || language.isNullOrBlank()) {
            return
        }
        val newLocale = Locale(language)
        updateLocale(context, newLocale)
        updateLocale(context.applicationContext, newLocale)
        if (updatePrefs) {
            Coroutines.scope().launch {
                AppPreferencesUtil(context).let { prefsUtil ->
                    val prefs = prefsUtil.appPrefsFlow.firstOrNull() ?: return@launch
                    prefsUtil.updateAppPreferences(prefs.copy(language = language))

                    with(Dispatchers.Main) {
                        onFinished?.invoke()
                    }
                }
            }
        }
    }

}
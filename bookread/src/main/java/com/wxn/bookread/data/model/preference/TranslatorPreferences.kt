package com.wxn.bookread.data.model.preference

data class TranslatorPreferences (
    val lastTargetTransilateLang: String = "",      //上一次用户选择的翻译的语言
    val lastSelectedTranslator: String = "",        //上一次用户选择的翻译的应用信息， 如果是自身的翻译服务则命名为：
)
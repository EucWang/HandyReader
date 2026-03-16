package com.wxn.bookread.data.model

import com.wxn.base.bean.Locator

/***
 * 阅读状态
 */
data class SpeekBookStatus(
    val isSpeaking:Boolean = false,
    val readBookLocator: Locator? = null,
    val playSentenceIndex: Int = 0
)

/***
 * 阅读的句子
 */
data class SpeakSentence(
    val sentence: String,
    val locator: Locator
)
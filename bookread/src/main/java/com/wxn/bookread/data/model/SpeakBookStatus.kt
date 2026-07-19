package com.wxn.bookread.data.model

import com.wxn.base.bean.Locator
import com.wxn.base.bean.TtsPlaybackStatus

/***
 * 阅读状态
 */
data class SpeekBookStatus(

    val speakingStatus: TtsPlaybackStatus = TtsPlaybackStatus.IDLE,

    val readBookLocator: Locator? = null,
    val playSentenceIndex: Int = 0
)

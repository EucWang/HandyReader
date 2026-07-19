package com.wxn.reader.service

import com.wxn.base.bean.Locator
import com.wxn.bookread.data.model.TextChapter
/**
 * 简化的TTS回调接口
 * 只包含必要的方法，所有方法都是suspend函数
 */
interface SimpleTtsCallback {
    /**
     * 句子播放完成, 更新TtsStateHolder中的位置, 以便界面刷新高亮
     * @return true: 继续播放下一个句子, false: 停止播放
     */
    fun onSentenceComplete(locator: Locator, sentenceIndex: Int)

    /**
     * 需要加载下一章
     * @return 下一章的TextChapter，如果为null则停止播放
     */
    fun loadNextChapter(currentChapterIndex: Int): TextChapter?

    /**
     * 播放完成（正常或错误）
     */
    fun onPlaybackComplete(success: Boolean, errorMessage: String? = null)

    /***
     * 播放过程中,检测定时器,是否到点了
     * @return  false : 即已经达到或者超过定时器的时长了,
     *          true: 还没有达到定时器的时长
      */
    fun checkTimer() : Boolean

    fun onTimerExpired()
}
package com.wxn.reader.service

import com.wxn.base.bean.Locator
import com.wxn.bookread.data.model.TextChapter
/**
 * 简化的TTS回调接口
 * 只包含必要的方法，所有方法都是suspend函数
 */
interface SimpleTtsCallback {
    /**
     * 句子播放完成
     * @return true: 继续播放下一个句子, false: 停止播放
     */
    suspend fun onSentenceComplete(locator: Locator, sentenceIndex: Int): Boolean

    /**
     * 需要加载下一章
     * @return 下一章的TextChapter，如果为null则停止播放
     */
    suspend fun loadNextChapter(currentChapterIndex: Int): TextChapter?

    /**
     * 播放完成（正常或错误）
     */
    suspend fun onPlaybackComplete(success: Boolean, errorMessage: String? = null)

}
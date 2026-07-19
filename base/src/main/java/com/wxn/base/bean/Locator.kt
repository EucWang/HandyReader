package com.wxn.base.bean

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject

/***
 * 对应locator字段
 */
@Parcelize
@Immutable
data class Locator(
    val id: String = "",               //id
    val chapterIndex: Int = 0,          //章节索引
    val startParagraphIndex: Int = 0,   //开始的段落索引
    val startTextOffset: Int = 0,       //位于开始的段落的文字偏移量
    val endParagraphIndex: Int = 0,     //结束的段落索引
    val endTextOffset: Int = 0,        //位于结束的段落的文字偏移量。注意：语义因创建路径不同而不同——文本选中时为 inclusive（最后一个选中字符的偏移量），TTS/搜索/页面定位时为 exclusive（最后字符偏移量+1）
    val text: String = "",               //包含的文字内容
    val progression: Double,
) : Parcelable {

    fun toUtteranceId() : String {
        return "$chapterIndex-$startParagraphIndex-$startTextOffset-$endParagraphIndex-$endTextOffset"
    }

    fun toJsonString() : String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("chapter_index", chapterIndex)
        obj.put("start_paragraph_index", startParagraphIndex)
        obj.put("start_text_offset", startTextOffset)
        obj.put("end_paragraph_index", endParagraphIndex)
        obj.put("end_text_offset", endTextOffset)
        obj.put("text", text)
        obj.put("progression", progression)
        return obj.toString()
    }

    companion object {

        fun toUtteranceId(locator: Locator) : String {
            return "${locator.chapterIndex}-${locator.startParagraphIndex}-${locator.startTextOffset}-${locator.endParagraphIndex}-${locator.endTextOffset}"
        }

        fun getUtteranceId(chapterIndex: Int,
                           startParagraphIndex: Int,
                           startTextOffset: Int,
                           endParagraphIndex: Int,
                           endTextOffset: Int) : String {
            return "$chapterIndex-$startParagraphIndex-$startTextOffset-$endParagraphIndex-$endTextOffset"
        }

        fun fromUtteranceId(utteranceId: String): Locator? {
            if (utteranceId.isEmpty() || !utteranceId.contains("-")) {
                return null
            }
            val splits = utteranceId.split("-")
            if (splits.size != 5) {
                return null
            }
            var chapterIndex = -1
            var startParagraphIndex = -1
            var startOffset = -1
            var endParagraphIndex = -1
            var endOffset = -1
            for ((index, split) in splits.withIndex()) {
                val value = split.toIntOrNull() ?: return null
                when(index) {
                    0 -> chapterIndex = value
                    1 -> startParagraphIndex = value
                    2 -> startOffset = value
                    3 -> endParagraphIndex = value
                    4 -> endOffset = value
                }
            }
            return if (chapterIndex < 0 || startParagraphIndex < 0 || startOffset < 0 || endParagraphIndex < 0 || endOffset < 0) {
                 null
            } else {
                Locator(utteranceId, chapterIndex, startParagraphIndex, startOffset, endParagraphIndex, endOffset, "", 0.0)
            }
        }

        fun fromJsonString(jsonString: String): Locator? {
            var ret: Locator? = null
            try {
                val obj = JSONObject(jsonString)
                val id = obj.optString("id", "")
                val chapterIndex = obj.optInt("chapter_index", 0)
                val startParagraphIndex = obj.optInt("start_paragraph_index", 0)
                val startTextOffset = obj.optInt("start_text_offset", 0)
                val endParagraphIndex = obj.optInt("end_paragraph_index", 0)
                val endTextOffset = obj.optInt("end_text_offset", 0)
                val text = obj.optString("text", "")
                val progression = obj.optDouble("progression", 0.0)
                ret = Locator(id, chapterIndex, startParagraphIndex, startTextOffset, endParagraphIndex, endTextOffset, text, progression)
            } catch (ex: JSONException) {
            }
            return ret
        }
    }
}
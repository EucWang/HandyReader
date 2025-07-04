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
    val endTextOffset: Int = 0,        //位于结束的段落的文字偏移量
    val text: String = "",               //包含的文字内容
    val progression: Double,
) : Parcelable {

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
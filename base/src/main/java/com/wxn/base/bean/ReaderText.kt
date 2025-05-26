package com.wxn.base.bean

import androidx.compose.runtime.Immutable

data class TextTag(
    val uuid:String,                //标签的唯一uuid值
    val anchorId : String = "",     //如果是锚点，则有值
    val name: String,               //标签名
    val start: Int = 0,             //标签影响的开始位置
    val end: Int =0,                //标签影响的结束位置
    val parentUuid: String = "",    //父级标签uuid
    val params: String = ""         //字符串拼接的键值对， 需要解析
)

@Immutable
sealed class ReaderText {

    /****
     * 章节
     */
    @Immutable
    data class Chapter(val index: String = "", var title: String, val nested: Boolean) : ReaderText()

    /***
     * 文本内容
     * annotations 对应的文本的样式
     */
    @Immutable
    data class Text(var line: String, var annotations: List<TextTag> = emptyList<TextTag>()): ReaderText()

    /****
     * 分隔符
     */
    @Immutable
    data object Separator : ReaderText()

    @Immutable data class Image(
        val path: String,   //绝对路径
        val width: Int,     //图片宽
        val height: Int     //图片高
        ) : ReaderText()
}
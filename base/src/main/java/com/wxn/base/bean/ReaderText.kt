package com.wxn.base.bean

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
sealed class ReaderText {

    /****
     * 章节
     */
    @Immutable
    data class Chapter(
        val index: String = "",
        var title: String,
        val nested: Boolean
    ) : ReaderText()

    /***
     * 文本内容
     * annotations 对应的文本的样式
     */
    @Immutable
    data class Text(var line: String,
                    var annotations: List<Pair<String, IntRange>> = emptyList<Pair<String, IntRange>>()) : ReaderText()

    /****
     * 分隔符
     */
    @Immutable
    data object Separator : ReaderText()

    @Immutable data class Image(
        val path: String, //绝对路径
        val width: Int,     //图片宽
        val height: Int     //图片高
        ) : ReaderText()
}
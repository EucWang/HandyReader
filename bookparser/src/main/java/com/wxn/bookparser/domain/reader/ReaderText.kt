package com.wxn.bookparser.domain.reader


import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.AnnotatedString
import java.util.UUID

@Immutable
sealed class ReaderText {

    /****
     * 章节
     */
    @Immutable
    data class Chapter(
        val id: UUID = UUID.randomUUID(),
        val title: String,
        val nested: Boolean
    ) : ReaderText()

    /***
     * 文本内容
     */
    @Immutable
    data class Text(val line: AnnotatedString) : ReaderText()

    /****
     * 分隔符
     */
    @Immutable
    data object Separator : ReaderText()


    @Immutable data class ImageSource(
        val path: String,
        ) : ReaderText()

    /***
     * 图像
     */
    @Immutable
    data class Image(
        val imageBitmap: ImageBitmap
    ) : ReaderText()
}
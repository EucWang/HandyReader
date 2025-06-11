package com.wxn.base.bean

import androidx.compose.runtime.Immutable
import com.wxn.base.util.Logger

data class TextTag(
    val uuid:String,                //标签的唯一uuid值
    val anchorId : String = "",     //如果是锚点，则有值
    val name: String,               //标签名
    var start: Int = 0,             //标签影响的开始位置
    var end: Int =0,                //标签影响的结束位置
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
    data class Text(var line: String, var annotations: List<TextTag> = emptyList<TextTag>()): ReaderText() {

        val isText: Boolean
            get(){
                val tagName = annotations.firstOrNull()?.name.orEmpty()
                if (tagName == "h1" || tagName == "h2" || tagName == "h3" || tagName == "h4" || tagName == "h5" || tagName == "h6" || tagName == "h7" || tagName == "img") {
                    return false
                }
                return true
            }

        fun tryParseToChapter(chapterIndex: Int): Chapter? {
            if (annotations.firstOrNull()?.name == "h1") {
                return Chapter(chapterIndex.toString(), title = line.trim(), nested = false)
            }
            return null
        }

        fun tryParseToImage() : Image? {
            if (annotations.firstOrNull()?.name == "img" && !annotations.firstOrNull()?.params.isNullOrEmpty()) {
                val params : String = annotations.first().params
                val paramItems = params.split("&").mapNotNull {
                    val item = it.split("=")
                    if(item.getOrNull(0) != null && item.getOrNull(1) != null) {
                        Pair(item.get(0), item.get(1))
                    } else {
                        null
                    }
                }
                var src = ""
                var width = 0
                var height = 0
                for(item in paramItems)  {
                    when(item.first) {
                        "src" -> {
                            src = item.second
                        }
                        "width" -> {
                            width = item.second.toIntOrNull() ?: 0
                        }
                        "height" -> {
                            height = item.second.toIntOrNull() ?: 0
                        }
                    }
                }
                Logger.d("tryParseToImage:img=$src,width=$width, height=$height")
                if (src.isNotEmpty() && width > 0 && height > 0) {
                    return Image(src.trim(), width, height)
                }
            }
            return null
        }
    }

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
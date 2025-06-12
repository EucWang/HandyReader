package com.wxn.base.bean

import android.R
import androidx.compose.runtime.Immutable
import com.wxn.base.util.Logger

data class TextTag(
    val uuid: String,                //标签的唯一uuid值
    val anchorId: String = "",     //如果是锚点，则有值
    val name: String,               //标签名
    var start: Int = 0,             //标签影响的开始位置
    var end: Int = 0,                //标签影响的结束位置
    val parentUuid: String = "",    //父级标签uuid
    val params: String = ""         //字符串拼接的键值对， 需要解析
) {

    fun paramsPairs(): List<Pair<String, String>> {
        return params.split("&").mapNotNull {
            val item = it.split("=")
            if (item.getOrNull(0) != null && item.getOrNull(1) != null) {
                Pair(item[0], item[1])
            } else {
                null
            }
        }
    }

    fun cssClasses(): List<String> {
        return paramsPairs().filter {
            it.first == "class" && it.second.isNotEmpty()
        }.map { it.second }
    }
}

data class TextCssInfo(
    var fontSize: Double = 1.0,
    var fontFamily: List<String> = emptyList<String>(),
    var fontWeight: CssFontWeight = CssFontWeight.FontWeightNormal,
    var fontStyle: CssFontStyle = CssFontStyle.CssFontStyleNormal,
    var textIndent: Int = 0,
    var fontColor: String = "",
    var textDecoration: CssTextDecoration = CssTextDecoration.CssTextDecorationNone,

    var textAlign: CssTextAlign = CssTextAlign.CssTextAlignLeft,
    var verticalAlign: CssVerticalAlign = CssVerticalAlign.CssVerticalAlignBaseLine,

    var lineHeight: Double = 1.0,
    var background: String = "",
    var isFullScreen: Boolean = false,

    var marginLeft: Double = 0.0,
    var marginRight: Double = 0.0,
    var marginTop: Double = 0.0,
    var marginBottom: Double = 0.0,

    var paddingLeft: Double = 0.0,
    var paddingRight: Double = 0.0,
    var paddingTop: Double = 0.0,
    var paddingBottom: Double = 0.0
) {

}


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
    data class Text(var line: String, var annotations: List<TextTag> = emptyList<TextTag>()) : ReaderText() {

        val isText: Boolean
            get() {
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

        fun tryParseToImage(): Image? {
            if (annotations.firstOrNull()?.name == "img" && !annotations.firstOrNull()?.params.isNullOrEmpty()) {
                val paramItems = annotations.firstOrNull()?.paramsPairs().orEmpty()
                var src = ""
                var width = 0
                var height = 0
                for (item in paramItems) {
                    when (item.first) {
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

        /***
         * 根据TextTag和Css样式表，
         */
        fun parseTextCss(csssheets: Map<String, CssInfo>) {
            var parsedCss = TextCssInfo()
            val cssClasses = arrayListOf<String>()
            annotations.forEach { tag ->
                cssClasses.addAll(tag.cssClasses())
            }
            for (css in cssClasses) {
                val ruleDatas = csssheets[css]?.datas.orEmpty()
                for (ruleData in ruleDatas) {
                    when (ruleData.name) {
                        "font-size" -> {
                            parsedCss.fontSize = parseCell(ruleData.value)
                        }

                        "font-family" -> {
                            val families = arrayListOf<String>()
                            ruleData.value.trim().split(",").forEach { family ->
                                val item = family.trim()
                                if (item != null) {
                                    families.add(item)
                                }
                            }
                            parsedCss.fontFamily = families
                        }

                        "font-weight" -> {
                            parsedCss.fontWeight = CssFontWeight.format(ruleData.value)
                        }

                        "font-style" -> {
                            parsedCss.fontStyle = CssFontStyle.format(ruleData.value)
                        }

                        "text-indent" -> {
                            parsedCss.textIndent = parseCellInt(ruleData.value, 0)
                        }

                        "color" -> {
                            parsedCss.fontColor = ruleData.value
                        }

                        "text-decoration" -> {
                            parsedCss.textDecoration = CssTextDecoration.format(ruleData.value)

                        }

                        "text-align" -> {
                            parsedCss.textAlign = CssTextAlign.format(ruleData.value)
                        }

                        "vertical-align" -> {
                            parsedCss.verticalAlign = CssVerticalAlign.format(ruleData.value)
                        }

                        "line-height" -> {
                            parsedCss.lineHeight = parseCell(ruleData.value)

                        }

                        "background" -> {
                            parsedCss.background = ruleData.value
                        }

                        "qrfullpage" -> {
                            if (ruleData.value == "1") {
                                parsedCss.isFullScreen = true
                            }
                        }

                        "page-break-after" -> {
                            if (ruleData.value == "always") {
                                parsedCss.isFullScreen = true
                            }
                        }

                        "margin-left" -> {
                            //TODO
                        }

                        "margin-right" -> {

                        }

                        "margin-top" -> {

                        }

                        "margin-bottom" -> {

                        }

                        "margin" -> {

                        }
                    }
                }
            }
            //居中或者右对齐是，首行缩进为0
            if (parsedCss.textIndent > 0 && (parsedCss.textAlign == CssTextAlign.CssTextAlignCenter || parsedCss.textAlign == CssTextAlign.CssTextAlignRight)) {
                parsedCss.textIndent = 0
            }

            this.textCssInfo = parsedCss
        }

        companion object {
            fun parseCell(value: String) : Double {
                val cells = arrayListOf<String>("em", "rem")
                for (cell in cells) {
                    if (value.endsWith(cell)) {
                        val value = value.substring(0, value.length - cell.length)
                        return value.toDoubleOrNull() ?: 1.0
                    }
                }
                return 0.0
            }
            fun parseCellInt(value: String, defaultValue: Int) : Int {
                val cells = arrayListOf<String>("em", "rem")
                for (cell in cells) {
                    if (value.endsWith(cell)) {
                        val value = value.substring(0, value.length - cell.length)
                        return value.toIntOrNull() ?: defaultValue
                    }
                }
                return defaultValue
            }
        }

        var textCssInfo = TextCssInfo()


    }

    /****
     * 分隔符
     */
    @Immutable
    data object Separator : ReaderText()

    @Immutable
    data class Image(
        val path: String,   //绝对路径
        val width: Int,     //图片宽
        val height: Int     //图片高
    ) : ReaderText()
}
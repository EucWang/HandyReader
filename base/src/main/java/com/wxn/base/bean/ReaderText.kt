package com.wxn.base.bean

import androidx.compose.runtime.Immutable
import com.wxn.base.unit.CssUnit
import com.wxn.base.unit.CssUnit.Companion.Em
import com.wxn.base.unit.CssUnit.Companion.Px
import com.wxn.base.util.Logger

data class TextTag(
    val uuid: String,                //标签的唯一uuid值
    val anchorId: String = "",     //如果是锚点，则有值
    val name: String,               //标签名               //underline/highlight
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
    var fontSize: CssUnit = Em(1.0f),
    var fontFamily: List<String> = emptyList<String>(),
    var fontWeight: CssFontWeight = CssFontWeight.FontWeightNormal,
    var fontStyle: CssFontStyle = CssFontStyle.CssFontStyleNormal,
    var textIndent: CssUnit = Em(0f),
    var fontColor: String = "",
    var textDecoration: CssTextDecoration = CssTextDecoration.CssTextDecorationNone,

    var textAlign: CssTextAlign = CssTextAlign.CssTextAlignLeft,
    var verticalAlign: CssVerticalAlign = CssVerticalAlign.CssVerticalAlignBaseLine,

    var lineHeight: CssUnit = Em(1f),
    var background: String = "",
    var isFullScreen: Boolean = false,

    var marginLeft: CssUnit = Em(0f),
    var marginRight: CssUnit = Em(0f),
    var marginTop: CssUnit = Em(0f),
    var marginBottom: CssUnit = Em(0f),

    var paddingLeft: CssUnit = Em(0f),
    var paddingRight: CssUnit = Em(0f),
    var paddingTop: CssUnit = Em(0f),
    var paddingBottom: CssUnit = Em(0f),
)

@Immutable
sealed class ReaderText {

    /****
     * 章节
     */
    @Immutable
    data class Chapter(val index: String = "", var title: String, val nested: Boolean) :
        ReaderText()

    /***
     * 文本内容
     * annotations 对应的文本的样式
     */
    @Immutable
    data class Text(var line: String, var annotations: List<TextTag> = emptyList<TextTag>()) :
        ReaderText() {

        val isText: Boolean
            get() {
                var ret = true
                for (tag in annotations) {
                    val tagName = tag.name
                    if (tagName == "h1" || tagName == "h2" || tagName == "h3" || tagName == "h4" || tagName == "h5" || tagName == "h6" || tagName == "h7" || tagName == "img") {
                        ret = false
                        break
                    }
                }
                return true
            }

        fun tryParseToChapter(chapterIndex: Int): Chapter? {
            val titleTag = annotations.firstOrNull { it.name == "h1" }
            if (titleTag != null && line.isNotEmpty()) {
                return Chapter(chapterIndex.toString(), title = line.trim(), nested = false)
            }
            return null
        }

        fun tryParseToImage(): Image? {
            val imgTag = annotations.firstOrNull { it.name == "img" || it.name == "image" }

            if (line.trim().isEmpty() && imgTag != null) {
                val paramItems = imgTag.paramsPairs()
                var src = ""
                var width = 0
                var height = 0
                for (item in paramItems) {
                    when (item.first) {
                        "src" -> {
                            src = item.second
                        }

                        "width" -> {
                            width = ((item.second.toIntOrNull() ?: 0) * 1.5).toInt()
                        }

                        "height" -> {
                            height = ((item.second.toIntOrNull() ?: 0) * 1.5).toInt()
                        }
                    }
                }
                Logger.d("tryParseToImage:img=$src,width=$width, height=$height, css=${textCssInfo}")
                if (src.isNotEmpty()) {
                    val ret = Image(src.trim(), width, height)
                    ret.textCssInfo = textCssInfo
                    return ret
                }
            }
            return null
        }

        /***
         * 根据TextTag和Css样式表，
         */
        fun parseTextCss(csssheets: Map<String, CssInfo>) {
            var parsedCss = TextCssInfo()
            val cssIdentifiers = arrayListOf<String>()
            annotations.forEach { tag ->
                if (tag.start == 0 && tag.end >= line.length -1) {
                    cssIdentifiers.addAll(tag.cssClasses())
                    if (!tag.name.isNullOrEmpty()) {
                        cssIdentifiers.add(tag.name)
                    }
                    if (!tag.anchorId.isNullOrEmpty()) {
                        cssIdentifiers.add(tag.anchorId)
                    }
                }
            }
            for (css in cssIdentifiers) {
                val ruleDatas = csssheets[css]?.datas.orEmpty()
                for (ruleData in ruleDatas) {
                    when (ruleData.name) {
                        "font-size" -> {
                            parsedCss.fontSize = CssUnit.format(ruleData.value.trim())
                        }

                        "font-family" -> {
                            val families = arrayListOf<String>()
                            ruleData.value.trim().split(",").forEach { family ->
                                val item = family.trim()
                                if (item.isNotEmpty()) {
                                    families.add(item)
                                }
                            }
                            parsedCss.fontFamily = families
                        }

                        "font-weight" -> {
                            parsedCss.fontWeight = CssFontWeight.format(ruleData.value.trim())
                        }

                        "font-style" -> {
                            parsedCss.fontStyle = CssFontStyle.format(ruleData.value.trim())
                        }

                        "text-indent" -> {
                            parsedCss.textIndent = CssUnit.format(ruleData.value.trim())
                        }

                        "color" -> {
                            parsedCss.fontColor = ruleData.value.trim()
                        }

                        "text-decoration" -> {
                            parsedCss.textDecoration = CssTextDecoration.format(ruleData.value.trim())
                        }

                        "text-align" -> {
                            parsedCss.textAlign = CssTextAlign.format(ruleData.value.trim())
                        }

                        "vertical-align" -> {
                            parsedCss.verticalAlign = CssVerticalAlign.format(ruleData.value.trim())
                        }

                        "line-height" -> {
                            parsedCss.lineHeight = CssUnit.format(ruleData.value.trim())

                        }

                        "background" -> {
                            parsedCss.background = ruleData.value.trim()
                        }

                        "qrfullpage" -> {
                            if (ruleData.value.trim() == "1") {
                                parsedCss.isFullScreen = true
                            }
                        }

                        "page-break-after" -> {
                            if (ruleData.value.trim() == "always") {
                                parsedCss.isFullScreen = true
                            }
                        }

                        "margin-left" -> {
                            parsedCss.marginLeft = CssUnit.format(ruleData.value)
                        }

                        "margin-right" -> {
                            parsedCss.marginRight = CssUnit.format(ruleData.value)
                        }

                        "margin-top" -> {
                            parsedCss.marginTop = CssUnit.format(ruleData.value)
                        }

                        "margin-bottom" -> {
                            parsedCss.marginBottom = CssUnit.format(ruleData.value)
                        }

                        "margin" -> {
                            val datas =  ruleData.value.trim().split(" ")
                            when (datas.size) {
                                1 -> {
                                    val value = CssUnit.format(datas[0].trim())
                                    parsedCss.marginLeft = value
                                    parsedCss.marginTop = value
                                    parsedCss.marginRight = value
                                    parsedCss.marginBottom = value
                                }

                                2 -> {
                                    val verticalValue = CssUnit.format(datas[0].trim())
                                    val horizontalValue = CssUnit.format(datas[1].trim())
                                    parsedCss.marginLeft = horizontalValue
                                    parsedCss.marginTop = verticalValue
                                    parsedCss.marginRight = horizontalValue
                                    parsedCss.marginBottom = verticalValue
                                }

                                3 -> {
                                    val top = CssUnit.format(datas[0].trim())
                                    val right = CssUnit.format(datas[1].trim())
                                    val bottom = CssUnit.format(datas[2].trim())
                                    val left = right
                                    parsedCss.marginLeft = left
                                    parsedCss.marginTop = top
                                    parsedCss.marginRight = right
                                    parsedCss.marginBottom = bottom
                                }

                                4 -> {
                                    val left = CssUnit.format(datas[0].trim())
                                    val top = CssUnit.format(datas[1].trim())
                                    val right = CssUnit.format(datas[2].trim())
                                    val bottom = CssUnit.format(datas[3].trim())
                                    parsedCss.marginLeft = left
                                    parsedCss.marginTop = top
                                    parsedCss.marginRight = right
                                    parsedCss.marginBottom = bottom
                                }
                            }
                        }


                        "padding-left" -> {
                            parsedCss.paddingLeft = CssUnit.format(ruleData.value)
                        }

                        "padding-right" -> {
                            parsedCss.paddingRight = CssUnit.format(ruleData.value)
                        }

                        "padding-top" -> {
                            parsedCss.paddingTop = CssUnit.format(ruleData.value)
                        }

                        "padding-bottom" -> {
                            parsedCss.paddingBottom = CssUnit.format(ruleData.value)
                        }

                        "padding" -> {
                            val datas = ruleData.value.trim().split(" ")
                            when (datas.size) {
                                1 -> {
                                    val value = CssUnit.format(datas[0].trim())
                                    parsedCss.paddingLeft = value
                                    parsedCss.paddingTop = value
                                    parsedCss.paddingRight = value
                                    parsedCss.paddingBottom = value
                                }

                                2 -> {
                                    val horizontalValue = CssUnit.format(datas[0].trim())
                                    val verticalValue = CssUnit.format(datas[1].trim())
                                    parsedCss.paddingLeft = horizontalValue
                                    parsedCss.paddingTop = verticalValue
                                    parsedCss.paddingRight = horizontalValue
                                    parsedCss.paddingBottom = verticalValue
                                }

                                4 -> {
                                    val left = CssUnit.format(datas[0].trim())
                                    val top = CssUnit.format(datas[1].trim())
                                    val right = CssUnit.format(datas[2].trim())
                                    val bottom = CssUnit.format(datas[3].trim())
                                    parsedCss.paddingLeft = left
                                    parsedCss.paddingTop = top
                                    parsedCss.paddingRight = right
                                    parsedCss.paddingBottom = bottom
                                }
                            }
                        }
                    }
                }
            }
            //居中或者右对齐是，首行缩进为0
            if (parsedCss.textIndent.value > 0 && (parsedCss.textAlign == CssTextAlign.CssTextAlignCenter || parsedCss.textAlign == CssTextAlign.CssTextAlignRight)) {
                parsedCss.textIndent = Em(0f)
            }
            annotations.forEach { tag ->
                if (tag.end - tag.start >= line.length) {
                    when (tag.name) {
                        "i", "em" -> {
                            parsedCss.fontStyle = CssFontStyle.CssFontStyleItalic
                        }

                        "b" -> {
                            parsedCss.fontWeight = CssFontWeight.FontWeightBold
                        }

                        "strong" -> {
                            parsedCss.fontWeight = CssFontWeight.FontWeightBolder
                        }

                        "p" -> {
                            tag.paramsPairs().forEach { kv ->
                                if (kv.first == "align") {
                                    when (kv.second) {
                                        "center" -> {
                                            parsedCss.textAlign = CssTextAlign.CssTextAlignCenter
                                        }

                                        "left" -> {
                                            parsedCss.textAlign = CssTextAlign.CssTextAlignLeft
                                        }

                                        "right" -> {
                                            parsedCss.textAlign = CssTextAlign.CssTextAlignRight
                                        }

                                        "justify" -> {
                                            parsedCss.textAlign = CssTextAlign.CssTextAlignJustify
                                        }
                                    }
                                }
                            }
                        }

                        "font" -> {
                            tag.paramsPairs().forEach { kv ->
                                if (kv.first == "size") {   // <font> 标签中，size 属性 默认使用的是“相对单位”, size="1" 对应的是 12px（默认字体大小）
                                    kv.second.toIntOrNull()?.let { size ->
                                        if (size in 1..10) {
                                            parsedCss.fontSize = Px(size.coerceIn(3, 7) * 12f)
                                        }
                                    }
                                } else if (kv.first == "color") {
                                    if (kv.second.isNotEmpty()) {
                                        parsedCss.fontColor = kv.second
                                    }
                                }
                            }
                        }
                    }
                }

                tag.paramsPairs().forEach { kv ->
                    if (kv.first == "width") {
                        parsedCss
                    }
                }
            }

            this.textCssInfo = parsedCss
        }
    }

    var textCssInfo = TextCssInfo()

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
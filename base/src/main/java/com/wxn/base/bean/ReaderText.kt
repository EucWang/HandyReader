package com.wxn.base.bean

import androidx.compose.runtime.Immutable
import com.wxn.base.unit.CssUnit
import com.wxn.base.unit.CssUnit.Companion.Em
import com.wxn.base.unit.CssUnit.Companion.Px
import com.wxn.base.util.Logger

/**
 * 段落内子区间的字号缩放信息(Phase 1:仅 font-size em 倍数)。
 *
 * - [start]/[end]: 段落内字符 offset(与 [TextTag.start]/[TextTag.end] 语义一致,
 *   start inclusive, end exclusive)
 * - [scale]: 字号倍数(如 1.5f = 1.5em),来自子区间 TextTag 的 `params="font-size=1.5em"`
 *
 * 不可变 data class。Phase 2 可扩展 color/weight 等而无需破坏 TextChar(它继续只承载几何)。
 * inline 字号段落才会出现非空列表。
 */
data class InlineFontSize(
    val start: Int,
    val end: Int,
    val scale: Float
)

data class TextTag(
    val uuid: String,                //标签的唯一uuid值
    val anchorId: String = "",     //如果是锚点，则有值
    val name: String,               //标签名               //underline/highlight
    var start: Int = 0,             //标签影响的开始位置（inclusive）
    var end: Int = 0,               //标签影响的结束位置（exclusive，不包含 end 位置的字符）
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

    var textAlign: CssTextAlign = CssTextAlign.CssTextAlignUndefined,
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

        /**
         * 本段所有 inline font-size 子区间(Phase 1)。
         *
         * - null:尚未解析(parseTextCss 未调用)
         * - empty:已解析但无子区间字号(90%+ 段落)
         * - nonEmpty:含至少一个 InlineFontSize
         *
         * 运行时排版数据,不持久化(项目无序列化框架,ReaderText 不进 DB/Intent,无需 @Transient)。
         * parseTextCss() 仍解析整段 CSS 到 [textCssInfo];本字段在 parseTextCss() 内顺带填充。
         *
         * parseTextCss() 全项目仅在 BookHelper.kt:98 调用一次(已核实),F2 中 parseTextCss 兜底是
         * 双保险(防御单元测试或其他入口跳过 BookHelper 直接进 ChapterProvider 的场景)。
         */
        var inlineFontSizes: List<InlineFontSize>? = null
            internal set

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
        fun parseTextCss() {
            var parsedCss = TextCssInfo()
            val inlineList = ArrayList<InlineFontSize>()   // F1 新增:子区间字号收集

            annotations.forEach { tag ->
                if (tag.start == 0 && tag.end >= line.length - 1) {
                    tag.paramsPairs().forEach { kv ->
                        when (kv.first) {
                            "font-size" -> {
                                parsedCss.fontSize = CssUnit.format(kv.second.trim())
                            }

                            "font-family" -> {
                                val families = arrayListOf<String>()
                                kv.second.trim().split(",").forEach { family ->
                                    val item = family.trim()
                                    if (item.isNotEmpty()) {
                                        families.add(item)
                                    }
                                }
                                parsedCss.fontFamily = families
                            }

                            "font-weight" -> {
                                parsedCss.fontWeight = CssFontWeight.format(kv.second.trim())
                            }

                            "font-style" -> {
                                parsedCss.fontStyle = CssFontStyle.format(kv.second.trim())
                            }

                            "text-indent" -> {
                                parsedCss.textIndent = CssUnit.format(kv.second.trim())
                            }

                            "color" -> {
                                parsedCss.fontColor = kv.second.trim()
                            }

                            "text-decoration" -> {
                                parsedCss.textDecoration = CssTextDecoration.format(kv.second.trim())
                            }

                            "text-align" -> {
                                parsedCss.textAlign = CssTextAlign.format(kv.second.trim())
                            }

                            "vertical-align" -> {
                                parsedCss.verticalAlign = CssVerticalAlign.format(kv.second.trim())
                            }

                            "line-height" -> {
                                parsedCss.lineHeight = CssUnit.format(kv.second.trim())
                            }

                            "qrfullpage" -> {
                                if (kv.second.trim() == "1") {
                                    parsedCss.isFullScreen = true
                                }
                            }

                            "page-break-after" -> {
                                if (kv.second.trim() == "always") {
                                    parsedCss.isFullScreen = true
                                }
                            }

                            "margin-left" -> {
                                parsedCss.marginLeft = CssUnit.format(kv.second)
                            }

                            "margin-right" -> {
                                parsedCss.marginRight = CssUnit.format(kv.second)
                            }

                            "margin-top" -> {
                                parsedCss.marginTop = CssUnit.format(kv.second)
                            }

                            "margin-bottom" -> {
                                parsedCss.marginBottom = CssUnit.format(kv.second)
                            }

                            "margin" -> {
                                val datas = kv.second.trim().split(" ")
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
                                        parsedCss.marginLeft = right
                                        parsedCss.marginTop = top
                                        parsedCss.marginRight = right
                                        parsedCss.marginBottom = bottom
                                    }

                                    4 -> {
                                        val top = CssUnit.format(datas[0].trim())
                                        val right = CssUnit.format(datas[1].trim())
                                        val bottom = CssUnit.format(datas[2].trim())
                                        val left = CssUnit.format(datas[3].trim())
                                        parsedCss.marginLeft = left
                                        parsedCss.marginTop = top
                                        parsedCss.marginRight = right
                                        parsedCss.marginBottom = bottom
                                    }
                                }
                            }

                            "padding-left" -> {
                                parsedCss.paddingLeft = CssUnit.format(kv.second)
                            }

                            "padding-right" -> {
                                parsedCss.paddingRight = CssUnit.format(kv.second)
                            }

                            "padding-top" -> {
                                parsedCss.paddingTop = CssUnit.format(kv.second)
                            }

                            "padding-bottom" -> {
                                parsedCss.paddingBottom = CssUnit.format(kv.second)
                            }

                            "padding" -> {
                                val datas = kv.second.trim().split(" ")
                                when (datas.size) {
                                    1 -> {
                                        val value = CssUnit.format(datas[0].trim())
                                        parsedCss.paddingLeft = value
                                        parsedCss.paddingTop = value
                                        parsedCss.paddingRight = value
                                        parsedCss.paddingBottom = value
                                    }

                                    2 -> {
                                        val verticalValue = CssUnit.format(datas[0].trim())
                                        val horizontalValue = CssUnit.format(datas[1].trim())
                                        parsedCss.paddingLeft = horizontalValue
                                        parsedCss.paddingTop = verticalValue
                                        parsedCss.paddingRight = horizontalValue
                                        parsedCss.paddingBottom = verticalValue
                                    }

                                    3 -> {
                                        val top = CssUnit.format(datas[0].trim())
                                        val right = CssUnit.format(datas[1].trim())
                                        val bottom = CssUnit.format(datas[2].trim())
                                        parsedCss.paddingLeft = right
                                        parsedCss.paddingTop = top
                                        parsedCss.paddingRight = right
                                        parsedCss.paddingBottom = bottom
                                    }

                                    4 -> {
                                        val top = CssUnit.format(datas[0].trim())
                                        val right = CssUnit.format(datas[1].trim())
                                        val bottom = CssUnit.format(datas[2].trim())
                                        val left = CssUnit.format(datas[3].trim())
                                        parsedCss.paddingLeft = left
                                        parsedCss.paddingTop = top
                                        parsedCss.paddingRight = right
                                        parsedCss.paddingBottom = bottom
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ── F1 新增:整段守卫未命中 → 收集子区间 font-size(Phase 1 仅 em) ──
                    // 不排序:按 annotations 原始顺序(DOM 遍历序)收集,F4/F6 用 lastOrNull
                    // 实现"后到覆盖先到"语义,匹配 C++ 深度优先遍历产出顺序
                    // [TEMP-DEBUG v4.0] 排查 capitularR 不放大问题
                    Logger.d("F1-DEBUG: tag.name=${tag.name} start=${tag.start} end=${tag.end} params=${tag.params}")
                    tag.paramsPairs().forEach { kv ->
                        if (kv.first == "font-size") {
                            val cssUnit = CssUnit.format(kv.second.trim())
                            Logger.d("F1-DEBUG: font-size kv=$kv cssUnit=$cssUnit isEm=${cssUnit.isEm()}")
                            if (cssUnit.isEm() && cssUnit.value > 0f) {
                                val scale = cssUnit.value.coerceIn(MIN_INLINE_SCALE, MAX_INLINE_SCALE)
                                inlineList.add(InlineFontSize(tag.start, tag.end, scale))
                                Logger.d("F1-DEBUG: COLLECTED start=${tag.start} end=${tag.end} scale=$scale")
                            }
                            // px 暂不处理(需段落基准 px 才能换算 em);Phase 2 再补
                        }
                    }
                }
            }

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
                                if (kv.first == "size") {
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
            }

            this.textCssInfo = parsedCss
            this.inlineFontSizes = inlineList.ifEmpty { emptyList() }   // F1 新增
            // [TEMP-DEBUG v4.0] 排查 capitularR 不放大问题
            Logger.d("F1-DEBUG: FINAL line.length=${line.length} inlineFontSizes=${this.inlineFontSizes} textCssInfo.fontSize=${parsedCss.fontSize}")
        }

        companion object {
            /** F1:子区间字号倍数 clamp 范围,防御损坏 EPUB(0.5em ~ 5.0em) */
            private const val MIN_INLINE_SCALE = 0.5f
            private const val MAX_INLINE_SCALE = 5.0f
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
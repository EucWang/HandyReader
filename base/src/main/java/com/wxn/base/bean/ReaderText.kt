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
data class InlineStyle(
    val start: Int,
    val end: Int,
    val props: InlineCssProps
) {
    companion object {
        /**
         * 按 offset 查询命中的样式（属性级 lastOrNull）。
         * **算法**：正序遍历全部区间，对每个命中的区间，用其非 null 属性覆盖累积值。
         * 等价于"每个属性各自做 lastOrNull，但一次遍历完成"。
         *
         * **CSS 层叠正确性**：
         * - 同属性嵌套（外层 color=#333, 内层 4~6 color=#fff）→ offset=5 取内层 #fff ✓
         * - 不同属性错位重叠（A 设字号, B 设颜色, 区间重叠）→ 字号取 A、颜色取 B，各自独立 ✓（修复了"整个对象 lastOrNull 导致未设属性被遮蔽"的缺陷）
         * - CSS 继承（外层设 color, 内层未设 color 但设了别的）→ 内层 props.color==null 不覆盖，保留外层 color ✓
         *
         * @param styles inline 样式列表（null/empty → 返回全 null 的默认 props）
         * @param offset 段落内字符 offset
         * @return 命中区间解析后的 [InlineCssProps]（未命中属性为 null）；列表空返回 [InlineCssProps] 默认实例
         */
        fun resolve(styles: List<InlineStyle>?, offset: Int): InlineCssProps {
            if (styles.isNullOrEmpty()) return InlineCssProps()
            var fontScale: Float? = null
            var color: String? = null
            var verticalAlign: CssVerticalAlign? = null
            for (style in styles) {
                if (offset in style.start until style.end) {
                    style.props.fontScale?.let { fontScale = it }
                    style.props.color?.let { color = it }
                    style.props.verticalAlign?.let {
                        verticalAlign = it
                    }
                }
            }
            return InlineCssProps(fontScale, color, verticalAlign)
        }
    }
}

/**
 * 段落内子区间的 inline CSS 属性集合（可扩展）。
 * 每个属性 null = 该区间未显式设置此属性（回退到段落级/用户级，或继承外层区间）。
 * ⚠️ 硬约束：未设置的属性**必须为 null**，不能是空字符串或默认值。
 * 否则会破坏 [InlineStyle.resolve] 的 CSS 继承语义（属性级 lastOrNull 依赖 null 判断"是否覆盖"）。
 */
data class InlineCssProps(
    val fontScale: Float? = null,   // 来自 font-size(em)，已 clamp 到 [0.5, 5.0]
    val color: String? = null,       // 颜色字符串原样保留（#hex / 命名色 / rgb()），渲染期解析
    val verticalAlign: CssVerticalAlign? = null  // super/sub 垂直对齐
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

    var display: String = "", //如果值为block，则需要应用CSS样式的文字颜色大小显示，而不是用户设置的文字颜色大小显示
)

@Immutable
sealed class ReaderText {

    companion object {
        /** 块级容器白名单：基调声明的有效设置点。
         *  内联标签（span/em/a/…）上的 dir 是 isolate 语义（影响片段内部顺序），
         *  不升格为段落基调——按 HTML 规范只认块级祖先与段落自身标签。
         *  "__root__" 为解析层注入的虚拟根标签（html/body 级声明的载体）。
         **/
        private val BLOCK_CONTEXT_TAGS = setOf(
            "__root__",
            "html", "body", "div", "p", "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "dl", "dt", "dd",
            "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
            "blockquote", "pre", "section", "main", "article", "aside", "nav",
            "header", "footer", "figure", "figcaption", "address",
            "details", "summary", "fieldset", "form", "center"
        )
    }

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
         * 本段所有 inline 样式子区间（fontScale / color，可扩展）。
         * - null: 尚未解析（parseTextCss 未调用）
         * - empty: 已解析但无子区间样式（90%+ 段落）
         * - nonEmpty: 含至少一个 InlineStyle
         * 运行时排版数据，不持久化。parseTextCss() 内填充。
         */
        var inlineStyles: List<InlineStyle>? = null
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
            val innerStyleList = ArrayList<InlineStyle>()   // 子区间样式收集

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
                            "display" -> {
                                parsedCss.display = kv.second
                            }
                        }
                    }
                } else {
                    var fontScale : Float? = null
                    var color : String? = null
                    var verticalAlign: CssVerticalAlign? = null
                    tag.paramsPairs().forEach { kv ->
                        when(kv.first) {
                            "font-size" -> {
                                val cssUnit = CssUnit.format(kv.second.trim())
                                if (cssUnit.isEm() && cssUnit.value > 0f) {
                                    fontScale = cssUnit.value.coerceIn(MIN_INLINE_SCALE, MAX_INLINE_SCALE)
                                }
                            }
                            "color" -> {
                                val v = kv.second.trim()
                                if (v.isNotEmpty()) color = v
                            }
                            "vertical-align" -> {
                                verticalAlign = CssVerticalAlign.format(kv.second.trim())
                            }
                        }
                    }
                    if (verticalAlign == null) {
                        verticalAlign = when (tag.name) {
                            "sup" -> CssVerticalAlign.CssVerticalAlignSuper
                            "sub" -> CssVerticalAlign.CssVerticalAlignSub
                            else -> null
                        }
                    }
                    if (fontScale != null || color != null || verticalAlign != null) {
                        innerStyleList.add(InlineStyle(tag.start, tag.end, InlineCssProps(fontScale, color, verticalAlign)))
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
            this.inlineStyles = innerStyleList
        }

        /** 块级祖先链（含自身标签）上最近一次显式方向声明（W3C：声明 > 嗅探）。
         *  三态：true=显式 RTL；false=显式 LTR；null=无声明（首强嗅探）。
         *  规则：annotations 按文档序（浅→深，C++ tags 先序 push + get_fathers_tags 保序输出
         *        + JNI 透传保序，三级保证），倒序遍历即最近声明优先；
         *        同一标签内 内联 style 的 direction > CSS 规则 direction > HTML dir
         *        （CSS 级联：inline > 作者规则 > presentation hint）；
         *        dir="auto" 显式选择嗅探并阻断继续向外层找；未知值（如 HTML4 废弃的 lro/rlo）
         *        视同该标签无声明，继续向外层找。
         *  计算属性（无缓存）：段落级有效方向由 segDirect.baseRtl 承载（disposeContent 融合后），
         *  本属性仅在方向判定入口消费一次，避免双真相源。
         **/
        val declaredBaseRtl: Boolean?
            get() {
                for (tag in annotations.asReversed()) {
                    if (tag.name.lowercase() !in BLOCK_CONTEXT_TAGS) continue
                    val kv = tag.paramsPairs().toMap()
                    // 内联 style（ele_params 原样拼整串 "style=direction:rtl;color:…"，此处解析
                    for (decl in kv["style"].orEmpty().split(";")) {
                        val prop = decl.trim().lowercase()
                        when {
                            prop.startsWith("direction:rtl") -> return true
                            prop.startsWith("direction:ltr") -> return false
                        }
                    }
                    // CSS 规则（apply_css_to_params 以属性名 direction= 合并进 params）
                    when (kv["direction"]?.trim()?.lowercase()) {
                        "rtl" -> return true
                        "ltr" -> return false
                    }
                    // HTML dir 属性
                    when (kv["dir"]?.trim()?.lowercase()) {
                        "rtl" -> return true
                        "ltr" -> return false
                        "auto" -> return null                // 阻断：显式 auto 不再继承外层
                    }
                }
                return null
            }

        companion object {
            /** F1:子区间字号倍数 clamp 范围,防御损坏 EPUB(0.5em ~ 5.0em) */
            private const val MIN_INLINE_SCALE = 0.5f
            private const val MAX_INLINE_SCALE = 5.0f
        }
    }

    var textCssInfo = TextCssInfo()

    var segDirect: SegmentResult? = null

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
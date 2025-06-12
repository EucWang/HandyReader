package com.wxn.base.bean

/**
 * 100 到 900：这些数值代表字体的粗细程度，数值越大，字体越粗。
 * 100：非常细（Hairline）
 * 200：Extra Light（Ultra Light）
 * 300：Light
 * 400：Normal（等同于 normal）
 * 500：Medium
 * 600：Semi Bold（Demi Bold）
 * 700：Bold（等同于 bold）
 * 800：Extra Bold（Ultra Bold）
 * 900：Black（Heavy）
 */
enum class CssFontWeight {
    FontWeightNormal, //默认值，表示字体以正常状态显示，对应数值为400
    FontWeightLighter, //300 表示比当前字体更细的字体，如果继承的字体重量为300，计算出的值将是200，
    FontWeightBold, //示字体加粗，通常对应数值为700
    FontWeightBolder; // 900 表示比当前字体更粗的字体，例如在一个段落中，如果段落的字体已经设置为粗体，而段落内的 <strong> 元素被设置为更粗，则 <strong> 元素将显得更暗。唯一例外是当父元素的权重设置为900时，没有更粗的值存在，因此结果权重保持为900


    override fun toString(): String {
        return when (this) {
            FontWeightNormal -> {
                "normal"
            }

            FontWeightBold -> {
                "bold"
            }

            FontWeightBolder -> {
                "bolder"
            }

            FontWeightLighter -> {
                "lighter"
            }
        }
    }

    companion object {
        fun format(fontWeight: String): CssFontWeight {
            return when (fontWeight) {
                "normal" -> FontWeightNormal
                "bold" -> FontWeightBold
                "bolder" -> FontWeightBolder
                "lighter" -> FontWeightLighter
                else -> FontWeightNormal
            }
        }
    }
}

enum class CssTextAlign {
    CssTextAlignCenter,
    CssTextAlignJustify,
    CssTextAlignLeft,
    CssTextAlignRight;


    override fun toString(): String {
        return when (this) {
            CssTextAlignJustify -> "justify"
            CssTextAlignLeft -> "left"
            CssTextAlignRight -> "right"
            CssTextAlignCenter -> "center"
        }
    }

    companion object {

        fun format(textAlign: String): CssTextAlign {
            return when (textAlign) {
                "left" -> CssTextAlignLeft
                "right" -> CssTextAlignRight
                "justify" -> CssTextAlignJustify
                "center" -> CssTextAlignCenter
                else -> CssTextAlignLeft
            }
        }
    }
}

enum class CssTextDecoration {
    CssTextDecorationNone,
    CssTextDecorationUnderline,
    CssTextDecorationOverline,
    CssTextDecorationLineThrough;


    override fun toString(): String {
        return when (this) {
            CssTextDecorationNone -> "none"
            CssTextDecorationUnderline -> "underline"
            CssTextDecorationOverline -> "overline"
            CssTextDecorationLineThrough -> "line-through"
        }
    }

    companion object {

        fun format(decoration: String): CssTextDecoration {
            return when (decoration) {
                "underline" -> CssTextDecorationUnderline
                "line-through" -> CssTextDecorationLineThrough
                "none" -> CssTextDecorationNone
                "overline" -> CssTextDecorationOverline
                else -> CssTextDecorationNone
            }
        }
    }
}

enum class CssFontStyle {
    CssFontStyleNormal,
    CssFontStyleItalic; //表示文本以斜体显示。这是使用当前字体的斜体字体，适用于有斜体版本的字体;
//    CssFontStyleOblique; //表示将文本倾斜，适用于没有斜体版本的字体。oblique 通常与 italic 效果相似，但 italic 更受支持。

    override fun toString(): String {
        return when (this) {
            CssFontStyleNormal -> "normal"
            CssFontStyleItalic -> "italic"
        }
    }

    companion object {

        fun format(fontStyle: String): CssFontStyle {
            return when (fontStyle) {
                "normal" -> CssFontStyleNormal
                "oblique", "italic" -> CssFontStyleItalic
//            "oblique" -> CssFontStyleOblique
                else -> CssFontStyleNormal
            }
        }
    }
}

/**
 * vertical-align属性用于控制行内元素或表格单元格内容的垂直对齐方式。
 */
enum class CssVerticalAlign {
    CssVerticalAlignBaseLine,   //默认值，表示元素与基线对齐。
    CssVerticalAlignTop,            //将元素的顶部与当前行框的顶部对齐。
    CssVerticalAlignMiddle,         //将元素的垂直中心与当前行框的中间对齐。常用于实现垂直居中效果。
    CssVerticalAlignBottom,         //将元素的底部与当前行框的底部对齐
    CssVerticalAlignTextTop,        //将元素的顶部与文本的顶部对齐，适用于文本内容。
    CssVerticalAlignTextBottom,     //将元素的底部与文本的底部对齐，适用于文本内容。
    CssVerticalAlignSub,            //将元素对齐到下标位置，适用于数学公式中的下标。
    CssVerticalAlignSuper;          //将元素对齐到上标位置，适用于数学公式中的上标。


    override fun toString(): String {
        return when (this) {
            CssVerticalAlignBaseLine -> ""
            CssVerticalAlignTop -> "top"
            CssVerticalAlignMiddle -> "middle"
            CssVerticalAlignBottom -> "bottom"
            CssVerticalAlignTextTop -> "text-top"
            CssVerticalAlignTextBottom -> "text-bottom"
            CssVerticalAlignSub -> "sub"
            CssVerticalAlignSuper -> "super"
        }
    }

    companion object {

        fun format(verticalAlign: String): CssVerticalAlign {
            return when (verticalAlign) {
                "", "baseline" -> CssVerticalAlignBaseLine
                "top" -> CssVerticalAlignTop
                "middle" -> CssVerticalAlignMiddle
                "bottom" -> CssVerticalAlignBottom
                "text-top" -> CssVerticalAlignTextTop
                "text-bottom" -> CssVerticalAlignTextBottom
                "sub" -> CssVerticalAlignSub
                "super" -> CssVerticalAlignSuper
                else -> CssVerticalAlignBaseLine
            }
        }
    }
}

enum class CssItem(val css: String) {
    CSS_WIDTH("width"),                 // 100%,
    CSS_HEIGHT("height"),               // 100%

    CSS_FONT_SIZE("font-size"),             //*** 2.5em;            em 是一个相对单位，1em 等于当前元素的 font-size。
    CSS_FONT_FAMILY("font-family"),         //*** "MYing Hei S", Hei;
    CSS_FONT_WEIGHT("font-weight"),         //*** normal, bold
    CSS_FONT_STYLE("font-style"),           //***   italic
    CSS_TEXT_INDENT("text-indent"),         //*** 0em  text-indent: 0em; 表示文本块中第一行文本的缩进为 0，即不进行缩进。
    CSS_COLOR("color"),                     //*** #916B23
    CSS_TEXT_DECORATION("text-decoration"),     //*** underline[下划线], line-through[删除线]， none, overline：表示在文本上方添加一条线。这种装饰效果通常用于强调文本的顶部

    CSS_TEXT_ALIGN("text-align"),       //*** center[居中], justify[左右对齐], left[左对齐]， right[右对齐]
    CSS_VERTICAL_ALIGN("vertical-align"),   //*** super[数字上标], sub[数字下标], middle[垂直居中]

    CSS_MARGIN("margin"),               //auto auto -0.5em auto; auto 表示浏览器会自动计算左右边距，使得元素在水平方向上居中。-0.5em 表示一个具体的负值，用于调整元素的上下边距。负值表示边距会向内收缩，从而减少元素与周围内容之间的间距。
    CSS_MARGIN_TOP("margin-top"),       //2em
    CSS_MARGIN_BOTTOM("margin-bottom"), //0em
    CSS_MARGIN_LEFT("margin-left"),     //0em
    CSS_MARGIN_RIGHT("margin-right"),   //0em

    CSS_PADDING("padding"),             //0.25em
    CSS_PADDING_TOP("padding-top"),     //35%
    CSS_PADDING_BOTTOM("padding-bottom"),
    CSS_PADDING_LEFT("padding-left"),
    CSS_PADDING_RIGHT("padding-right"),

    CSS_LINE_HEIGHT("line-height"),     //*** 1.618em 行高（line height）的值为当前元素字体大小的 1.618 倍， line-height 的值是当前元素 font-size 的乘数。

    CSS_BORDER("border"),                   //1px solid #916B23; 边框线
    CSS_BORDER_RADIUS("border-radius"),     //0.25em    边框圆角
    CSS_BACKGROUND("background"),           //#D2D2D2   背景颜色

    CSS_QR_FULL_PAGE("qrfullpage"),         //全屏图   取值 1 表示全屏模式，0 表示非全屏模式
    CSS_PAGE_BREAK_AFTER("page-break-after"),   //分页 取值 always 是 page-break-after 的一个值，表示强制在该元素之后插入一个页面分页

    CSS_UNKNOWN("none")
}
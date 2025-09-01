package com.wxn.base.bean

import com.wxn.base.bean.CssItemConstant.CSS_BACKGROUND_NAME
import com.wxn.base.bean.CssItemConstant.CSS_BORDER_NAME
import com.wxn.base.bean.CssItemConstant.CSS_BORDER_RADIUS_NAME
import com.wxn.base.bean.CssItemConstant.CSS_COLOR_NAME
import com.wxn.base.bean.CssItemConstant.CSS_FONT_FAMILY_NAME
import com.wxn.base.bean.CssItemConstant.CSS_FONT_SIZE_NAME
import com.wxn.base.bean.CssItemConstant.CSS_FONT_STYLE_NAME
import com.wxn.base.bean.CssItemConstant.CSS_FONT_WEIGHT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_HEIGHT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_LINE_HEIGHT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_MARGIN_BOTTOM_NAME
import com.wxn.base.bean.CssItemConstant.CSS_MARGIN_LEFT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_MARGIN_NAME
import com.wxn.base.bean.CssItemConstant.CSS_MARGIN_RIGHT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_MARGIN_TOP_NAME
import com.wxn.base.bean.CssItemConstant.CSS_PADDING_BOTTOM_NAME
import com.wxn.base.bean.CssItemConstant.CSS_PADDING_LEFT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_PADDING_NAME
import com.wxn.base.bean.CssItemConstant.CSS_PADDING_RIGHT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_PADDING_TOP_NAME
import com.wxn.base.bean.CssItemConstant.CSS_PAGE_BREAK_AFTER_NAME
import com.wxn.base.bean.CssItemConstant.CSS_QR_FULL_PAGE_NAME
import com.wxn.base.bean.CssItemConstant.CSS_TEXT_ALIGN_NAME
import com.wxn.base.bean.CssItemConstant.CSS_TEXT_DECORATION_NAME
import com.wxn.base.bean.CssItemConstant.CSS_TEXT_INDENT_NAME
import com.wxn.base.bean.CssItemConstant.CSS_UNKNOWN_NAME
import com.wxn.base.bean.CssItemConstant.CSS_VERTICAL_ALIGN_NAME
import com.wxn.base.bean.CssItemConstant.CSS_WIDTH_NAME

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
    CSS_WIDTH(CSS_WIDTH_NAME),                 // 100%,
    CSS_HEIGHT(CSS_HEIGHT_NAME),               // 100%

    CSS_FONT_SIZE(CSS_FONT_SIZE_NAME),             //*** 2.5em;            em 是一个相对单位，1em 等于当前元素的 font-size。
    CSS_FONT_FAMILY(CSS_FONT_FAMILY_NAME),         //*** "MYing Hei S", Hei;
    CSS_FONT_WEIGHT(CSS_FONT_WEIGHT_NAME),         //*** normal, bold
    CSS_FONT_STYLE(CSS_FONT_STYLE_NAME),           //***   italic
    CSS_TEXT_INDENT(CSS_TEXT_INDENT_NAME),         //*** 0em  text-indent: 0em; 表示文本块中第一行文本的缩进为 0，即不进行缩进。
    CSS_COLOR(CSS_COLOR_NAME),                     //*** #916B23
    CSS_TEXT_DECORATION(CSS_TEXT_DECORATION_NAME),     //*** underline[下划线], line-through[删除线]， none, overline：表示在文本上方添加一条线。这种装饰效果通常用于强调文本的顶部

    CSS_TEXT_ALIGN(CSS_TEXT_ALIGN_NAME),       //*** center[居中], justify[左右对齐], left[左对齐]， right[右对齐]
    CSS_VERTICAL_ALIGN(CSS_VERTICAL_ALIGN_NAME),   //*** super[数字上标], sub[数字下标], middle[垂直居中]

    CSS_MARGIN(CSS_MARGIN_NAME),               //auto auto -0.5em auto; auto 表示浏览器会自动计算左右边距，使得元素在水平方向上居中。-0.5em 表示一个具体的负值，用于调整元素的上下边距。负值表示边距会向内收缩，从而减少元素与周围内容之间的间距。
    CSS_MARGIN_TOP(CSS_MARGIN_TOP_NAME),       //2em
    CSS_MARGIN_BOTTOM(CSS_MARGIN_BOTTOM_NAME), //0em
    CSS_MARGIN_LEFT(CSS_MARGIN_LEFT_NAME),     //0em
    CSS_MARGIN_RIGHT(CSS_MARGIN_RIGHT_NAME),   //0em

    CSS_PADDING(CSS_PADDING_NAME),             //0.25em
    CSS_PADDING_TOP(CSS_PADDING_TOP_NAME),     //35%
    CSS_PADDING_BOTTOM(CSS_PADDING_BOTTOM_NAME),
    CSS_PADDING_LEFT(CSS_PADDING_LEFT_NAME),
    CSS_PADDING_RIGHT(CSS_PADDING_RIGHT_NAME),

    CSS_LINE_HEIGHT(CSS_LINE_HEIGHT_NAME),     //*** 1.618em 行高（line height）的值为当前元素字体大小的 1.618 倍， line-height 的值是当前元素 font-size 的乘数。

    CSS_BORDER(CSS_BORDER_NAME),                   //1px solid #916B23; 边框线
    CSS_BORDER_RADIUS(CSS_BORDER_RADIUS_NAME),     //0.25em    边框圆角
    CSS_BACKGROUND(CSS_BACKGROUND_NAME),           //#D2D2D2   背景颜色

    CSS_QR_FULL_PAGE(CSS_QR_FULL_PAGE_NAME),         //全屏图   取值 1 表示全屏模式，0 表示非全屏模式
    CSS_PAGE_BREAK_AFTER(CSS_PAGE_BREAK_AFTER_NAME),   //分页 取值 always 是 page-break-after 的一个值，表示强制在该元素之后插入一个页面分页

    CSS_UNKNOWN(CSS_UNKNOWN_NAME);

}
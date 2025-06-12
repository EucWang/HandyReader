package com.wxn.base.bean

data class CssInfo(
    val identifier: String,
    val weight: Int,
    val isBaseSelector: Boolean,
    val datas: List<RuleData>
)

data class RuleData(
    val name: String,
    val value: String
)

fun RuleData.format(): CSS_ITEM =
    when (name) {
        "width" -> CSS_ITEM.CSS_WIDTH
        "height" -> CSS_ITEM.CSS_HEIGHT
        "font-size" -> CSS_ITEM.CSS_FONT_SIZE
        "font-family" -> CSS_ITEM.CSS_FONT_FAMILY
        "font-weight" -> CSS_ITEM.CSS_FONT_WEIGHT
        "font-style" -> CSS_ITEM.CSS_FONT_STYLE
        "text-indent" -> CSS_ITEM.CSS_TEXT_INDENT
        "color" -> CSS_ITEM.CSS_COLOR
        "text-decoration" -> CSS_ITEM.CSS_TEXT_DECORATION

        "text-align" -> CSS_ITEM.CSS_TEXT_ALIGN
        "vertical-align" -> CSS_ITEM.CSS_VERTICAL_ALIGN

        "margin" -> CSS_ITEM.CSS_MARGIN
        "margin-top" -> CSS_ITEM.CSS_MARGIN_TOP
        "margin-bottom" -> CSS_ITEM.CSS_MARGIN_BOTTOM
        "margin-left" -> CSS_ITEM.CSS_MARGIN_LEFT
        "margin-right" -> CSS_ITEM.CSS_MARGIN_RIGHT

        "padding" -> CSS_ITEM.CSS_PADDING
        "padding-top" -> CSS_ITEM.CSS_PADDING_TOP
        "padding-bottom" -> CSS_ITEM.CSS_PADDING_BOTTOM
        "padding-left" -> CSS_ITEM.CSS_PADDING_LEFT
        "padding-right" -> CSS_ITEM.CSS_PADDING_RIGHT

        "line-height" -> CSS_ITEM.CSS_LINE_HEIGHT

        "border" -> CSS_ITEM.CSS_BORDER
        "border-radius" -> CSS_ITEM.CSS_BORDER_RADIUS
        "background" -> CSS_ITEM.CSS_BACKGROUND

        "qrfullpage" -> CSS_ITEM.CSS_QR_FULL_PAGE
        "page-break-after" -> CSS_ITEM.CSS_PAGE_BREAK_AFTER
        else -> CSS_ITEM.CSS_UNKNOWN
    }

enum class CSS_ITEM(val css: String) {
    CSS_WIDTH("width"),                 // 100%,
    CSS_HEIGHT("height"),               // 100%

    CSS_FONT_SIZE("font-size"),             //*** 2.5em;            em 是一个相对单位，1em 等于当前元素的 font-size。
    CSS_FONT_FAMILY("font-family"),         //*** "MYing Hei S", Hei;
    CSS_FONT_WEIGHT("font-weight"),         //*** normal, bold
    CSS_FONT_STYLE("font-style"),           //***   italic
    CSS_TEXT_INDENT("text-indent"),         //*** 0em  text-indent: 0em; 表示文本块中第一行文本的缩进为 0，即不进行缩进。
    CSS_COLOR("color"),                     //*** #916B23
    CSS_TEXT_DECORATION("text-decoration"),     //*** underline[下划线], line-through[删除线]

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
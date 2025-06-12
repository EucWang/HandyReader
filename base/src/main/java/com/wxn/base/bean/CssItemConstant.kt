package com.wxn.base.bean

object CssItemConstant {

    const val CSS_WIDTH_NAME = "width"                 // 100%,
    const val CSS_HEIGHT_NAME = "height"               // 100%

    const val CSS_FONT_SIZE_NAME = "font-size"             //*** 2.5em;            em 是一个相对单位，1em 等于当前元素的 font-size。
    const val CSS_FONT_FAMILY_NAME = "font-family"         //*** "MYing Hei S", Hei;
    const val CSS_FONT_WEIGHT_NAME = "font-weight"         //*** normal, bold
    const val CSS_FONT_STYLE_NAME = "font-style"           //***   italic
    const val CSS_TEXT_INDENT_NAME = "text-indent"         //*** 0em  text-indent: 0em; 表示文本块中第一行文本的缩进为 0，即不进行缩进。
    const val CSS_COLOR_NAME = "color"                     //*** #916B23
    const val CSS_TEXT_DECORATION_NAME = "text-decoration"     //*** underline[下划线], line-through[删除线]， none, overline：表示在文本上方添加一条线。这种装饰效果通常用于强调文本的顶部

    const val CSS_TEXT_ALIGN_NAME = "text-align"       //*** center[居中], justify[左右对齐], left[左对齐]， right[右对齐]
    const val CSS_VERTICAL_ALIGN_NAME = "vertical-align"   //*** super[数字上标], sub[数字下标], middle[垂直居中]

    const val CSS_MARGIN_NAME = "margin"               //auto auto -0.5em auto; auto 表示浏览器会自动计算左右边距，使得元素在水平方向上居中。-0.5em 表示一个具体的负值，用于调整元素的上下边距。负值表示边距会向内收缩，从而减少元素与周围内容之间的间距。
    const val CSS_MARGIN_TOP_NAME = "margin-top"       //2em
    const val CSS_MARGIN_BOTTOM_NAME = "margin-bottom" //0em
    const val CSS_MARGIN_LEFT_NAME = "margin-left"     //0em
    const val CSS_MARGIN_RIGHT_NAME = "margin-right"   //0em

    const val CSS_PADDING_NAME = "padding"             //0.25em
    const val CSS_PADDING_TOP_NAME = "padding-top"     //35%
    const val CSS_PADDING_BOTTOM_NAME = "padding-bottom"
    const val CSS_PADDING_LEFT_NAME = "padding-left"
    const val CSS_PADDING_RIGHT_NAME = "padding-right"

    const val CSS_LINE_HEIGHT_NAME = "line-height"     //*** 1.618em 行高（line height）的值为当前元素字体大小的 1.618 倍， line-height 的值是当前元素 font-size 的乘数。

    const val CSS_BORDER_NAME = "border"                   //1px solid #916B23; 边框线
    const val CSS_BORDER_RADIUS_NAME = "border-radius"     //0.25em    边框圆角
    const val CSS_BACKGROUND_NAME = "background"          //#D2D2D2   背景颜色

    const val CSS_QR_FULL_PAGE_NAME = "qrfullpage"         //全屏图   取值 1 表示全屏模式，0 表示非全屏模式
    const val CSS_PAGE_BREAK_AFTER_NAME = "page-break-after"   //分页 取值 always 是 page-break-after 的一个值，表示强制在该元素之后插入一个页面分页

    const val CSS_UNKNOWN_NAME = "unknown"

}
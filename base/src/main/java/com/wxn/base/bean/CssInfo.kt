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


fun RuleData.format(): CssItem =
    when (name) {
        "width" -> CssItem.CSS_WIDTH
        "height" -> CssItem.CSS_HEIGHT
        "font-size" -> CssItem.CSS_FONT_SIZE
        "font-family" -> CssItem.CSS_FONT_FAMILY
        "font-weight" -> CssItem.CSS_FONT_WEIGHT
        "font-style" -> CssItem.CSS_FONT_STYLE
        "text-indent" -> CssItem.CSS_TEXT_INDENT
        "color" -> CssItem.CSS_COLOR
        "text-decoration" -> CssItem.CSS_TEXT_DECORATION

        "text-align" -> CssItem.CSS_TEXT_ALIGN
        "vertical-align" -> CssItem.CSS_VERTICAL_ALIGN

        "margin" -> CssItem.CSS_MARGIN
        "margin-top" -> CssItem.CSS_MARGIN_TOP
        "margin-bottom" -> CssItem.CSS_MARGIN_BOTTOM
        "margin-left" -> CssItem.CSS_MARGIN_LEFT
        "margin-right" -> CssItem.CSS_MARGIN_RIGHT

        "padding" -> CssItem.CSS_PADDING
        "padding-top" -> CssItem.CSS_PADDING_TOP
        "padding-bottom" -> CssItem.CSS_PADDING_BOTTOM
        "padding-left" -> CssItem.CSS_PADDING_LEFT
        "padding-right" -> CssItem.CSS_PADDING_RIGHT

        "line-height" -> CssItem.CSS_LINE_HEIGHT

        "border" -> CssItem.CSS_BORDER
        "border-radius" -> CssItem.CSS_BORDER_RADIUS
        "background" -> CssItem.CSS_BACKGROUND

        "qrfullpage" -> CssItem.CSS_QR_FULL_PAGE
        "page-break-after" -> CssItem.CSS_PAGE_BREAK_AFTER
        else -> CssItem.CSS_UNKNOWN
    }

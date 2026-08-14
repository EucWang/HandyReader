package com.wxn.bookread.provider

import com.wxn.bookread.provider.ChapterProvider.columnGapActual
import com.wxn.bookread.provider.ChapterProvider.columnWidth
import com.wxn.bookread.provider.ChapterProvider.paddingHorizontal
import com.wxn.bookread.provider.ChapterProvider.visibleRight
import com.wxn.bookread.provider.ChapterProvider.visibleWidth

/** 全页几何（单列模式）。 */
fun layoutBoundsPage(): LayoutBounds = LayoutBounds(
    width = visibleWidth,
    startX = paddingHorizontal,
    endX = visibleRight,
    role = ColumnRole.FULL
)

/** 双列左列几何。 */
fun layoutBoundsLeftColumn(): LayoutBounds = LayoutBounds(
    width = columnWidth,
    startX = paddingHorizontal,
    endX = paddingHorizontal + columnWidth,
    role = ColumnRole.LEFT
)

/** 双列右列几何。 */
fun layoutBoundsRightColumn(): LayoutBounds = LayoutBounds(
    width = columnWidth,
    startX = paddingHorizontal + columnWidth + columnGapActual,
    endX = paddingHorizontal + 2 * columnWidth + columnGapActual,
    role = ColumnRole.RIGHT
)

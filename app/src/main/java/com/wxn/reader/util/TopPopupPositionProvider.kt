package com.wxn.reader.util

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

class TopPopupPositionProvider(
    val alignment: Alignment,
    val offset: IntOffset,
    val anchor: IntOffset
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {

        return if (anchor.y - (popupContentSize.height) - offset.y > 0) { //top of popup
            IntOffset(
                offset.x,
                anchor.y - (popupContentSize.height) - offset.y
            )
        } else { // bottom of popup
            IntOffset(
                offset.x,
                anchor.y + offset.y
            )
        }

//        val anchorAlignmentPoint = alignment.align(IntSize.Zero, anchorBounds.size, layoutDirection)
//
//        // Note the negative sign. Popup alignment point contributes negative offset.
//        val popupAlignmentPoint = -alignment.align(IntSize.Zero, popupContentSize, layoutDirection)
//
//        val resolvedUserOffset = IntOffset(
//            offset.x * (if (layoutDirection == LayoutDirection.Ltr) 1 else -1),
//            offset.y
//        )
//
//        return anchorBounds.topLeft +
//                anchorAlignmentPoint +
//                popupAlignmentPoint +
//                resolvedUserOffset
    }
}
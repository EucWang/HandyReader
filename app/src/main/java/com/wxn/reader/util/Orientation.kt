package com.wxn.reader.util


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment

@Stable
class Orientation(
    val orientation: Int,
    val horizontalAlignment: Alignment.Horizontal,
    val horizontalArrangement: Arrangement.Horizontal,
    val verticalAlignment: Alignment.Vertical,
    val verticalArrangement: Arrangement.Vertical
) {

    companion object {
        const val VERTICAL = 0
        const val HORIZONTAL = 1
    }
}

@Composable
fun rememberOrientationHorizontal(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top
) = remember {
    Orientation(Orientation.HORIZONTAL, horizontalAlignment, horizontalArrangement, verticalAlignment, verticalArrangement)
}

@Composable
fun rememberOrientationVertical(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top
) = remember {
    Orientation(Orientation.VERTICAL, horizontalAlignment, horizontalArrangement, verticalAlignment, verticalArrangement)
}
package com.wxn.reader.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale

/****
 * The default supported data types are:
 * String (mapped to a Uri)
 * Uri ("android.resource", "content", "file", "http", and "https" schemes only)
 * HttpUrl
 * File
 * DrawableRes
 * Drawable
 * Bitmap
 * ByteArray
 * ByteBuffer
 */
@Composable
fun ImagePanel(
    modifier: Modifier = Modifier,
    data: Any?,
    size: Int = 300,
    clipToBounds: Boolean = true,
    colorFilter: ColorFilter? = null,
    alpha: Float = DefaultAlpha,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val request =
        ImageRequest
            .Builder(context)
            .data(data)
            .size(size)
            .scale(
                if (contentScale == ContentScale.Crop) Scale.FILL else Scale.FIT
            ).build()
    AsyncImage(
        model = request,
        contentDescription = "",
        modifier = modifier,
        alignment = alignment,
        alpha = alpha,
        colorFilter = colorFilter,
        clipToBounds = clipToBounds,
        contentScale = contentScale
    )
}
package com.wxn.reader.presentation.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.wxn.reader.R

/** ★ P1-5:orphan 书 source 标识(与 BookEntity/HomeViewModel.SOURCE_SYNC_ORPHAN 保持一致)。 */
private const val SOURCE_SYNC_ORPHAN = "sync_orphan"

@Composable
fun BookCover(
    coverImage: String?,
    title: String,
    author: String,
    isAudiobook: Boolean,
    modifier: Modifier = Modifier,
    bookSource: String? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    showText: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    // coverImage 变化时重置 isError，避免"换封面后永远显示 placeholder"
    var isError by remember(coverImage) { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center) {
        if (coverImage.isNullOrEmpty() || isError) {
            BookCoverPlaceholder(
                title = title,
                author = author,
                isAudiobook = isAudiobook,
                modifier = Modifier.fillMaxSize().clip(shape).align(Alignment.Center),
                shape = shape,
                showText = showText,
            )
        } else {
            val request = remember(coverImage) {
                ImageRequest.Builder(context)
                    .data(coverImage)
                    .size(300)
                    .scale(Scale.FILL)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = title,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = contentScale,
                onError = { isError = true },
            )
        }

        // ★ P1-5:orphan 书(无本地文件,需用户重新关联)右下角显示断链角标
        if (bookSource == SOURCE_SYNC_ORPHAN) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.LinkOff,
                    contentDescription = stringResource(R.string.cd_orphan_needs_file),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

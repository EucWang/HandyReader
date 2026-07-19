package com.wxn.reader.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SetListItem(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    text: String,
    icon: ImageVector,
    elevationOverlay: Color = if (isDarkTheme) {
        Color.White.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    },
    itemClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (!isDarkTheme) {
                    Color.Black.copy(alpha = 0.8f)
                } else {
                    Color.Black.copy(alpha = 0.5f)
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .background(elevationOverlay)
            .clickable(onClick = {
                itemClick?.invoke()
            })
            .fillMaxWidth(),
        headlineContent = {
            Text(
                text = text, //stringResource(R.string.general),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon, //Icons.Outlined.Tune,
                contentDescription = "General",
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )
    )
}

@Composable
fun SetListItem(text: String) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .fillMaxWidth(),
        headlineContent = {
             Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )
    )
}
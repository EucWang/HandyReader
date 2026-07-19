package com.wxn.reader.util

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxn.base.ext.ColorA
import com.wxn.reader.R
import com.wxn.reader.ui.theme.stringResource


@Composable
fun LoadingPanel(text: String = stringResource(R.string.loading), showIndicator: Boolean = true) {
    Box(
        modifier = Modifier.fillMaxSize()
            .hoverable(rememberMutableInteractionSource())
            .focusable(true, rememberMutableInteractionSource())
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.height(82.dp)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(4.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                WidthSpace(20.dp)
                if (showIndicator) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color(0xFFF19F15),
                        trackColor = ColorA(0xF19F15, 0.1f),
                        strokeWidth = 3.dp,
                    )
                    WidthSpace(15.dp)
                }

                Text(
                    text,
                    color = ColorA(0x999999),
                    fontSize = 16.sp
                )
            }
        }
    }
}
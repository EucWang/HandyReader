package com.wxn.reader.presentation.bookReader.components

import android.graphics.Rect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.ext.sendToClip
import com.wxn.base.util.toLocale
import com.wxn.reader.R
import com.wxn.reader.data.remote.dto.SupportedLanguage
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.OnFirstLaunch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatePanel(
    viewModel: MainReadViewModel,
    rect: Rect,
    selectedText: String,
    targetLang: String,
    translatedText: String?,
    supportedLanguages: List<SupportedLanguage>,
    onTargetLangChange: (String) -> Unit,
    onTranslate: () -> Unit
) {
    val translateStatus by viewModel.translateStatus.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidthDp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    val panelWidth = 280.dp
    val panelWidthPx = with(density) { panelWidth.toPx() }
    val verticalPadding = with(density) { 24.dp.toPx() }

    val panelHeightEstimate = with(density) { 190.dp.toPx() }
    val offsetX = calculateOffsetX(rect, screenWidthPx, panelWidthPx)
    val isNearTop = rect.top < panelHeightEstimate
    val targetOffsetY = if (isNearTop) {
        minOf(rect.bottom + verticalPadding, screenHeightPx - panelHeightEstimate)
    } else {
        maxOf(rect.top - panelHeightEstimate - verticalPadding, 0f)
    }
    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "translatePanelOffsetY"
    )

    var targetExpanded by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(0) { 2 }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = Modifier.offset {
            IntOffset(offsetX.toInt(), animatedOffsetY.toInt())
        }
    ) {
        Card(
            modifier = Modifier
                .width(panelWidth)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    HorizontalPager(pagerState,
                        verticalAlignment = Alignment.Top,
                        ){ index ->
                        when(index) {
                            0 -> {
                                Box(modifier = Modifier.fillMaxSize().heightIn(min = 120.dp)) {
                                    Box(modifier = Modifier.fillMaxSize().padding(8.dp).align(Alignment.TopStart)) {
                                        Text(
                                            text = selectedText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxSize().align(Alignment.TopStart)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            context.sendToClip(selectedText)
                                        },
                                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.alpha(0.75f),
                                            contentDescription = "")
                                    }
                                }
                            }
                            1 -> {
                                Box(modifier = Modifier.fillMaxSize().heightIn(min = 120.dp)) {
                                    Box(modifier = Modifier.fillMaxSize().padding(8.dp).align(Alignment.TopStart)) {
                                        Text(
                                            text = translatedText.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxSize().align(Alignment.TopStart)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!translatedText.isNullOrEmpty()) {
                                                context.sendToClip(translatedText)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.alpha(0.75f),
                                            contentDescription = "")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = targetExpanded,
                        onExpandedChange = {
                            if (supportedLanguages.isNotEmpty()) {
                                targetExpanded = !targetExpanded
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                stringResource(R.string.target_language),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier
                                    .clickable{
                                        targetExpanded = true
                                    }
                                    .height(36.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(0.dp)
                            ) {
                                Text(
                                    targetLang.toLocale()?.displayName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded)
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = targetExpanded,
                            onDismissRequest = { targetExpanded = false }
                        ) {
                            supportedLanguages.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            lang.code.toLocale()?.displayName.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    onClick = {
                                        onTargetLangChange(lang.code)
                                        targetExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (translateStatus != MainReadViewModel.TranslateStatus.TRANSLATING) {
                                onTranslate.invoke()
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(0.dp),
                        enabled = translateStatus != MainReadViewModel.TranslateStatus.TRANSLATING && targetLang.isNotBlank()
                    ) {
                        if (translateStatus == MainReadViewModel.TranslateStatus.TRANSLATING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "translate"
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(translateStatus) {
        //翻译成功，跳转页面
        if (translateStatus == MainReadViewModel.TranslateStatus.TRANSLATED) {
            scope.launch {
                pagerState.animateScrollToPage(1)
            }
        }
    }

    OnFirstLaunch {
        if (targetLang.isNotBlank()) {
            onTranslate()
        }
    }
}

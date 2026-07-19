package com.wxn.reader.presentation.bookReader.components

import android.graphics.Rect
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.BrandingWatermark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.wxn.base.ext.sendToClip
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.toLocale
import com.wxn.reader.R
import com.wxn.reader.data.model.WordResult
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.OnFirstLaunch

private val DICTIONARY_LANGUAGES = listOf("en", "zh", "fr", "de", "ru", "es", "pt", "ja", "ar", "hi")

private val SOURCE_LABELS = mapOf(
    "ecdict" to "ECDICT",
    "free" to "Free Dictionary",
    "wiktionary" to "Wiktionary",
    "xinhua" to "新华字典",
    "chinese" to "网络词典",
    "dbnary" to "DBnary",
    "awn" to "Arabic WordNet",
    "siwar" to "Siwar",
    "kaikki" to "Kaikki",
)

private enum class AudioPlaybackState { IDLE, LOADING, PLAYING, ERROR }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DictionaryPanel(
    viewModel: MainReadViewModel,
    rect: Rect,
    onDismiss: () -> Unit
) {
    val dictionaryResult by viewModel.dictionaryResult.collectAsStateWithLifecycle()
    val dictionaryStatus by viewModel.dictionaryStatus.collectAsStateWithLifecycle()
    val dictionaryLang by viewModel.dictionaryLang.collectAsStateWithLifecycle()
    val dictionaryWord by viewModel.dictionaryWord.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidthDp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    val panelWidth = 280.dp
    val panelWidthPx = with(density) { panelWidth.toPx() }
    val verticalPadding = with(density) { 24.dp.toPx() }
    val panelHeightEstimate = with(density) { 320.dp.toPx() }

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
        label = "dictionaryPanelOffsetY"
    )

    var langExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var playbackState by remember { mutableStateOf(AudioPlaybackState.IDLE) }
    var volumeToggle by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(playbackState) {
        if (playbackState == AudioPlaybackState.PLAYING) {
            while (true) {
                delay(300)
                volumeToggle = (volumeToggle + 1) % 3
            }
        }
    }

    LaunchedEffect(dictionaryWord) {
        mediaPlayer?.release()
        mediaPlayer = null
        playbackState = AudioPlaybackState.IDLE
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

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
                WordNavigationRow(
                    word = dictionaryWord,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    onGoBack = { viewModel.goBack() },
                    onGoForward = { viewModel.goForward() }
                )

                val onPlayAudio: (String) -> Unit = { url ->
                    when (playbackState) {
                        AudioPlaybackState.LOADING -> { }
                        AudioPlaybackState.PLAYING -> {
                            mediaPlayer?.release()
                            mediaPlayer = null
                            playbackState = AudioPlaybackState.IDLE
                        }
                        AudioPlaybackState.IDLE,
                        AudioPlaybackState.ERROR -> {
                            playbackState = AudioPlaybackState.LOADING
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(context, android.net.Uri.parse(url))
                                setAudioStreamType(AudioManager.STREAM_MUSIC)
                                setOnPreparedListener {
                                    start()
                                    playbackState = AudioPlaybackState.PLAYING
                                }
                                setOnCompletionListener {
                                    playbackState = AudioPlaybackState.IDLE
                                }
                                setOnErrorListener { _, _, _ ->
                                    playbackState = AudioPlaybackState.ERROR
                                    true
                                }
                                prepareAsync()
                            }
                        }
                    }
                }

                WordInfoRow(
                    result = dictionaryResult,
                    playbackState = playbackState,
                    volumeToggle = volumeToggle,
                    onPlayAudio = onPlayAudio,
                    onCopy = {
                        context.sendToClip(dictionaryWord)
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                LanguageSelectorRow(
                    currentLang = dictionaryLang,
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                    onLangSelected = { lang ->
                        langExpanded = false
                        viewModel.onDictionaryLangChange(lang)
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    when (dictionaryStatus) {
                        MainReadViewModel.DictionaryStatus.LOADING -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        MainReadViewModel.DictionaryStatus.SUCCESS -> {
                            dictionaryResult?.let { result ->
                                ResultContent(
                                    result = result,
                                    onLookupAnother = { viewModel.lookupAnotherWord(it) },
                                    onCopy = { context.sendToClip(it) }
                                )
                            }
                        }

                        MainReadViewModel.DictionaryStatus.NOT_FOUND -> {
                            NotFoundContent(
                                word = dictionaryWord,
                                onSearchGoogle = {
                                    try {
                                        val searchUrl = "https://www.google.com/search?q=define:$dictionaryWord"
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(searchUrl)
                                        )
                                        val chooser = android.content.Intent.createChooser(
                                            intent,
                                            context.getString(R.string.search_in_google)
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(chooser)
                                    } catch (_: SecurityException) {
                                        ToastUtil.show(R.string.action_launch_failed)
                                    }
                                }
                            )
                        }

                        MainReadViewModel.DictionaryStatus.ERROR -> {
                            ErrorContent(
                                onRetry = { viewModel.retryDictionaryLookup() }
                            )
                        }

                        MainReadViewModel.DictionaryStatus.IDLE -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    OnFirstLaunch {
        viewModel.lookupWord()
    }
}

@Composable
private fun WordNavigationRow(
    word: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(32.dp)) {
            if (canGoBack) {
                IconButton(
                    onClick = onGoBack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.dict_navigate_back),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text(
            text = word,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        Box(modifier = Modifier.width(32.dp)) {
            if (canGoForward) {
                IconButton(
                    onClick = onGoForward,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.dict_navigate_forward),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun WordInfoRow(
    result: WordResult?,
    playbackState: AudioPlaybackState,
    volumeToggle: Int,
    onPlayAudio: (String) -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        result?.phonetic?.let { phonetic ->
            Text(
                text = phonetic,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val audioUrl = result?.phonetics?.firstOrNull { it.hasAudio }?.audio
        if (audioUrl != null) {
            IconButton(
                onClick = { onPlayAudio(audioUrl) },
                modifier = Modifier.size(28.dp),
                enabled = playbackState != AudioPlaybackState.LOADING
            ) {
                when (playbackState) {
                    AudioPlaybackState.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AudioPlaybackState.PLAYING -> {
                        Icon(
                            imageVector = when (volumeToggle) {
                                0 -> Icons.Default.VolumeUp
                                1 -> Icons.Default.VolumeDown
                                else -> Icons.Default.VolumeMute
                            },
                            contentDescription = stringResource(R.string.dict_play_pronunciation),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    AudioPlaybackState.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.dict_retry),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    AudioPlaybackState.IDLE -> {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.dict_play_pronunciation),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp).alpha(0.75f),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectorRow(
    currentLang: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLangSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                stringResource(R.string.dict_language),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .clickable { onExpandedChange(true) }
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .padding(0.dp)
            ) {
                Text(
                    currentLang.toLocale()?.displayName ?: currentLang,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DICTIONARY_LANGUAGES.forEach { langCode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            langCode.toLocale()?.displayName ?: langCode,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = { onLangSelected(langCode) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultContent(
    result: WordResult,
    onLookupAnother: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val grouped = result.definitions.groupBy { it.partOfSpeech }

    grouped.forEach { (pos, defs) ->
        Spacer(modifier = Modifier.height(4.dp))

        if (pos.isNotBlank() && pos != "unknown") {
            Text(
                text = pos,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        defs.forEach { def ->
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = def.definition,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            def.example?.let { example ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            def.note?.let { note ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            if (def.synonyms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                WordChips(
                    words = def.synonyms,
                    onClick = onLookupAnother
                )
            }
        }
    }

    if (result.synonyms.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dict_synonyms),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        WordChips(
            words = result.synonyms,
            onClick = onLookupAnother
        )
    }

    if (result.antonyms.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dict_antonyms),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        WordChips(
            words = result.antonyms,
            onClick = onLookupAnother
        )
    }

    if (result.lang == "ar") {
        result.lemma?.let { lemma ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dict_lemma),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = lemma,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        result.root?.let { root ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dict_root),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = root,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        result.source?.let { source ->
            val label = SOURCE_LABELS[source] ?: source
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(
    words: List<String>,
    onClick: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        words.forEach { word ->
            AssistChip(
                onClick = { onClick(word) },
                label = {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
private fun NotFoundContent(
    word: String,
    onSearchGoogle: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.dict_not_found, word),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSearchGoogle) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.search_in_google))
            }
        }
    }
}

@Composable
private fun ErrorContent(
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.dict_error_service),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.dict_retry))
            }
        }
    }
}

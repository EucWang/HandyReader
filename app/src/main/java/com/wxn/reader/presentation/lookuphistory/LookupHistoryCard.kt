package com.wxn.reader.presentation.lookuphistory

import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle.Companion.Italic
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.data.model.DictDefinition
import kotlinx.coroutines.delay

private enum class AudioPlaybackState { IDLE, LOADING, PLAYING, ERROR }

@Composable
fun LookupHistoryCardComposable(
    card: LookupHistoryCard,
    modifier: Modifier = Modifier,
    onRetryDefinition: (LookupHistoryCard) -> Unit = {}
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackState by remember { mutableStateOf(AudioPlaybackState.IDLE) }
    var volumeToggle by remember { mutableIntStateOf(0) }

    LaunchedEffect(playbackState) {
        if (playbackState == AudioPlaybackState.PLAYING) {
            while (true) {
                delay(300)
                volumeToggle = (volumeToggle + 1) % 3
            }
        }
    }

    LaunchedEffect(card.word) {
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

    val audioUrl = card.wordResult?.phonetics?.firstOrNull { it.hasAudio }?.audio

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

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.word,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            if (!card.wordResult?.phonetic.isNullOrBlank() || !audioUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!card.wordResult?.phonetic.isNullOrBlank()) {
                        Text(
                            text = card.wordResult!!.phonetic!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!audioUrl.isNullOrBlank()) {
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
                                        contentDescription = stringResource(R.string.cd_play_pronunciation),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                AudioPlaybackState.ERROR -> {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.cd_retry),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                AudioPlaybackState.IDLE -> {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = stringResource(R.string.cd_play_pronunciation),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            if (card.wordResult != null && card.wordResult.definitions.isNotEmpty()) {
                card.wordResult.definitions.forEach { definition ->
                    DefinitionItem(definition)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            else if (card.isLoadingDefinition) {
                // 加载中：显示 loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (card.fetchFailed) {
                // 查询失败：显示重试按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "\u2014",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onRetryDefinition.invoke(card) }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.dict_retry))
                    }
                }
            }
            else {
                Text(
                    text = "\u2014",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val synonyms = card.wordResult?.synonyms.orEmpty()
            if (synonyms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WordGroupLabel(stringResource(R.string.synonyms), synonyms)
            }

            val antonyms = card.wordResult?.antonyms.orEmpty()
            if (antonyms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WordGroupLabel(stringResource(R.string.antonyms), antonyms)
            }

            if (card.positions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.book_positions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                card.positions.forEach { position ->
                    PositionItem(
                        position = position,
                        word = card.word
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DefinitionItem(definition: DictDefinition) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = definition.partOfSpeech,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = definition.definition,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (!definition.example.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\u201C${definition.example}\u201D",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
private fun WordGroupLabel(label: String, words: List<String>) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            words.forEach { word ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionItem(
    position: LookupHistoryPosition,
    word: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = position.bookTitle,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (position.sentenceText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildHighlightedSentence(position.sentenceText, word),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun buildHighlightedSentence(sentence: String, word: String): androidx.compose.ui.text.AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        val lowerSentence = sentence.lowercase()
        val lowerWord = word.lowercase()
        var startIndex = 0
        while (startIndex < lowerSentence.length) {
            val index = lowerSentence.indexOf(lowerWord, startIndex)
            if (index == -1) {
                append(sentence.substring(startIndex))
                break
            }
            append(sentence.substring(startIndex, index))
            withStyle(SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold
            )) {
                append(sentence.substring(index, index + word.length))
            }
            startIndex = index + word.length
        }
    }
}

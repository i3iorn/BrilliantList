package dev.schrammel.brilliantlist.feature_entry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Renders the media of an entry: an image preview or an audio player. */
@Composable
fun EntryMedia(entry: Entry, loader: MediaLoader, modifier: Modifier = Modifier) {
    when (entry.type) {
        EntryType.IMAGE -> ImagePreview(entry, loader, modifier)
        EntryType.AUDIO -> AudioPlayer(entry, loader, modifier)
        EntryType.TEXT -> Unit
    }
}

@Composable
private fun ImagePreview(entry: Entry, loader: MediaLoader, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()

    val bitmap by produceState<ImageBitmap?>(initialValue = null, entry.id) {
        value = loader.thumbnail(entry, targetWidthPx)?.asImageBitmap()
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.entry_image_description),
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
        )
    } else {
        // The photo is decoded in the background. The placeholder keeps the
        // card from jumping when the image appears.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(PLACEHOLDER_HEIGHT.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

/** The height of the placeholder while a photo is decoded. */
private val PLACEHOLDER_HEIGHT = 200

@Composable
private fun AudioPlayer(entry: Entry, loader: MediaLoader, modifier: Modifier = Modifier) {
    var isPlaying by remember(entry.id) { mutableStateOf(false) }
    var positionMs by remember(entry.id) { mutableIntStateOf(0) }
    var durationMs by remember(entry.id) { mutableIntStateOf(0) }
    var isScrubbing by remember(entry.id) { mutableStateOf(false) }
    var scrubMs by remember(entry.id) { mutableFloatStateOf(0f) }
    val player = remember { EncryptedAudioPlayer() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { player.stop() } }

    // The length of a raw PCM recording is known from the size of the file, so
    // it can be shown before the clip is played.
    val knownDuration by produceState(0, entry.id) {
        value = loader.audioDurationMs(entry)
    }
    if (knownDuration > 0) durationMs = knownDuration

    // Poll the player while it runs so the progress bar reflects the position.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPositionMs()
            val duration = player.durationMs()
            if (duration > 0) durationMs = duration
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    val range = durationMs.coerceAtLeast(1).toFloat()
    val shownPosition = if (isScrubbing) scrubMs.toInt() else positionMs
    val positionText = stringResource(
        R.string.entry_audio_position,
        formatDuration(shownPosition),
        formatDuration(durationMs)
    )

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        player.stop()
                        isPlaying = false
                        positionMs = 0
                    } else {
                        scope.launch {
                            val source = loader.audioSource(entry) ?: return@launch
                            player.play(
                                source = source,
                                onStarted = { isPlaying = true },
                                onCompleted = {
                                    isPlaying = false
                                    positionMs = 0
                                }
                            )
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
                    ),
                    contentDescription = stringResource(
                        if (isPlaying) R.string.entry_stop_audio else R.string.entry_play_audio
                    )
                )
            }
            Text(
                text = stringResource(R.string.entry_audio_label),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = positionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (durationMs > 0) {
            // The slider can be dragged to jump to another position. The
            // position is announced as a time, not as a percentage, so the
            // manual range information is left to the slider.
            Slider(
                value = if (isScrubbing) scrubMs else positionMs.toFloat().coerceIn(0f, range),
                onValueChange = {
                    isScrubbing = true
                    scrubMs = it
                },
                onValueChangeFinished = {
                    isScrubbing = false
                    player.seekTo(scrubMs.toInt())
                    positionMs = scrubMs.toInt()
                },
                valueRange = 0f..range,
                modifier = Modifier.semantics {
                    contentDescription = positionText
                }
            )
        }
    }
}

/** Formats a duration as minutes:seconds. */
internal fun formatDuration(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    return formatSeconds(totalSeconds)
}

private const val PROGRESS_INTERVAL_MS = 200L

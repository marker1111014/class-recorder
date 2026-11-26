package com.classrecorder.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classrecorder.app.audio.PlaybackUiState
import com.classrecorder.app.domain.FilenameHelper

@Composable
fun AudioPlayerBar(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = playbackState.activeRecordingId != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playbackState.title.ifBlank { "課堂錄音" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FilenameHelper.formatDuration(playbackState.positionMs)} / ${FilenameHelper.formatDuration(playbackState.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onCycleSpeed) {
                        val speedStr = if (playbackState.speed % 1.0f == 0f) {
                            "${playbackState.speed.toInt()}.0x"
                        } else {
                            "${playbackState.speed}x"
                        }
                        Text(speedStr, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "暫停" else "播放"
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "關閉播放器",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                val duration = playbackState.durationMs.coerceAtLeast(1L).toFloat()
                val progress = (playbackState.positionMs.coerceIn(0L, playbackState.durationMs) / duration)
                Slider(
                    value = progress,
                    onValueChange = { frac -> onSeek((frac * duration).toLong()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}

@Composable
fun LiveAudioMeter(
    amplitude: Float,
    isPaused: Boolean,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val barFactors = listOf(0.35f, 0.65f, 0.9f, 1.0f, 0.8f, 0.55f, 0.95f, 0.7f, 0.45f)
    Row(
        modifier = modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        barFactors.forEachIndexed { idx, factor ->
            val target = if (isPaused) {
                0.15f
            } else {
                (0.18f + amplitude * factor * (if (idx % 2 == 0) 1.0f else 0.78f)).coerceIn(0.15f, 1f)
            }
            val animated by animateFloatAsState(targetValue = target, label = "ampBar$idx")
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((26 * animated).dp)
                    .clip(CircleShape)
                    .background(barColor.copy(alpha = if (isPaused) 0.35f else 0.85f))
            )
        }
    }
}

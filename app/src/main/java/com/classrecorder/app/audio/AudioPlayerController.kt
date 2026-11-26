package com.classrecorder.app.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackUiState(
    val activeRecordingId: Long? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val errorMessage: String? = null
)

class AudioPlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun togglePlay(recordingId: Long, filePath: String, title: String) {
        val current = _state.value
        if (current.activeRecordingId == recordingId && mediaPlayer != null) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }
        startNew(recordingId, filePath, title)
    }

    private fun startNew(recordingId: Long, filePath: String, title: String) {
        stop()
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            _state.update { it.copy(errorMessage = "找不到錄音檔案") }
            return
        }

        val currentSpeed = _state.value.speed
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                if (currentSpeed != 1.0f) {
                    runCatching {
                        playbackParams = PlaybackParams().setSpeed(currentSpeed)
                    }
                }
                setOnCompletionListener {
                    progressJob?.cancel()
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            positionMs = it.durationMs
                        )
                    }
                }
                start()
            }
            mediaPlayer = player
            _state.value = PlaybackUiState(
                activeRecordingId = recordingId,
                title = title,
                isPlaying = true,
                positionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(0L),
                speed = currentSpeed,
                errorMessage = null
            )
            startProgressPolling()
        } catch (e: Exception) {
            stop()
            _state.update { it.copy(errorMessage = "無法播放音檔：${e.localizedMessage ?: "格式錯誤"}") }
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) player.pause()
        }
        progressJob?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        val player = mediaPlayer ?: return
        runCatching {
            if (_state.value.positionMs >= _state.value.durationMs && _state.value.durationMs > 0L) {
                player.seekTo(0)
            }
            player.start()
        }
        _state.update { it.copy(isPlaying = true) }
        startProgressPolling()
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        runCatching { player.seekTo(clamped.toInt()) }
        _state.update { it.copy(positionMs = clamped) }
    }

    fun cycleSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
        val current = _state.value.speed
        val next = speeds[(speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f } + 1) % speeds.size]
        _state.update { it.copy(speed = next) }
        val player = mediaPlayer ?: return
        runCatching {
            val wasPlaying = player.isPlaying
            player.playbackParams = PlaybackParams().setSpeed(next)
            if (!wasPlaying) {
                player.pause()
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        mediaPlayer = null
        _state.update {
            PlaybackUiState(speed = it.speed)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val player = mediaPlayer ?: break
                val playing = runCatching { player.isPlaying }.getOrDefault(false)
                val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                _state.update { it.copy(positionMs = pos, isPlaying = playing) }
                if (!playing) break
                delay(200L)
            }
        }
    }
}

package com.classrecorder.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.classrecorder.app.data.prefs.AudioQualityPreset
import java.io.File

class AudioRecorderEngine(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isPaused: Boolean = false

    val isRecordingActive: Boolean
        get() = mediaRecorder != null

    fun start(outputFile: File, quality: AudioQualityPreset): Result<File> {
        stopSilently()
        return try {
            outputFile.parentFile?.mkdirs()
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(quality.sampleRate)
                setAudioEncodingBitRate(quality.bitRate)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            currentFile = outputFile
            isPaused = false
            Result.success(outputFile)
        } catch (e: Exception) {
            stopSilently()
            if (outputFile.exists() && outputFile.length() == 0L) {
                runCatching { outputFile.delete() }
            }
            Result.failure(e)
        }
    }

    fun pause(): Boolean {
        val recorder = mediaRecorder ?: return false
        return try {
            if (!isPaused) {
                recorder.pause()
                isPaused = true
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resume(): Boolean {
        val recorder = mediaRecorder ?: return false
        return try {
            if (isPaused) {
                recorder.resume()
                isPaused = false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sampleNormalizedAmplitude(): Float {
        val recorder = mediaRecorder ?: return 0f
        if (isPaused) return 0f
        return try {
            val raw = recorder.maxAmplitude // 0..32767
            (raw / 32767f).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    fun stop(): File? {
        val recorder = mediaRecorder
        val file = currentFile
        mediaRecorder = null
        currentFile = null
        isPaused = false
        if (recorder != null) {
            runCatching { recorder.stop() }
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        return file
    }

    private fun stopSilently() {
        val recorder = mediaRecorder
        mediaRecorder = null
        currentFile = null
        isPaused = false
        if (recorder != null) {
            runCatching { recorder.stop() }
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
    }
}

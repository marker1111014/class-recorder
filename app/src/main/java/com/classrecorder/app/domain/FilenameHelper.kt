package com.classrecorder.app.domain

import java.io.File
import java.util.Locale

object FilenameHelper {

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        val hour = clamped / 60
        val minute = clamped % 60
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    fun formatTimeCompact(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        val hour = clamped / 60
        val minute = clamped % 60
        return String.format(Locale.US, "%02d%02d", hour, minute)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        return when {
            safeBytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safeBytes / (1024.0 * 1024.0))
            safeBytes >= 1024L -> String.format(Locale.US, "%.0f KB", safeBytes / 1024.0)
            else -> "$safeBytes B"
        }
    }

    fun dayOfWeekLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "週一"
        2 -> "週二"
        3 -> "週三"
        4 -> "週四"
        5 -> "週五"
        6 -> "週六"
        7 -> "週日"
        else -> ""
    }

    fun dayOfWeekShortLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> ""
    }

    fun sanitizeFilenamePart(input: String, maxLength: Int = 36): String {
        val cleaned = input
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.')
        return cleaned.take(maxLength).trim('_', '.')
    }

    fun buildScheduledFilename(
        dateIso: String,
        periodOrder: Int,
        startMinute: Int,
        endMinute: Int,
        courseOrPeriodName: String
    ): String {
        val periodTag = String.format(Locale.US, "P%02d", periodOrder.coerceAtLeast(1))
        val timeTag = "${formatTimeCompact(startMinute)}-${formatTimeCompact(endMinute)}"
        val safeTitle = sanitizeFilenamePart(courseOrPeriodName).ifEmpty { periodTag }
        return "${dateIso}_${periodTag}_${timeTag}_${safeTitle}.m4a"
    }

    fun buildManualFilename(
        dateIso: String,
        timeCompactWithSec: String,
        label: String
    ): String {
        val safeTitle = sanitizeFilenamePart(label).ifEmpty { "手動錄音" }
        return "${dateIso}_Manual_${timeCompactWithSec}_${safeTitle}.m4a"
    }

    fun resolveUniqueFile(directory: File, desiredFilename: String): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val base = desiredFilename.removeSuffix(".m4a")
        var candidate = File(directory, desiredFilename)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "${base}_${suffix}.m4a")
            suffix++
        }
        return candidate
    }
}

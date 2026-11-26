package com.classrecorder.app.domain

import com.classrecorder.app.data.db.DailySummaryEntity
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.data.prefs.UserSettings
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AiExportFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun buildDailyMarkdownReport(
        dateIso: String,
        dayOfWeek: Int,
        recordings: List<RecordingEntity>,
        dailySummary: DailySummaryEntity?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String = buildString {
        val dayLabel = FilenameHelper.dayOfWeekLabel(dayOfWeek)
        appendLine("# $dateIso（$dayLabel）課堂錄音與每日總結")
        appendLine()
        if (dailySummary != null && dailySummary.summaryText.isNotBlank()) {
            if (dailySummary.title.isNotBlank()) {
                appendLine("## 今日主題：${dailySummary.title}")
            } else {
                appendLine("## 今日總結")
            }
            appendLine(dailySummary.summaryText.trim())
            appendLine()
        } else {
            appendLine("## 今日總結")
            appendLine("_（尚未填寫今日總結，可將錄音匯出給 AI 整理後貼回此處）_")
            appendLine()
        }

        appendLine("## 今日時間軸與各節課紀錄（共 ${recordings.size} 段）")
        if (recordings.isEmpty()) {
            appendLine("尚無錄音紀錄。")
        } else {
            recordings.sortedBy { it.actualStartEpochMs }.forEachIndexed { idx, rec ->
                val title = rec.courseName.ifBlank { rec.periodName }
                val timeRange = formatRecordingTimeRange(rec, zoneId)
                val duration = FilenameHelper.formatDuration(rec.durationMs)
                appendLine("### ${idx + 1}. $timeRange｜$title（${rec.periodName}）")
                appendLine("- 錄音時長：$duration｜檔案：`${rec.fileName}`")
                if (rec.classNote.isNotBlank()) {
                    appendLine("- 重點筆記：${rec.classNote.trim()}")
                }
                if (rec.homeworkNote.isNotBlank()) {
                    appendLine("- 作業／提醒：${rec.homeworkNote.trim()}")
                }
                if (rec.aiTranscriptOrSummary.isNotBlank()) {
                    appendLine("- 單節摘要：${rec.aiTranscriptOrSummary.trim()}")
                }
                appendLine()
            }
        }
    }

    fun buildAiPromptForDay(
        dateIso: String,
        dayOfWeek: Int,
        recordings: List<RecordingEntity>,
        dailySummary: DailySummaryEntity?,
        promptTemplate: String = UserSettings.DEFAULT_AI_PROMPT_TEMPLATE,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String = buildString {
        val dayLabel = FilenameHelper.dayOfWeekLabel(dayOfWeek)
        appendLine(promptTemplate.trim())
        appendLine()
        appendLine("---")
        appendLine("【日期】$dateIso（$dayLabel）")
        appendLine("【今日錄音時間軸與課程資訊】")
        if (recordings.isEmpty()) {
            appendLine("（本日目前無錄音檔案）")
        } else {
            recordings.sortedBy { it.actualStartEpochMs }.forEachIndexed { idx, rec ->
                val title = rec.courseName.ifBlank { rec.periodName }
                val timeRange = formatRecordingTimeRange(rec, zoneId)
                val duration = FilenameHelper.formatDuration(rec.durationMs)
                append("${idx + 1}. $timeRange｜$title（${rec.periodName}，時長 $duration）")
                append("｜檔名：${rec.fileName}")
                appendLine()
                if (rec.classNote.isNotBlank()) {
                    appendLine("   - 我記的筆記：${rec.classNote.trim()}")
                }
                if (rec.homeworkNote.isNotBlank()) {
                    appendLine("   - 交代事項：${rec.homeworkNote.trim()}")
                }
            }
        }
        if (dailySummary != null && dailySummary.summaryText.isNotBlank()) {
            appendLine()
            appendLine("【我目前的初步備註】")
            appendLine(dailySummary.summaryText.trim())
        }
    }

    fun formatRecordingTimeRange(
        rec: RecordingEntity,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (rec.scheduledStartMinute != null && rec.scheduledEndMinute != null) {
            return "${FilenameHelper.formatMinuteOfDay(rec.scheduledStartMinute)}~${FilenameHelper.formatMinuteOfDay(rec.scheduledEndMinute)}"
        }
        val startStr = Instant.ofEpochMilli(rec.actualStartEpochMs).atZone(zoneId).format(timeFormatter)
        val endStr = rec.actualEndEpochMs?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).format(timeFormatter)
        } ?: "進行中"
        return "$startStr~$endStr"
    }

    fun createDailyZipBundle(
        outputZipFile: File,
        dateIso: String,
        dayOfWeek: Int,
        recordings: List<RecordingEntity>,
        dailySummary: DailySummaryEntity?,
        promptTemplate: String
    ): File {
        outputZipFile.parentFile?.mkdirs()
        val promptText = buildAiPromptForDay(dateIso, dayOfWeek, recordings, dailySummary, promptTemplate)
        val markdownReport = buildDailyMarkdownReport(dateIso, dayOfWeek, recordings, dailySummary)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
            zos.putNextEntry(ZipEntry("00_AI_總結提示詞_Prompt.txt"))
            zos.write(promptText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("00_今日筆記與時間軸.md"))
            zos.write(markdownReport.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val seenNames = mutableSetOf<String>()
            recordings.forEach { rec ->
                val audioFile = File(rec.filePath)
                if (audioFile.exists() && audioFile.length() > 0L) {
                    var entryName = rec.fileName.ifBlank { audioFile.name }
                    while (!seenNames.add(entryName)) {
                        entryName = "_" + entryName
                    }
                    zos.putNextEntry(ZipEntry(entryName))
                    BufferedInputStream(FileInputStream(audioFile)).use { bis ->
                        bis.copyTo(zos, bufferSize = 16 * 1024)
                    }
                    zos.closeEntry()
                }
            }
        }
        return outputZipFile
    }
}

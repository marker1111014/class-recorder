package com.classrecorder.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.classrecorder.app.data.db.AppDatabase
import com.classrecorder.app.data.db.DailySummaryEntity
import com.classrecorder.app.data.db.PeriodDefinitionEntity
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.data.db.RecordingStatus
import com.classrecorder.app.data.db.SkippedSlotEntity
import com.classrecorder.app.data.db.TimetableSlotEntity
import com.classrecorder.app.data.prefs.AppPreferences
import com.classrecorder.app.data.prefs.AudioQualityPreset
import com.classrecorder.app.data.prefs.UserSettings
import com.classrecorder.app.domain.AiExportFormatter
import com.classrecorder.app.domain.FilenameHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.time.DayOfWeek
import java.time.LocalDate

class ClassRecorderRepository(
    private val context: Context,
    private val db: AppDatabase,
    val preferences: AppPreferences
) {
    private val periodDao = db.periodDao()
    private val timetableDao = db.timetableDao()
    private val skippedSlotDao = db.skippedSlotDao()
    private val recordingDao = db.recordingDao()
    private val dailySummaryDao = db.dailySummaryDao()

    val periodsFlow: Flow<List<PeriodDefinitionEntity>> = periodDao.observeAll()
    val timetableFlow: Flow<List<TimetableSlotEntity>> = timetableDao.observeAll()
    val skippedKeysFlow: Flow<Set<String>> = skippedSlotDao.observeAll()
        .map { list -> list.map { it.key }.toSet() }
    val recordingsFlow: Flow<List<RecordingEntity>> = recordingDao.observeAll()
    val dailySummariesFlow: Flow<List<DailySummaryEntity>> = dailySummaryDao.observeAll()
    val settingsFlow: Flow<UserSettings> = preferences.settingsFlow
    val totalStorageBytesFlow: Flow<Long> = recordingDao.observeTotalSizeBytes().map { it ?: 0L }

    fun observeRecordingsForDate(dateIso: String): Flow<List<RecordingEntity>> =
        recordingDao.observeByDate(dateIso)

    fun observeDailySummary(dateIso: String): Flow<DailySummaryEntity?> =
        dailySummaryDao.observeByDate(dateIso)

    suspend fun getPeriods(): List<PeriodDefinitionEntity> = periodDao.getAll()

    suspend fun getTimetableSlots(): List<TimetableSlotEntity> = timetableDao.getAll()

    suspend fun getSkippedKeysFrom(dateIso: String): Set<String> =
        skippedSlotDao.getFromDate(dateIso).map { it.key }.toSet()

    suspend fun getRecordingsForDate(dateIso: String): List<RecordingEntity> =
        recordingDao.getByDate(dateIso)

    suspend fun getDailySummary(dateIso: String): DailySummaryEntity? =
        dailySummaryDao.getByDate(dateIso)

    suspend fun getRecordingById(id: Long): RecordingEntity? = recordingDao.getById(id)

    suspend fun upsertPeriod(period: PeriodDefinitionEntity): Long {
        return if (period.id == 0L) {
            periodDao.insert(period)
        } else {
            periodDao.update(period)
            period.id
        }
    }

    suspend fun deletePeriod(period: PeriodDefinitionEntity) {
        periodDao.delete(period)
    }

    suspend fun applyPeriodPreset(periods: List<PeriodDefinitionEntity>) {
        val existingSlots = timetableDao.getAll()
        val existingPeriods = periodDao.getAll()
        val orderToCourse = mutableMapOf<Pair<Int, Int>, TimetableSlotEntity>()
        val periodIdToOrder = existingPeriods.associate { it.id to it.orderIndex }
        existingSlots.forEach { slot ->
            val order = periodIdToOrder[slot.periodId]
            if (order != null) {
                orderToCourse[slot.dayOfWeek to order] = slot
            }
        }
        periodDao.deleteAll()
        periods.forEach { p ->
            val newId = periodDao.insert(p)
            for (day in 1..7) {
                val old = orderToCourse[day to p.orderIndex]
                if (old != null) {
                    timetableDao.upsert(
                        old.copy(id = 0L, periodId = newId)
                    )
                }
            }
        }
    }

    suspend fun upsertTimetableSlot(
        dayOfWeek: Int,
        periodId: Long,
        enabled: Boolean,
        courseName: String,
        classroom: String = "",
        colorTag: Int = 0
    ) {
        val existing = timetableDao.getSlot(dayOfWeek, periodId)
        if (!enabled && courseName.isBlank() && classroom.isBlank()) {
            timetableDao.deleteSlot(dayOfWeek, periodId)
        } else {
            timetableDao.upsert(
                TimetableSlotEntity(
                    id = existing?.id ?: 0L,
                    dayOfWeek = dayOfWeek,
                    periodId = periodId,
                    enabled = enabled,
                    courseName = courseName.trim(),
                    classroom = classroom.trim(),
                    colorTag = colorTag
                )
            )
        }
    }

    suspend fun applySampleMorning34Schedule() {
        val periods = periodDao.getAll()
        val p3 = periods.firstOrNull { it.orderIndex == 3 } ?: periods.getOrNull(2)
        val p4 = periods.firstOrNull { it.orderIndex == 4 } ?: periods.getOrNull(3)
        val todayDow = LocalDate.now().dayOfWeek.value
        val targetDays = setOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value, todayDow)
        targetDays.forEach { dow ->
            if (p3 != null) {
                upsertTimetableSlot(dow, p3.id, enabled = true, courseName = "早課 A（第3節）", colorTag = 1)
            }
            if (p4 != null) {
                upsertTimetableSlot(dow, p4.id, enabled = true, courseName = "早課 B（第4節）", colorTag = 2)
            }
        }
    }

    suspend fun setSlotSkippedForDate(dateIso: String, periodId: Long, skipped: Boolean) {
        val key = SkippedSlotEntity.makeKey(dateIso, periodId)
        if (skipped) {
            skippedSlotDao.skip(SkippedSlotEntity(key = key, dateIso = dateIso, periodId = periodId))
        } else {
            skippedSlotDao.unskip(key)
        }
    }

    suspend fun insertRecording(recording: RecordingEntity): Long = recordingDao.insert(recording)

    suspend fun updateRecording(recording: RecordingEntity) = recordingDao.update(recording)

    suspend fun updateRecordingNotes(
        id: Long,
        classNote: String,
        homeworkNote: String,
        aiSummary: String
    ) {
        recordingDao.updateNotes(id, classNote, homeworkNote, aiSummary)
    }

    suspend fun deleteRecording(recording: RecordingEntity) {
        withContext(Dispatchers.IO) {
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        recordingDao.delete(recording)
    }

    suspend fun saveDailySummary(dateIso: String, title: String, summaryText: String, moodOrTag: String = "") {
        dailySummaryDao.upsert(
            DailySummaryEntity(
                dateIso = dateIso,
                title = title.trim(),
                summaryText = summaryText.trim(),
                moodOrTag = moodOrTag.trim(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun getRecordingsDirForDate(dateIso: String): File {
        val dir = File(context.filesDir, "recordings/$dateIso")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun recoverDanglingRecordings() = withContext(Dispatchers.IO) {
        val active = recordingDao.getActiveRecordings()
        active.forEach { rec ->
            val file = File(rec.filePath)
            if (file.exists() && file.length() > 1024L) {
                val duration = readAudioDurationMs(file).takeIf { it > 0L } ?: rec.durationMs
                recordingDao.update(
                    rec.copy(
                        status = RecordingStatus.COMPLETED,
                        actualEndEpochMs = rec.actualEndEpochMs ?: file.lastModified(),
                        durationMs = duration,
                        fileSizeBytes = file.length()
                    )
                )
            } else {
                recordingDao.update(
                    rec.copy(
                        status = RecordingStatus.FAILED,
                        actualEndEpochMs = rec.actualEndEpochMs ?: System.currentTimeMillis(),
                        fileSizeBytes = if (file.exists()) file.length() else 0L
                    )
                )
            }
        }
    }

    fun readAudioDurationMs(file: File): Long {
        if (!file.exists() || file.length() <= 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun createDailyZipFile(dateIso: String): File? = withContext(Dispatchers.IO) {
        val recordings = recordingDao.getByDate(dateIso).filter { File(it.filePath).exists() }
        val summary = dailySummaryDao.getByDate(dateIso)
        val settings = preferences.getSettings()
        if (recordings.isEmpty() && summary == null) return@withContext null

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportDir, "ClassRecorder_${dateIso}.zip")
        val dayOfWeek = LocalDate.parse(dateIso).dayOfWeek.value
        AiExportFormatter.createDailyZipBundle(
            outputZipFile = zipFile,
            dateIso = dateIso,
            dayOfWeek = dayOfWeek,
            recordings = recordings,
            dailySummary = summary,
            promptTemplate = settings.aiPromptTemplate
        )
        zipFile
    }

    suspend fun exportDateToDownloads(dateIso: String): Result<String> = withContext(Dispatchers.IO) {
        val recordings = recordingDao.getByDate(dateIso).filter { File(it.filePath).exists() && File(it.filePath).length() > 0L }
        if (recordings.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("這天沒有可匯出的音檔"))
        }
        val summary = dailySummaryDao.getByDate(dateIso)
        val settings = preferences.getSettings()
        val dayOfWeek = LocalDate.parse(dateIso).dayOfWeek.value
        val promptText = AiExportFormatter.buildAiPromptForDay(dateIso, dayOfWeek, recordings, summary, settings.aiPromptTemplate)
        val markdownText = AiExportFormatter.buildDailyMarkdownReport(dateIso, dayOfWeek, recordings, summary)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/ClassRecorder/$dateIso"
            var exportedCount = 0

            recordings.forEach { rec ->
                val src = File(rec.filePath)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, rec.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativeFolder)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(src).use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    exportedCount++
                }
            }

            writeTextToDownloads(resolver, relativeFolder, "AI_總結提示詞_${dateIso}.txt", promptText)
            writeTextToDownloads(resolver, relativeFolder, "今日筆記與時間軸_${dateIso}.md", markdownText)

            Result.success("已匯出 $exportedCount 段音檔與筆記至「下載 / ClassRecorder / $dateIso」")
        } else {
            val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ClassRecorder/$dateIso")
            targetDir.mkdirs()
            recordings.forEach { rec ->
                val src = File(rec.filePath)
                src.copyTo(File(targetDir, rec.fileName), overwrite = true)
            }
            File(targetDir, "AI_總結提示詞_${dateIso}.txt").writeText(promptText)
            File(targetDir, "今日筆記與時間軸_${dateIso}.md").writeText(markdownText)
            Result.success("已匯出 ${recordings.size} 段音檔至 ${targetDir.absolutePath}")
        }
    }

    private fun writeTextToDownloads(
        resolver: android.content.ContentResolver,
        relativeFolder: String,
        fileName: String,
        content: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, relativeFolder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    suspend fun updateSettingsScheduleEnabled(enabled: Boolean) = preferences.setScheduleEnabled(enabled)
    suspend fun updateKeepArmedOvernight(keep: Boolean) = preferences.setKeepArmedOvernight(keep)
    suspend fun updateShowWeekends(show: Boolean) = preferences.setShowWeekends(show)
    suspend fun updateAudioQuality(preset: AudioQualityPreset) = preferences.setAudioQuality(preset)
    suspend fun updateBuffers(startSec: Int, endSec: Int) = preferences.setBuffers(startSec, endSec)
    suspend fun updatePromptTemplate(template: String) = preferences.setAiPromptTemplate(template)
}

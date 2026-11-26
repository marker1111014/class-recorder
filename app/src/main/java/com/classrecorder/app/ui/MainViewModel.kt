package com.classrecorder.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classrecorder.app.ClassRecorderApp
import com.classrecorder.app.audio.AudioPlayerController
import com.classrecorder.app.audio.PlaybackUiState
import com.classrecorder.app.data.db.AppDatabase
import com.classrecorder.app.data.db.DailySummaryEntity
import com.classrecorder.app.data.db.PeriodDefinitionEntity
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.data.db.RecordingStatus
import com.classrecorder.app.data.db.TimetableSlotEntity
import com.classrecorder.app.data.prefs.AudioQualityPreset
import com.classrecorder.app.data.prefs.UserSettings
import com.classrecorder.app.domain.AiExportFormatter
import com.classrecorder.app.domain.NextAlarmTarget
import com.classrecorder.app.domain.ScheduleCalculator
import com.classrecorder.app.domain.ScheduledSlotInstance
import com.classrecorder.app.service.AlarmScheduler
import com.classrecorder.app.service.ClassRecorderService
import com.classrecorder.app.service.NotificationHelper
import com.classrecorder.app.service.RecorderServiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class DayArchiveGroup(
    val dateIso: String,
    val dayOfWeek: Int,
    val recordings: List<RecordingEntity>,
    val summary: DailySummaryEntity?,
    val totalDurationMs: Long,
    val totalSizeBytes: Long
)

data class PermissionStatus(
    val hasRecordAudio: Boolean = false,
    val hasNotifications: Boolean = true,
    val hasExactAlarm: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = false
) {
    val allCriticalReady: Boolean get() = hasRecordAudio && hasNotifications
    val allRecommendedReady: Boolean
        get() = hasRecordAudio && hasNotifications && hasExactAlarm && isIgnoringBatteryOptimizations
}

data class MainUiState(
    val todayDateIso: String = LocalDate.now().toString(),
    val todayDayOfWeek: Int = LocalDate.now().dayOfWeek.value,
    val periods: List<PeriodDefinitionEntity> = emptyList(),
    val timetableSlots: List<TimetableSlotEntity> = emptyList(),
    val skippedKeys: Set<String> = emptySet(),
    val todayScheduledSlots: List<ScheduledSlotInstance> = emptyList(),
    val activeSlotNow: ScheduledSlotInstance? = null,
    val nextUpcomingSlot: ScheduledSlotInstance? = null,
    val todayRecordings: List<RecordingEntity> = emptyList(),
    val todaySummary: DailySummaryEntity? = null,
    val dayGroups: List<DayArchiveGroup> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val totalStorageBytes: Long = 0L,
    val serviceState: RecorderServiceState = RecorderServiceState(),
    val playbackState: PlaybackUiState = PlaybackUiState()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ClassRecorderApp
    private val repository = app.repository
    val playerController = AudioPlayerController()

    private val _clockTick = MutableStateFlow(System.currentTimeMillis())
    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _shareIntentEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val shareIntentEvents: SharedFlow<Intent> = _shareIntentEvents.asSharedFlow()

    private val baseDataFlow = combine(
        repository.periodsFlow,
        repository.timetableFlow,
        repository.skippedKeysFlow,
        repository.recordingsFlow,
        repository.dailySummariesFlow
    ) { periods, slots, skippedKeys, recordings, summaries ->
        BaseDataBundle(periods, slots, skippedKeys, recordings, summaries)
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseDataFlow,
        repository.settingsFlow,
        repository.totalStorageBytesFlow,
        ClassRecorderService.serviceState,
        playerController.state,
        _clockTick
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val base = values[0] as BaseDataBundle
        val settings = values[1] as UserSettings
        val totalBytes = values[2] as Long
        val serviceState = values[3] as RecorderServiceState
        val playbackState = values[4] as PlaybackUiState

        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val todayIso = today.toString()
        val zoneId = ZoneId.systemDefault()

        val todaySlots = ScheduleCalculator.getSlotsForDate(
            date = today,
            periods = base.periods,
            timetableSlots = base.slots,
            skippedKeys = base.skippedKeys,
            startBufferSeconds = settings.startBufferSeconds,
            endBufferSeconds = settings.endBufferSeconds,
            zoneId = zoneId
        )
        val activeSlot = ScheduleCalculator.findActiveSlot(now, todaySlots)
        val nextSlot = ScheduleCalculator.findNextUpcomingSlot(
            now = now,
            periods = base.periods,
            timetableSlots = base.slots,
            skippedKeys = base.skippedKeys,
            startBufferSeconds = settings.startBufferSeconds,
            endBufferSeconds = settings.endBufferSeconds,
            zoneId = zoneId
        )

        val todayRecordings = base.recordings
            .filter { it.dateIso == todayIso }
            .sortedBy { it.actualStartEpochMs }

        val summaryMap = base.summaries.associateBy { it.dateIso }
        val todaySummary = summaryMap[todayIso]

        val allDates = (base.recordings.map { it.dateIso } + base.summaries.map { it.dateIso })
            .distinct()
            .sortedDescending()

        val groups = allDates.map { dateStr ->
            val recs = base.recordings
                .filter { it.dateIso == dateStr }
                .sortedBy { it.actualStartEpochMs }
            val dow = recs.firstOrNull()?.dayOfWeek ?: runCatching { LocalDate.parse(dateStr).dayOfWeek.value }.getOrDefault(1)
            DayArchiveGroup(
                dateIso = dateStr,
                dayOfWeek = dow,
                recordings = recs,
                summary = summaryMap[dateStr],
                totalDurationMs = recs.sumOf { it.durationMs },
                totalSizeBytes = recs.sumOf { it.fileSizeBytes }
            )
        }

        MainUiState(
            todayDateIso = todayIso,
            todayDayOfWeek = today.dayOfWeek.value,
            periods = base.periods,
            timetableSlots = base.slots,
            skippedKeys = base.skippedKeys,
            todayScheduledSlots = todaySlots,
            activeSlotNow = activeSlot,
            nextUpcomingSlot = nextSlot,
            todayRecordings = todayRecordings,
            todaySummary = todaySummary,
            dayGroups = groups,
            settings = settings,
            totalStorageBytes = totalBytes,
            serviceState = serviceState,
            playbackState = playbackState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState()
    )

    init {
        // Refresh time-based UI status (active slot / next slot) every 20 seconds
        viewModelScope.launch {
            while (true) {
                delay(20_000L)
                _clockTick.value = System.currentTimeMillis()
            }
        }
        // Surface service error/status messages into snackbars
        viewModelScope.launch {
            ClassRecorderService.serviceState.collect { st ->
                val msg = st.lastMessage
                if (!msg.isNullOrBlank()) {
                    _snackbarMessages.tryEmit(msg)
                    ClassRecorderService.clearLastMessage()
                }
            }
        }
        viewModelScope.launch {
            playerController.state.collect { st ->
                val msg = st.errorMessage
                if (!msg.isNullOrBlank()) {
                    _snackbarMessages.tryEmit(msg)
                    playerController.clearError()
                }
            }
        }
    }

    fun refreshPermissions(hasRecordAudio: Boolean) {
        val ctx = getApplication<Application>()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        _permissionStatus.value = PermissionStatus(
            hasRecordAudio = hasRecordAudio,
            hasNotifications = NotificationHelper.canPostNotifications(ctx),
            hasExactAlarm = AlarmScheduler.canScheduleExactAlarms(ctx),
            isIgnoringBatteryOptimizations = ignoringBattery
        )
    }

    fun armSchedule(autoStartIfInActiveSlot: Boolean = true) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            repository.updateSettingsScheduleEnabled(true)
            ClassRecorderService.armSchedule(ctx, autoStartActiveSlot = autoStartIfInActiveSlot)
            _snackbarMessages.tryEmit("已開啟自動排程錄音守候")
        }
    }

    fun disarmSchedule() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            repository.updateSettingsScheduleEnabled(false)
            if (ClassRecorderService.isServiceAlive) {
                ClassRecorderService.disarmSchedule(ctx)
            } else {
                AlarmScheduler.cancelAll(ctx)
            }
            _snackbarMessages.tryEmit("已關閉自動排程守候")
        }
    }

    fun startManualRecording(label: String = "手動錄音") {
        val ctx = getApplication<Application>()
        ClassRecorderService.startManual(ctx, label.ifBlank { "手動錄音" })
    }

    fun startScheduledSlotNow(instance: ScheduledSlotInstance) {
        val ctx = getApplication<Application>()
        ClassRecorderService.startSlotFromUi(ctx, instance)
    }

    fun togglePauseResume() {
        ClassRecorderService.togglePauseResume(getApplication())
    }

    fun stopRecording() {
        ClassRecorderService.stopRecording(getApplication())
    }

    fun toggleSkipToday(periodId: Long, currentlySkipped: Boolean) {
        val todayIso = LocalDate.now().toString()
        viewModelScope.launch {
            repository.setSlotSkippedForDate(todayIso, periodId, skipped = !currentlySkipped)
            syncScheduleWithServiceAndAlarms()
            _snackbarMessages.tryEmit(if (!currentlySkipped) "已設定今日跳過此節" else "已恢復今日此節排程")
        }
    }

    fun saveTimetableSlot(
        dayOfWeek: Int,
        periodId: Long,
        enabled: Boolean,
        courseName: String,
        classroom: String,
        colorTag: Int
    ) {
        viewModelScope.launch {
            repository.upsertTimetableSlot(dayOfWeek, periodId, enabled, courseName, classroom, colorTag)
            syncScheduleWithServiceAndAlarms()
        }
    }

    fun applySampleMorning34Schedule() {
        viewModelScope.launch {
            repository.applySampleMorning34Schedule()
            syncScheduleWithServiceAndAlarms()
            _snackbarMessages.tryEmit("已套用第 3、4 節（10:10~11:00、11:10~12:00）範例排程")
        }
    }

    fun savePeriod(period: PeriodDefinitionEntity) {
        viewModelScope.launch {
            repository.upsertPeriod(period)
            syncScheduleWithServiceAndAlarms()
            _snackbarMessages.tryEmit("已更新節次時間「${period.name}」")
        }
    }

    fun deletePeriod(period: PeriodDefinitionEntity) {
        viewModelScope.launch {
            repository.deletePeriod(period)
            syncScheduleWithServiceAndAlarms()
            _snackbarMessages.tryEmit("已刪除節次「${period.name}」")
        }
    }

    fun applyUniversityPeriodPreset() {
        viewModelScope.launch {
            repository.applyPeriodPreset(AppDatabase.DEFAULT_UNIVERSITY_PERIODS)
            syncScheduleWithServiceAndAlarms()
            _snackbarMessages.tryEmit("已套用標準大學 1~8 節時間表（08:10 起）")
        }
    }

    fun applyHighSchoolPeriodPreset() {
        viewModelScope.launch {
            repository.applyPeriodPreset(AppDatabase.HIGH_SCHOOL_PERIODS)
            syncScheduleWithServiceAndAlarms()
            _snackbarMessages.tryEmit("已套用高中 1~8 節時間表")
        }
    }

    fun saveRecordingNotes(
        recordingId: Long,
        classNote: String,
        homeworkNote: String,
        aiSummary: String
    ) {
        viewModelScope.launch {
            repository.updateRecordingNotes(recordingId, classNote, homeworkNote, aiSummary)
            _snackbarMessages.tryEmit("已儲存課堂筆記")
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            if (playerController.state.value.activeRecordingId == recording.id) {
                playerController.stop()
            }
            repository.deleteRecording(recording)
            _snackbarMessages.tryEmit("已刪除錄音「${recording.courseName.ifBlank { recording.periodName }}」")
        }
    }

    fun saveDailySummary(dateIso: String, title: String, summaryText: String, moodOrTag: String = "") {
        viewModelScope.launch {
            repository.saveDailySummary(dateIso, title, summaryText, moodOrTag)
            _snackbarMessages.tryEmit("已儲存 $dateIso 的每日總結")
        }
    }

    fun buildAiPromptForDate(dateIso: String): String {
        val group = uiState.value.dayGroups.firstOrNull { it.dateIso == dateIso }
        val recs = group?.recordings ?: uiState.value.todayRecordings.filter { it.dateIso == dateIso }
        val summary = group?.summary ?: if (dateIso == uiState.value.todayDateIso) uiState.value.todaySummary else null
        val dow = group?.dayOfWeek ?: runCatching { LocalDate.parse(dateIso).dayOfWeek.value }.getOrDefault(1)
        return AiExportFormatter.buildAiPromptForDay(
            dateIso = dateIso,
            dayOfWeek = dow,
            recordings = recs,
            dailySummary = summary,
            promptTemplate = uiState.value.settings.aiPromptTemplate
        )
    }

    fun copyAiPromptToClipboard(dateIso: String) {
        val ctx = getApplication<Application>()
        val prompt = buildAiPromptForDate(dateIso)
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI 課堂總結提示詞", prompt))
        _snackbarMessages.tryEmit("已複製 AI 總結提示詞（含今日課表與筆記）")
    }

    fun copyMarkdownReportToClipboard(dateIso: String) {
        val ctx = getApplication<Application>()
        val group = uiState.value.dayGroups.firstOrNull { it.dateIso == dateIso }
        val recs = group?.recordings ?: emptyList()
        val summary = group?.summary
        val dow = group?.dayOfWeek ?: runCatching { LocalDate.parse(dateIso).dayOfWeek.value }.getOrDefault(1)
        val md = AiExportFormatter.buildDailyMarkdownReport(dateIso, dow, recs, summary)
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("每日課堂總結報告", md))
        _snackbarMessages.tryEmit("已複製 $dateIso 的 Markdown 總結報告")
    }

    fun shareSingleRecording(recording: RecordingEntity) {
        val ctx = getApplication<Application>()
        val file = File(recording.filePath)
        if (!file.exists() || file.length() <= 0L) {
            _snackbarMessages.tryEmit("找不到該錄音檔案")
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recording.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _shareIntentEvents.tryEmit(Intent.createChooser(shareIntent, "分享課堂錄音"))
    }

    fun shareAllAudioForDate(dateIso: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val recs = repository.getRecordingsForDate(dateIso)
                .filter { it.status == RecordingStatus.COMPLETED && File(it.filePath).exists() }
            if (recs.isEmpty()) {
                _snackbarMessages.tryEmit("$dateIso 沒有可分享的已完成錄音")
                return@launch
            }
            val uris = ArrayList<Uri>()
            recs.forEach { rec ->
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(rec.filePath))
                uris.add(uri)
            }
            val prompt = buildAiPromptForDate(dateIso)
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    putExtra(Intent.EXTRA_TEXT, prompt)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/mp4"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_TEXT, prompt)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            _shareIntentEvents.tryEmit(Intent.createChooser(intent, "分享 $dateIso 全部錄音與 AI 提示詞"))
        }
    }

    fun shareDailyZipBundle(dateIso: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val zipFile = repository.createDailyZipFile(dateIso)
            if (zipFile == null || !zipFile.exists()) {
                _snackbarMessages.tryEmit("沒有可打包匯出的資料")
                return@launch
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", zipFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "課時錄音記_${dateIso}.zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _shareIntentEvents.tryEmit(Intent.createChooser(intent, "匯出 $dateIso 完整資料包 (.zip)"))
        }
    }

    fun exportDateToDownloads(dateIso: String) {
        viewModelScope.launch {
            val result = repository.exportDateToDownloads(dateIso)
            result.fold(
                onSuccess = { msg -> _snackbarMessages.tryEmit(msg) },
                onFailure = { err -> _snackbarMessages.tryEmit(err.localizedMessage ?: "匯出失敗") }
            )
        }
    }

    fun updateAudioQuality(preset: AudioQualityPreset) {
        viewModelScope.launch {
            repository.updateAudioQuality(preset)
        }
    }

    fun updateBuffers(startSec: Int, endSec: Int) {
        viewModelScope.launch {
            repository.updateBuffers(startSec, endSec)
            syncScheduleWithServiceAndAlarms()
        }
    }

    fun updateKeepArmedOvernight(keep: Boolean) {
        viewModelScope.launch {
            repository.updateKeepArmedOvernight(keep)
        }
    }

    fun updateShowWeekends(show: Boolean) {
        viewModelScope.launch {
            repository.updateShowWeekends(show)
        }
    }

    fun updatePromptTemplate(template: String) {
        viewModelScope.launch {
            repository.updatePromptTemplate(template)
            _snackbarMessages.tryEmit("已更新 AI 總結提示詞範本")
        }
    }

    private suspend fun syncScheduleWithServiceAndAlarms() {
        val ctx = getApplication<Application>()
        val target: NextAlarmTarget? = AlarmScheduler.rescheduleNextAlarm(ctx, repository)
        ClassRecorderService.refreshScheduleIfRunning(ctx)
        _clockTick.value = System.currentTimeMillis()
        if (target == null && ClassRecorderService.isServiceAlive) {
            ClassRecorderService.refreshScheduleIfRunning(ctx)
        }
    }

    override fun onCleared() {
        playerController.stop()
        super.onCleared()
    }

    private data class BaseDataBundle(
        val periods: List<PeriodDefinitionEntity>,
        val slots: List<TimetableSlotEntity>,
        val skippedKeys: Set<String>,
        val recordings: List<RecordingEntity>,
        val summaries: List<DailySummaryEntity>
    )
}

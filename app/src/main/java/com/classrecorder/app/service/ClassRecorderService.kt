package com.classrecorder.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.classrecorder.app.ClassRecorderApp
import com.classrecorder.app.audio.AudioRecorderEngine
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.data.db.RecordingStatus
import com.classrecorder.app.data.repository.ClassRecorderRepository
import com.classrecorder.app.domain.FilenameHelper
import com.classrecorder.app.domain.NextAlarmTarget
import com.classrecorder.app.domain.ScheduleCalculator
import com.classrecorder.app.domain.ScheduledSlotInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class RecorderPhase {
    IDLE,
    ARMED,
    RECORDING,
    PAUSED
}

data class RecorderServiceState(
    val phase: RecorderPhase = RecorderPhase.IDLE,
    val activeRecordingId: Long? = null,
    val activePeriodId: Long? = null,
    val activeTitle: String = "",
    val activeTimeRange: String = "",
    val elapsedMs: Long = 0L,
    val normalizedAmplitude: Float = 0f,
    val nextSlotSummary: String = "",
    val isManualSession: Boolean = false,
    val lastMessage: String? = null
)

class ClassRecorderService : LifecycleService() {

    private lateinit var repository: ClassRecorderRepository
    private lateinit var recorderEngine: AudioRecorderEngine

    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    private var activeEntity: RecordingEntity? = null
    private var scheduledEndEpochMs: Long? = null
    private var isInForeground: Boolean = false
    private var isStopping: Boolean = false

    private var accumulatedRecordMs: Long = 0L
    private var lastResumedAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        isServiceAlive = true
        val app = application as ClassRecorderApp
        repository = app.repository
        recorderEngine = AudioRecorderEngine(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_ARM_SCHEDULE -> handleArmSchedule(
                autoStartActiveSlot = intent.getBooleanExtra(EXTRA_AUTO_START_ACTIVE_SLOT, false)
            )
            ACTION_START_SLOT -> handleStartSlot(intent)
            ACTION_START_MANUAL -> handleStartManual(
                label = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "手動錄音"
            )
            ACTION_PAUSE_OR_RESUME -> handlePauseOrResume()
            ACTION_STOP_RECORDING, ACTION_ALARM_STOP_SLOT -> handleStopRecording(
                fromAlarm = (action == ACTION_ALARM_STOP_SLOT),
                targetPeriodId = intent.getLongExtra(EXTRA_PERIOD_ID, -1L).takeIf { it > 0L }
            )
            ACTION_REFRESH_SCHEDULE -> handleRefreshSchedule()
            ACTION_DISARM -> handleDisarm()
        }
        return START_STICKY
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureForegroundWithMic(notification: android.app.Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
            }
            isInForeground = true
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun handleArmSchedule(autoStartActiveSlot: Boolean) {
        if (!hasRecordAudioPermission()) {
            _serviceState.update { it.copy(lastMessage = "請先允許麥克風權限才能啟用自動錄音守候") }
            stopSelf()
            return
        }

        if (recorderEngine.isRecordingActive) {
            lifecycleScope.launch {
                val nextSummary = refreshNextAlarmSummary()
                _serviceState.update { it.copy(nextSlotSummary = nextSummary) }
            }
            return
        }

        val initialNotif = NotificationHelper.buildArmedNotification(this, "正在計算今日課表排程…")
        if (!ensureForegroundWithMic(initialNotif)) {
            _serviceState.update { it.copy(lastMessage = "無法從背景啟動麥克風守候，請在 App 內開啟") }
            stopSelf()
            return
        }

        lifecycleScope.launch {
            repository.updateSettingsScheduleEnabled(true)
            val now = ZonedDateTime.now()
            val settings = repository.preferences.getSettings()
            val periods = repository.getPeriods()
            val slots = repository.getTimetableSlots()
            val skipped = repository.getSkippedKeysFrom(LocalDate.now().toString())
            val todaySlots = ScheduleCalculator.getSlotsForDate(
                date = now.toLocalDate(),
                periods = periods,
                timetableSlots = slots,
                skippedKeys = skipped,
                startBufferSeconds = settings.startBufferSeconds,
                endBufferSeconds = settings.endBufferSeconds
            )
            val activeSlot = ScheduleCalculator.findActiveSlot(now, todaySlots)
            if (autoStartActiveSlot && activeSlot != null) {
                startSlotInternal(activeSlot)
            } else {
                val summary = refreshNextAlarmSummary()
                transitionToArmedNotification(summary)
            }
        }
    }

    private fun handleStartSlot(intent: Intent) {
        NotificationHelper.cancelStartPrompt(this)
        if (!hasRecordAudioPermission()) {
            _serviceState.update { it.copy(lastMessage = "缺少麥克風權限，無法開始課堂錄音") }
            if (!isInForeground) stopSelf()
            return
        }

        val dateIso = intent.getStringExtra(EXTRA_DATE_ISO) ?: LocalDate.now().toString()
        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, LocalDate.now().dayOfWeek.value)
        val periodId = intent.getLongExtra(EXTRA_PERIOD_ID, -1L).takeIf { it > 0L }
        val periodOrder = intent.getIntExtra(EXTRA_PERIOD_ORDER, 1)
        val periodName = intent.getStringExtra(EXTRA_PERIOD_NAME) ?: "第 $periodOrder 節"
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: ""
        val startMinute = intent.getIntExtra(EXTRA_START_MINUTE, -1).takeIf { it >= 0 }
        val endMinute = intent.getIntExtra(EXTRA_END_MINUTE, -1).takeIf { it >= 0 }
        val endEpochMs = intent.getLongExtra(EXTRA_END_EPOCH_MS, -1L).takeIf { it > 0L }

        val title = courseName.ifBlank { periodName }
        val timeRange = if (startMinute != null && endMinute != null) {
            "${FilenameHelper.formatMinuteOfDay(startMinute)}~${FilenameHelper.formatMinuteOfDay(endMinute)}"
        } else {
            ""
        }

        val notif = NotificationHelper.buildRecordingNotification(
            context = this,
            title = title,
            timeRange = timeRange,
            elapsedMs = 0L,
            isPaused = false
        )

        if (!isInForeground && !ensureForegroundWithMic(notif)) {
            NotificationHelper.showManualStartPromptNotification(
                context = this,
                startServiceIntent = intent,
                title = title,
                timeRange = timeRange
            )
            stopSelf()
            return
        }

        lifecycleScope.launch {
            if (recorderEngine.isRecordingActive) {
                if (activeEntity?.periodId == periodId && periodId != null) {
                    return@launch
                }
                finalizeCurrentRecording(markCompleted = true)
                delay(150L)
            }

            if (periodId != null) {
                val skipped = repository.getSkippedKeysFrom(dateIso)
                if ("${dateIso}_${periodId}" in skipped) {
                    val summary = refreshNextAlarmSummary()
                    transitionToArmedNotification(summary)
                    return@launch
                }
            }

            startRecordingSession(
                dateIso = dateIso,
                dayOfWeek = dayOfWeek,
                periodId = periodId,
                periodOrder = periodOrder,
                periodName = periodName,
                courseName = courseName,
                scheduledStartMinute = startMinute,
                scheduledEndMinute = endMinute,
                endEpochMs = endEpochMs,
                isManual = false
            )
        }
    }

    private suspend fun startSlotInternal(slot: ScheduledSlotInstance) {
        startRecordingSession(
            dateIso = slot.dateIso,
            dayOfWeek = slot.date.dayOfWeek.value,
            periodId = slot.period.id,
            periodOrder = slot.period.orderIndex,
            periodName = slot.period.name,
            courseName = slot.slot.courseName,
            scheduledStartMinute = slot.period.startMinuteOfDay,
            scheduledEndMinute = slot.period.endMinuteOfDay,
            endEpochMs = slot.effectiveEnd.toInstant().toEpochMilli(),
            isManual = false
        )
    }

    private fun handleStartManual(label: String) {
        if (!hasRecordAudioPermission()) {
            _serviceState.update { it.copy(lastMessage = "請先授予麥克風權限") }
            if (!isInForeground) stopSelf()
            return
        }

        val notif = NotificationHelper.buildRecordingNotification(
            context = this,
            title = label,
            timeRange = "手動錄音",
            elapsedMs = 0L,
            isPaused = false
        )
        if (!isInForeground && !ensureForegroundWithMic(notif)) {
            _serviceState.update { it.copy(lastMessage = "無法啟動前景錄音服務") }
            stopSelf()
            return
        }

        lifecycleScope.launch {
            if (recorderEngine.isRecordingActive) {
                finalizeCurrentRecording(markCompleted = true)
                delay(150L)
            }
            val nowDate = LocalDate.now()
            startRecordingSession(
                dateIso = nowDate.toString(),
                dayOfWeek = nowDate.dayOfWeek.value,
                periodId = null,
                periodOrder = 0,
                periodName = "手動錄音",
                courseName = if (label == "手動錄音") "" else label,
                scheduledStartMinute = null,
                scheduledEndMinute = null,
                endEpochMs = null,
                isManual = true
            )
        }
    }

    private suspend fun startRecordingSession(
        dateIso: String,
        dayOfWeek: Int,
        periodId: Long?,
        periodOrder: Int,
        periodName: String,
        courseName: String,
        scheduledStartMinute: Int?,
        scheduledEndMinute: Int?,
        endEpochMs: Long?,
        isManual: Boolean
    ) {
        val settings = repository.preferences.getSettings()
        val title = courseName.ifBlank { periodName }
        val timeRange = if (scheduledStartMinute != null && scheduledEndMinute != null) {
            "${FilenameHelper.formatMinuteOfDay(scheduledStartMinute)}~${FilenameHelper.formatMinuteOfDay(scheduledEndMinute)}"
        } else {
            "即時錄音"
        }

        val dir = repository.getRecordingsDirForDate(dateIso)
        val desiredName = if (!isManual && scheduledStartMinute != null && scheduledEndMinute != null) {
            FilenameHelper.buildScheduledFilename(
                dateIso = dateIso,
                periodOrder = periodOrder,
                startMinute = scheduledStartMinute,
                endMinute = scheduledEndMinute,
                courseOrPeriodName = title
            )
        } else {
            val timeStamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
            FilenameHelper.buildManualFilename(dateIso, timeStamp, title)
        }
        val outputFile = FilenameHelper.resolveUniqueFile(dir, desiredName)

        val startResult = withContext(Dispatchers.IO) {
            recorderEngine.start(outputFile, settings.audioQuality)
        }
        if (startResult.isFailure) {
            val err = startResult.exceptionOrNull()?.localizedMessage ?: "麥克風目前被佔用"
            _serviceState.update { it.copy(lastMessage = "錄音啟動失敗：$err") }
            val nextSummary = refreshNextAlarmSummary()
            if (settings.scheduleEnabled) {
                transitionToArmedNotification(nextSummary)
            } else {
                stopForegroundAndSelf()
            }
            return
        }

        val nowMs = System.currentTimeMillis()
        val entity = RecordingEntity(
            dateIso = dateIso,
            dayOfWeek = dayOfWeek,
            periodId = periodId,
            periodName = periodName,
            courseName = courseName,
            scheduledStartMinute = scheduledStartMinute,
            scheduledEndMinute = scheduledEndMinute,
            actualStartEpochMs = nowMs,
            filePath = outputFile.absolutePath,
            fileName = outputFile.name,
            status = RecordingStatus.RECORDING,
            isManual = isManual
        )
        val insertedId = repository.insertRecording(entity)
        activeEntity = entity.copy(id = insertedId)
        scheduledEndEpochMs = endEpochMs
        accumulatedRecordMs = 0L
        lastResumedAtMs = nowMs

        val lockTimeoutMs = if (endEpochMs != null && endEpochMs > nowMs) {
            (endEpochMs - nowMs) + 5 * 60_000L
        } else {
            4 * 60 * 60_000L
        }
        acquireWakeLock(lockTimeoutMs)

        val nextSummary = AlarmScheduler.rescheduleNextAlarm(this, repository)?.let {
            formatAlarmTargetText(it)
        } ?: ""

        _serviceState.value = RecorderServiceState(
            phase = RecorderPhase.RECORDING,
            activeRecordingId = insertedId,
            activePeriodId = periodId,
            activeTitle = title,
            activeTimeRange = timeRange,
            elapsedMs = 0L,
            normalizedAmplitude = 0f,
            nextSlotSummary = nextSummary,
            isManualSession = isManual,
            lastMessage = null
        )

        ensureForegroundWithMic(
            NotificationHelper.buildRecordingNotification(
                context = this,
                title = title,
                timeRange = timeRange,
                elapsedMs = 0L,
                isPaused = false
            )
        )
        startTickerLoop()
    }

    private fun handlePauseOrResume() {
        val current = _serviceState.value
        val entity = activeEntity ?: return
        if (current.phase == RecorderPhase.RECORDING) {
            if (recorderEngine.pause()) {
                accumulatedRecordMs += (System.currentTimeMillis() - lastResumedAtMs).coerceAtLeast(0L)
                lifecycleScope.launch {
                    repository.updateRecording(
                        entity.copy(status = RecordingStatus.PAUSED, durationMs = accumulatedRecordMs)
                    )
                    activeEntity = entity.copy(status = RecordingStatus.PAUSED, durationMs = accumulatedRecordMs)
                }
                _serviceState.update {
                    it.copy(phase = RecorderPhase.PAUSED, elapsedMs = accumulatedRecordMs, normalizedAmplitude = 0f)
                }
                ensureForegroundWithMic(
                    NotificationHelper.buildRecordingNotification(
                        context = this,
                        title = current.activeTitle,
                        timeRange = current.activeTimeRange,
                        elapsedMs = accumulatedRecordMs,
                        isPaused = true
                    )
                )
            }
        } else if (current.phase == RecorderPhase.PAUSED) {
            if (recorderEngine.resume()) {
                lastResumedAtMs = System.currentTimeMillis()
                lifecycleScope.launch {
                    repository.updateRecording(entity.copy(status = RecordingStatus.RECORDING))
                    activeEntity = entity.copy(status = RecordingStatus.RECORDING)
                }
                _serviceState.update { it.copy(phase = RecorderPhase.RECORDING) }
                ensureForegroundWithMic(
                    NotificationHelper.buildRecordingNotification(
                        context = this,
                        title = current.activeTitle,
                        timeRange = current.activeTimeRange,
                        elapsedMs = accumulatedRecordMs,
                        isPaused = false
                    )
                )
            }
        }
    }

    private fun handleStopRecording(fromAlarm: Boolean, targetPeriodId: Long?) {
        val activeSnapshot = activeEntity ?: run {
            if (!fromAlarm) {
                lifecycleScope.launch {
                    val settings = repository.preferences.getSettings()
                    if (!settings.scheduleEnabled) {
                        stopForegroundAndSelf()
                    }
                }
            }
            return
        }
        if (fromAlarm && targetPeriodId != null && activeSnapshot.periodId != targetPeriodId) {
            return
        }

        lifecycleScope.launch {
            if (isStopping) return@launch
            val active = activeEntity ?: return@launch
            if (fromAlarm && targetPeriodId != null && active.periodId != targetPeriodId) {
                return@launch
            }
            val finishedPeriodId = active.periodId
            val wasScheduledSlot = !active.isManual
            val finishedDateIso = active.dateIso

            finalizeCurrentRecording(markCompleted = true)

            val settings = repository.preferences.getSettings()
            val now = ZonedDateTime.now()

            // Check if a back-to-back slot begins immediately (end == nextStart)
            if (settings.scheduleEnabled) {
                val periods = repository.getPeriods()
                val slots = repository.getTimetableSlots()
                val skipped = repository.getSkippedKeysFrom(now.toLocalDate().toString())
                val todaySlots = ScheduleCalculator.getSlotsForDate(
                    date = now.toLocalDate(),
                    periods = periods,
                    timetableSlots = slots,
                    skippedKeys = skipped,
                    startBufferSeconds = settings.startBufferSeconds,
                    endBufferSeconds = settings.endBufferSeconds
                )
                val immediateNext = ScheduleCalculator.findActiveSlot(now.plusSeconds(2), todaySlots)
                if (immediateNext != null && immediateNext.period.id != finishedPeriodId) {
                    delay(180L)
                    startSlotInternal(immediateNext)
                    return@launch
                }

                val nextTarget = AlarmScheduler.rescheduleNextAlarm(
                    context = this@ClassRecorderService,
                    repository = repository,
                    now = now,
                    afterJustFinishedPeriodId = if (!fromAlarm) finishedPeriodId else null
                )
                val hasMoreToday = ScheduleCalculator.hasRemainingSlotsToday(now, todaySlots) &&
                    todaySlots.any {
                        !it.isSkipped &&
                            it.effectiveStart.isAfter(now) &&
                            it.period.id != finishedPeriodId
                    }

                if (wasScheduledSlot && !hasMoreToday) {
                    val todayCount = repository.getRecordingsForDate(finishedDateIso)
                        .count { it.status == RecordingStatus.COMPLETED }
                    if (todayCount > 0) {
                        NotificationHelper.showAfterSchoolSummaryNotification(
                            context = this@ClassRecorderService,
                            recordedCountToday = todayCount,
                            dateIso = finishedDateIso
                        )
                    }
                }

                if (hasMoreToday || settings.keepArmedOvernight) {
                    transitionToArmedNotification(nextTarget?.let { formatAlarmTargetText(it) } ?: "目前無排程")
                } else {
                    stopForegroundAndSelf()
                }
            } else {
                AlarmScheduler.cancelAll(this@ClassRecorderService)
                stopForegroundAndSelf()
            }
        }
    }

    private fun handleRefreshSchedule() {
        lifecycleScope.launch {
            val summary = refreshNextAlarmSummary()
            if (_serviceState.value.phase == RecorderPhase.ARMED) {
                transitionToArmedNotification(summary)
            } else {
                _serviceState.update { it.copy(nextSlotSummary = summary) }
            }
        }
    }

    private fun handleDisarm() {
        lifecycleScope.launch {
            if (recorderEngine.isRecordingActive) {
                finalizeCurrentRecording(markCompleted = true)
            }
            repository.updateSettingsScheduleEnabled(false)
            AlarmScheduler.cancelAll(this@ClassRecorderService)
            stopForegroundAndSelf()
        }
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            var lastNotificationUpdateMs = 0L
            while (recorderEngine.isRecordingActive) {
                val nowMs = System.currentTimeMillis()
                val phase = _serviceState.value.phase
                val totalElapsed = if (phase == RecorderPhase.RECORDING) {
                    accumulatedRecordMs + (nowMs - lastResumedAtMs).coerceAtLeast(0L)
                } else {
                    accumulatedRecordMs
                }
                val amp = if (phase == RecorderPhase.RECORDING) {
                    recorderEngine.sampleNormalizedAmplitude()
                } else {
                    0f
                }

                _serviceState.update {
                    it.copy(
                        elapsedMs = totalElapsed,
                        normalizedAmplitude = amp
                    )
                }

                if (nowMs - lastNotificationUpdateMs >= 1000L) {
                    lastNotificationUpdateMs = nowMs
                    val st = _serviceState.value
                    ensureForegroundWithMic(
                        NotificationHelper.buildRecordingNotification(
                            context = this@ClassRecorderService,
                            title = st.activeTitle,
                            timeRange = st.activeTimeRange,
                            elapsedMs = totalElapsed,
                            isPaused = (st.phase == RecorderPhase.PAUSED)
                        )
                    )
                }

                val endAt = scheduledEndEpochMs
                if (endAt != null && nowMs >= endAt) {
                    handleStopRecording(fromAlarm = true, targetPeriodId = activeEntity?.periodId)
                    break
                }

                delay(250L)
            }
        }
    }

    private suspend fun finalizeCurrentRecording(markCompleted: Boolean) {
        if (isStopping) return
        isStopping = true
        tickerJob?.cancel()
        tickerJob = null
        val entity = activeEntity
        val currentPhase = _serviceState.value.phase
        val finalElapsed = if (currentPhase == RecorderPhase.RECORDING) {
            accumulatedRecordMs + (System.currentTimeMillis() - lastResumedAtMs).coerceAtLeast(0L)
        } else {
            accumulatedRecordMs
        }

        activeEntity = null
        scheduledEndEpochMs = null
        accumulatedRecordMs = 0L

        try {
            val file: File? = withContext(Dispatchers.IO) {
                recorderEngine.stop()
            }
            releaseWakeLock()

            if (entity != null) {
                withContext(Dispatchers.IO) {
                    val actualFile = file ?: File(entity.filePath)
                    val existsAndNonEmpty = actualFile.exists() && actualFile.length() > 512L
                    val durationFromMedia = if (existsAndNonEmpty) {
                        repository.readAudioDurationMs(actualFile).takeIf { it > 0L } ?: finalElapsed
                    } else {
                        finalElapsed
                    }
                    val finalStatus = if (markCompleted && existsAndNonEmpty) {
                        RecordingStatus.COMPLETED
                    } else {
                        RecordingStatus.FAILED
                    }
                    repository.updateRecording(
                        entity.copy(
                            actualEndEpochMs = System.currentTimeMillis(),
                            durationMs = durationFromMedia,
                            fileSizeBytes = if (actualFile.exists()) actualFile.length() else 0L,
                            status = finalStatus
                        )
                    )
                }
            }
        } finally {
            isStopping = false
        }
    }

    private suspend fun refreshNextAlarmSummary(): String {
        val target = AlarmScheduler.rescheduleNextAlarm(this, repository)
        return target?.let { formatAlarmTargetText(it) } ?: "目前無即將開始的課堂排程"
    }

    private fun formatAlarmTargetText(target: NextAlarmTarget): String = when (target) {
        is NextAlarmTarget.StartSlot -> {
            val ins = target.instance
            val dayText = if (ins.date == LocalDate.now()) {
                "今天"
            } else {
                "${ins.date.monthValue}/${ins.date.dayOfMonth} (${FilenameHelper.dayOfWeekShortLabel(ins.date.dayOfWeek.value)})"
            }
            "下一節：$dayText ${ins.timeRangeText} ${ins.displayTitle}"
        }
        is NextAlarmTarget.StopSlot -> {
            val ins = target.instance
            "進行中：${ins.timeRangeText} ${ins.displayTitle}"
        }
    }

    private fun transitionToArmedNotification(nextSlotSummary: String) {
        _serviceState.value = RecorderServiceState(
            phase = RecorderPhase.ARMED,
            nextSlotSummary = nextSlotSummary
        )
        ensureForegroundWithMic(
            NotificationHelper.buildArmedNotification(this, nextSlotSummary)
        )
    }

    private fun stopForegroundAndSelf() {
        isInForeground = false
        _serviceState.value = RecorderServiceState(phase = RecorderPhase.IDLE)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClassRecorder:MicSession").apply {
            setReferenceCounted(false)
            acquire(timeoutMs.coerceAtLeast(60_000L))
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            runCatching {
                if (wl.isHeld) wl.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        if (recorderEngine.isRecordingActive) {
            val entity = activeEntity
            val file = recorderEngine.stop()
            if (entity != null && file != null && file.exists() && file.length() > 512L) {
                // Best-effort cleanup will also run via recoverDanglingRecordings on next app open
                runCatching {
                    val elapsed = (System.currentTimeMillis() - entity.actualStartEpochMs).coerceAtLeast(0L)
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        repository.updateRecording(
                            entity.copy(
                                actualEndEpochMs = System.currentTimeMillis(),
                                durationMs = elapsed,
                                fileSizeBytes = file.length(),
                                status = RecordingStatus.COMPLETED
                            )
                        )
                    }
                }
            }
        }
        releaseWakeLock()
        isInForeground = false
        isServiceAlive = false
        _serviceState.value = RecorderServiceState(phase = RecorderPhase.IDLE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ARM_SCHEDULE = "com.classrecorder.app.action.ARM_SCHEDULE"
        const val ACTION_START_SLOT = "com.classrecorder.app.action.START_SLOT"
        const val ACTION_START_MANUAL = "com.classrecorder.app.action.START_MANUAL"
        const val ACTION_PAUSE_OR_RESUME = "com.classrecorder.app.action.PAUSE_OR_RESUME"
        const val ACTION_STOP_RECORDING = "com.classrecorder.app.action.STOP_RECORDING"
        const val ACTION_ALARM_STOP_SLOT = "com.classrecorder.app.action.ALARM_STOP_SLOT"
        const val ACTION_REFRESH_SCHEDULE = "com.classrecorder.app.action.REFRESH_SCHEDULE"
        const val ACTION_DISARM = "com.classrecorder.app.action.DISARM"

        const val EXTRA_AUTO_START_ACTIVE_SLOT = "extra_auto_start_active_slot"
        const val EXTRA_DATE_ISO = "extra_date_iso"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_PERIOD_ID = "extra_period_id"
        const val EXTRA_PERIOD_ORDER = "extra_period_order"
        const val EXTRA_PERIOD_NAME = "extra_period_name"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_START_MINUTE = "extra_start_minute"
        const val EXTRA_END_MINUTE = "extra_end_minute"
        const val EXTRA_END_EPOCH_MS = "extra_end_epoch_ms"

        @Volatile
        var isServiceAlive: Boolean = false
            private set

        private val _serviceState = MutableStateFlow(RecorderServiceState())
        val serviceState: StateFlow<RecorderServiceState> = _serviceState.asStateFlow()

        fun armSchedule(context: Context, autoStartActiveSlot: Boolean = true) {
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_ARM_SCHEDULE
                putExtra(EXTRA_AUTO_START_ACTIVE_SLOT, autoStartActiveSlot)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startManual(context: Context, label: String = "手動錄音") {
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_START_MANUAL
                putExtra(EXTRA_COURSE_NAME, label)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startSlotFromUi(context: Context, instance: ScheduledSlotInstance) {
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_START_SLOT
                putExtra(EXTRA_DATE_ISO, instance.dateIso)
                putExtra(EXTRA_DAY_OF_WEEK, instance.date.dayOfWeek.value)
                putExtra(EXTRA_PERIOD_ID, instance.period.id)
                putExtra(EXTRA_PERIOD_ORDER, instance.period.orderIndex)
                putExtra(EXTRA_PERIOD_NAME, instance.period.name)
                putExtra(EXTRA_COURSE_NAME, instance.slot.courseName)
                putExtra(EXTRA_START_MINUTE, instance.period.startMinuteOfDay)
                putExtra(EXTRA_END_MINUTE, instance.period.endMinuteOfDay)
                putExtra(EXTRA_END_EPOCH_MS, instance.effectiveEnd.toInstant().toEpochMilli())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun togglePauseResume(context: Context) {
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_PAUSE_OR_RESUME
            }
            context.startService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }

        fun disarmSchedule(context: Context) {
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_DISARM
            }
            if (isServiceAlive) {
                context.startService(intent)
            }
        }

        fun refreshScheduleIfRunning(context: Context) {
            if (!isServiceAlive) return
            val intent = Intent(context, ClassRecorderService::class.java).apply {
                action = ACTION_REFRESH_SCHEDULE
            }
            context.startService(intent)
        }

        fun clearLastMessage() {
            _serviceState.update { it.copy(lastMessage = null) }
        }
    }
}

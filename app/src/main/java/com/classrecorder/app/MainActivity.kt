package com.classrecorder.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.ui.MainViewModel
import com.classrecorder.app.ui.components.AiExportSheet
import com.classrecorder.app.ui.components.AudioPlayerBar
import com.classrecorder.app.ui.components.NoteEditorDialog
import com.classrecorder.app.ui.screens.ArchiveScreen
import com.classrecorder.app.ui.screens.DailySummaryDetailScreen
import com.classrecorder.app.ui.screens.SettingsScreen
import com.classrecorder.app.ui.screens.TimetableScreen
import com.classrecorder.app.ui.screens.TodayScreen
import com.classrecorder.app.ui.theme.ClassRecorderTheme

private enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("今日", Icons.Rounded.Today),
    TIMETABLE("課表", Icons.Rounded.CalendarViewWeek),
    ARCHIVE("紀錄與總結", Icons.Rounded.AutoStories),
    SETTINGS("設定", Icons.Rounded.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val pendingOpenExportDate = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenExportDate.value = intent?.getStringExtra(EXTRA_OPEN_EXPORT_DATE)
        refreshPermissionSnapshot()

        setContent {
            ClassRecorderTheme {
                ClassRecorderRoot(
                    viewModel = viewModel,
                    openExportDateFromNotification = pendingOpenExportDate.value,
                    onConsumeNotificationDate = { pendingOpenExportDate.value = null },
                    onRefreshPermissions = { refreshPermissionSnapshot() },
                    onOpenExactAlarmSettings = { openExactAlarmSettings() },
                    onOpenBatteryOptimizationSettings = { openBatteryOptimizationSettings() },
                    onLaunchShareIntent = { intent -> runCatching { startActivity(intent) } }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_EXPORT_DATE)?.let {
            pendingOpenExportDate.value = it
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionSnapshot()
    }

    private fun refreshPermissionSnapshot() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.refreshPermissions(hasMic)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(directIntent) }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_EXPORT_DATE = "extra_open_export_date"
    }
}

@Composable
private fun ClassRecorderRoot(
    viewModel: MainViewModel,
    openExportDateFromNotification: String?,
    onConsumeNotificationDate: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onLaunchShareIntent: (Intent) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var detailDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var aiExportDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNoteRecording by remember { mutableStateOf<RecordingEntity?>(null) }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
    }

    val requestMicAndNotificationPermissions = {
        val toRequest = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        multiPermissionLauncher.launch(toRequest)
    }

    val requestNotificationOnly = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            multiPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareIntentEvents.collect { shareIntent ->
            onLaunchShareIntent(shareIntent)
        }
    }

    LaunchedEffect(openExportDateFromNotification) {
        if (!openExportDateFromNotification.isNullOrBlank()) {
            aiExportDateIso = openExportDateFromNotification
            onConsumeNotificationDate()
        }
    }

    val activeDetailDate = detailDateIso
    if (activeDetailDate != null) {
        DailySummaryDetailScreen(
            dateIso = activeDetailDate,
            uiState = uiState,
            onBack = { detailDateIso = null },
            onOpenAiExport = { aiExportDateIso = activeDetailDate },
            onCopyMarkdownReport = { viewModel.copyMarkdownReportToClipboard(activeDetailDate) },
            onTogglePlayRecording = { rec ->
                viewModel.playerController.togglePlay(
                    rec.id,
                    rec.filePath,
                    rec.courseName.ifBlank { rec.periodName }
                )
            },
            onEditRecordingNotes = { editingNoteRecording = it },
            onShareRecording = { viewModel.shareSingleRecording(it) },
            onDeleteRecording = { viewModel.deleteRecording(it) }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column {
                    AudioPlayerBar(
                        playbackState = uiState.playbackState,
                        onTogglePlayPause = {
                            if (uiState.playbackState.isPlaying) {
                                viewModel.playerController.pause()
                            } else {
                                viewModel.playerController.resume()
                            }
                        },
                        onSeek = { viewModel.playerController.seekTo(it) },
                        onCycleSpeed = { viewModel.playerController.cycleSpeed() },
                        onClose = { viewModel.playerController.stop() }
                    )
                    NavigationBar {
                        AppTab.entries.forEachIndexed { idx, tab ->
                            NavigationBarItem(
                                selected = (selectedTabIndex == idx),
                                onClick = { selectedTabIndex = idx },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val mod = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (AppTab.entries[selectedTabIndex]) {
                AppTab.TODAY -> TodayScreen(
                    uiState = uiState,
                    permissionStatus = permissionStatus,
                    onArmSchedule = { viewModel.armSchedule(autoStartIfInActiveSlot = true) },
                    onDisarmSchedule = { viewModel.disarmSchedule() },
                    onStartManual = { label -> viewModel.startManualRecording(label) },
                    onStartSlotNow = { slot -> viewModel.startScheduledSlotNow(slot) },
                    onTogglePauseResume = { viewModel.togglePauseResume() },
                    onStopRecording = { viewModel.stopRecording() },
                    onToggleSkipToday = { periodId, currentlySkipped ->
                        viewModel.toggleSkipToday(periodId, currentlySkipped)
                    },
                    onTogglePlayRecording = { rec ->
                        viewModel.playerController.togglePlay(
                            rec.id,
                            rec.filePath,
                            rec.courseName.ifBlank { rec.periodName }
                        )
                    },
                    onEditRecordingNotes = { editingNoteRecording = it },
                    onShareRecording = { viewModel.shareSingleRecording(it) },
                    onDeleteRecording = { viewModel.deleteRecording(it) },
                    onOpenAiExport = { aiExportDateIso = uiState.todayDateIso },
                    onGoToTimetable = { selectedTabIndex = AppTab.TIMETABLE.ordinal },
                    onGoToSettings = { selectedTabIndex = AppTab.SETTINGS.ordinal },
                    onRequestMicAndNotifyPermissions = requestMicAndNotificationPermissions,
                    modifier = mod
                )

                AppTab.TIMETABLE -> TimetableScreen(
                    uiState = uiState,
                    onSaveTimetableSlot = { dow, periodId, enabled, courseName, classroom, colorTag ->
                        viewModel.saveTimetableSlot(dow, periodId, enabled, courseName, classroom, colorTag)
                    },
                    onApplySampleMorning34 = { viewModel.applySampleMorning34Schedule() },
                    onSavePeriod = { viewModel.savePeriod(it) },
                    onDeletePeriod = { viewModel.deletePeriod(it) },
                    onApplyUniversityPreset = { viewModel.applyUniversityPeriodPreset() },
                    onApplyHighSchoolPreset = { viewModel.applyHighSchoolPeriodPreset() },
                    modifier = mod
                )

                AppTab.ARCHIVE -> ArchiveScreen(
                    uiState = uiState,
                    onSelectDate = { dateIso -> detailDateIso = dateIso },
                    modifier = mod
                )

                AppTab.SETTINGS -> SettingsScreen(
                    uiState = uiState,
                    permissionStatus = permissionStatus,
                    onRequestRecordAudio = requestMicAndNotificationPermissions,
                    onRequestNotifications = requestNotificationOnly,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                    onSelectAudioQuality = { viewModel.updateAudioQuality(it) },
                    onUpdateBuffers = { s, e -> viewModel.updateBuffers(s, e) },
                    onToggleShowWeekends = { viewModel.updateShowWeekends(it) },
                    onToggleKeepArmedOvernight = { viewModel.updateKeepArmedOvernight(it) },
                    onUpdatePromptTemplate = { viewModel.updatePromptTemplate(it) },
                    modifier = mod
                )
            }
        }
    }

    editingNoteRecording?.let { rec ->
        NoteEditorDialog(
            recording = rec,
            onSave = { classNote, hwNote, aiSummary ->
                viewModel.saveRecordingNotes(rec.id, classNote, hwNote, aiSummary)
            },
            onDismiss = { editingNoteRecording = null }
        )
    }

    aiExportDateIso?.let { dateIso ->
        val dayGroup = uiState.dayGroups.firstOrNull { it.dateIso == dateIso }
        val recordingsCount = dayGroup?.recordings?.count { it.status == com.classrecorder.app.data.db.RecordingStatus.COMPLETED }
            ?: uiState.todayRecordings.count { it.dateIso == dateIso && it.status == com.classrecorder.app.data.db.RecordingStatus.COMPLETED }
        val existingSummary = dayGroup?.summary ?: if (dateIso == uiState.todayDateIso) uiState.todaySummary else null
        val promptPreview = viewModel.buildAiPromptForDate(dateIso)

        AiExportSheet(
            dateIso = dateIso,
            recordingCount = recordingsCount,
            generatedPromptPreview = promptPreview,
            initialSummaryTitle = existingSummary?.title ?: "",
            initialSummaryText = existingSummary?.summaryText ?: "",
            onCopyPrompt = { viewModel.copyAiPromptToClipboard(dateIso) },
            onShareAllAudioAndPrompt = { viewModel.shareAllAudioForDate(dateIso) },
            onShareZipBundle = { viewModel.shareDailyZipBundle(dateIso) },
            onExportToDownloads = { viewModel.exportDateToDownloads(dateIso) },
            onSaveSummary = { title, text -> viewModel.saveDailySummary(dateIso, title, text) },
            onDismiss = { aiExportDateIso = null }
        )
    }
}

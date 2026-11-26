package com.classrecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.data.db.RecordingStatus
import com.classrecorder.app.domain.AiExportFormatter
import com.classrecorder.app.domain.FilenameHelper
import com.classrecorder.app.domain.ScheduledSlotInstance
import com.classrecorder.app.service.RecorderPhase
import com.classrecorder.app.ui.MainUiState
import com.classrecorder.app.ui.PermissionStatus
import com.classrecorder.app.ui.components.LiveAudioMeter
import com.classrecorder.app.ui.theme.CourseTagColors
import java.time.ZonedDateTime

@Composable
fun TodayScreen(
    uiState: MainUiState,
    permissionStatus: PermissionStatus,
    onArmSchedule: () -> Unit,
    onDisarmSchedule: () -> Unit,
    onStartManual: (String) -> Unit,
    onStartSlotNow: (ScheduledSlotInstance) -> Unit,
    onTogglePauseResume: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleSkipToday: (Long, Boolean) -> Unit,
    onTogglePlayRecording: (RecordingEntity) -> Unit,
    onEditRecordingNotes: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit,
    onDeleteRecording: (RecordingEntity) -> Unit,
    onOpenAiExport: () -> Unit,
    onGoToTimetable: () -> Unit,
    onGoToSettings: () -> Unit,
    onRequestMicAndNotifyPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualDialog by remember { mutableStateOf(false) }
    var pendingDeleteRecording by remember { mutableStateOf<RecordingEntity?>(null) }

    val completedTodayCount = uiState.todayRecordings.count { it.status == RecordingStatus.COMPLETED }
    val manualTodayRecordings = uiState.todayRecordings.filter { it.periodId == null }
    val recordingsByPeriodId = uiState.todayRecordings
        .filter { it.periodId != null }
        .groupBy { it.periodId!! }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${uiState.todayDateIso} · ${FilenameHelper.dayOfWeekLabel(uiState.todayDayOfWeek)}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "今日排程 ${uiState.todayScheduledSlots.count { !it.isSkipped }} 節 · 已完成錄音 $completedTodayCount 段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!permissionStatus.allCriticalReady || !permissionStatus.hasExactAlarm) {
            item {
                PermissionWarningBanner(
                    permissionStatus = permissionStatus,
                    onRequestBasic = onRequestMicAndNotifyPermissions,
                    onOpenSettings = onGoToSettings
                )
            }
        }

        item {
            RecorderStatusHeroCard(
                uiState = uiState,
                hasMicPermission = permissionStatus.hasRecordAudio,
                onRequestPermission = onRequestMicAndNotifyPermissions,
                onArmSchedule = onArmSchedule,
                onDisarmSchedule = onDisarmSchedule,
                onOpenManualDialog = { showManualDialog = true },
                onStartSlotNow = onStartSlotNow,
                onTogglePauseResume = onTogglePauseResume,
                onStopRecording = onStopRecording
            )
        }

        item {
            AfterSchoolSummaryCard(
                completedCount = completedTodayCount,
                summaryTitle = uiState.todaySummary?.title ?: "",
                summaryText = uiState.todaySummary?.summaryText ?: "",
                onOpenExport = onOpenAiExport
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日課表與錄音時間軸",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onGoToTimetable) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("調整課表")
                }
            }
        }

        if (uiState.todayScheduledSlots.isEmpty() && manualTodayRecordings.isEmpty()) {
            item {
                EmptyTodayScheduleCard(onGoToTimetable = onGoToTimetable)
            }
        }

        items(
            items = uiState.todayScheduledSlots,
            key = { "slot_${it.period.id}" }
        ) { slotInstance ->
            val slotRecordings = recordingsByPeriodId[slotInstance.period.id].orEmpty()
            ScheduledSlotTimelineCard(
                slotInstance = slotInstance,
                recordings = slotRecordings,
                activeRecordingId = uiState.serviceState.activeRecordingId,
                playingRecordingId = uiState.playbackState.activeRecordingId,
                isAudioPlaying = uiState.playbackState.isPlaying,
                onToggleSkip = {
                    onToggleSkipToday(slotInstance.period.id, slotInstance.isSkipped)
                },
                onStartSlotNow = { onStartSlotNow(slotInstance) },
                onTogglePlay = onTogglePlayRecording,
                onEditNotes = onEditRecordingNotes,
                onShare = onShareRecording,
                onDelete = { pendingDeleteRecording = it }
            )
        }

        if (manualTodayRecordings.isNotEmpty()) {
            item {
                Text(
                    text = "今日手動／額外錄音",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = manualTodayRecordings,
                key = { "manual_${it.id}" }
            ) { rec ->
                RecordingItemCard(
                    recording = rec,
                    isCurrentlyRecording = (rec.id == uiState.serviceState.activeRecordingId),
                    isPlaying = (rec.id == uiState.playbackState.activeRecordingId && uiState.playbackState.isPlaying),
                    onTogglePlay = { onTogglePlayRecording(rec) },
                    onEditNotes = { onEditRecordingNotes(rec) },
                    onShare = { onShareRecording(rec) },
                    onDelete = { pendingDeleteRecording = rec }
                )
            }
        }
    }

    if (showManualDialog) {
        ManualRecordDialog(
            onStart = { label ->
                showManualDialog = false
                onStartManual(label)
            },
            onDismiss = { showManualDialog = false }
        )
    }

    pendingDeleteRecording?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRecording = null },
            title = { Text("刪除這段錄音？") },
            text = { Text("確定要刪除「${rec.courseName.ifBlank { rec.periodName }}」(${rec.fileName}) 嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteRecording(rec)
                        pendingDeleteRecording = null
                    }
                ) {
                    Text("刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRecording = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PermissionWarningBanner(
    permissionStatus: PermissionStatus,
    onRequestBasic: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!permissionStatus.allCriticalReady) {
                        "需要麥克風與通知權限才能自動錄音"
                    } else {
                        "建議開啟「精準鬧鐘」權限以確保準時開錄"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "設定好權限後，系統就能依照課表自動切換錄音時段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = if (!permissionStatus.allCriticalReady) onRequestBasic else onOpenSettings
            ) {
                Text(
                    text = if (!permissionStatus.allCriticalReady) "立即授權" else "去設定",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RecorderStatusHeroCard(
    uiState: MainUiState,
    hasMicPermission: Boolean,
    onRequestPermission: () -> Unit,
    onArmSchedule: () -> Unit,
    onDisarmSchedule: () -> Unit,
    onOpenManualDialog: () -> Unit,
    onStartSlotNow: (ScheduledSlotInstance) -> Unit,
    onTogglePauseResume: () -> Unit,
    onStopRecording: () -> Unit
) {
    val st = uiState.serviceState
    val isLive = st.phase == RecorderPhase.RECORDING || st.phase == RecorderPhase.PAUSED
    val isArmed = st.phase == RecorderPhase.ARMED

    val containerColor = when {
        isLive -> MaterialTheme.colorScheme.errorContainer
        isArmed -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val contentColor = when {
        isLive -> MaterialTheme.colorScheme.onErrorContainer
        isArmed -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (st.phase == RecorderPhase.PAUSED) "錄音已暫停" else "正在錄音中",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                    LiveAudioMeter(
                        amplitude = st.normalizedAmplitude,
                        isPaused = (st.phase == RecorderPhase.PAUSED),
                        barColor = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = st.activeTitle.ifBlank { "課堂錄音" },
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = st.activeTimeRange,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                    Text(
                        text = FilenameHelper.formatDuration(st.elapsedMs),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onTogglePauseResume,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (st.phase == RecorderPhase.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (st.phase == RecorderPhase.PAUSED) "繼續" else "暫停")
                    }
                    Button(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止並儲存")
                    }
                }
            } else if (isArmed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "排程自動守候中（可鎖定螢幕）",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                }
                Text(
                    text = st.nextSlotSummary.ifBlank { "等待下一個課表時段自動開錄" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { if (hasMicPermission) onOpenManualDialog() else onRequestPermission() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("手動立即錄音")
                    }
                    OutlinedButton(
                        onClick = onDisarmSchedule,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("停止守候")
                    }
                }
            } else {
                val activeNow = uiState.activeSlotNow
                val nextSlot = uiState.nextUpcomingSlot
                Text(
                    text = "自動排程尚未啟動守候",
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                val desc = when {
                    activeNow != null -> "現在正在 ${activeNow.displayTitle}（${activeNow.timeRangeText}）時段內，點擊下方按鈕即可開錄並守候後續課程。"
                    nextSlot != null -> {
                        val dayPrefix = if (nextSlot.dateIso == uiState.todayDateIso) "今天" else "${nextSlot.date.monthValue}/${nextSlot.date.dayOfMonth}"
                        "下一段排程：$dayPrefix ${nextSlot.timeRangeText} · ${nextSlot.displayTitle}"
                    }
                    else -> "尚未設定上課節次，請先至「課表」勾選要錄音的時段，或使用手動錄音。"
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f)
                )

                if (activeNow != null) {
                    Button(
                        onClick = { if (hasMicPermission) onStartSlotNow(activeNow) else onRequestPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即補錄「${activeNow.displayTitle}」並接續排程")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { if (hasMicPermission) onArmSchedule() else onRequestPermission() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("開啟排程守候")
                    }
                    FilledTonalButton(
                        onClick = { if (hasMicPermission) onOpenManualDialog() else onRequestPermission() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("手動錄音")
                    }
                }
            }
        }
    }
}

@Composable
private fun AfterSchoolSummaryCard(
    completedCount: Int,
    summaryTitle: String,
    summaryText: String,
    onOpenExport: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (summaryTitle.isNotBlank()) "今日總結 · $summaryTitle" else "放學後 · 今日總結與 AI 匯出",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (summaryText.isNotBlank()) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = if (completedCount > 0) {
                        "今天已完成 $completedCount 段錄音。點下方按鈕可一鍵複製 AI 提示詞並分享全部音檔給 Gemini / ChatGPT 總結，或記錄今天的心得。"
                    } else {
                        "錄完今天的課程後，可在此打包分享所有錄音給 AI 幫你整理今日重點、時間軸與作業提醒。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onOpenExport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (summaryText.isNotBlank()) "編輯總結 ／ 匯出給 AI" else "一鍵匯出給 AI 總結 ／ 寫今日總結")
            }
        }
    }
}

@Composable
private fun EmptyTodayScheduleCard(onGoToTimetable: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "今天還沒有排定自動錄音的課堂",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "到「課表」頁面點選今天有課的節次（例如早上第 3 節 10:10~11:00、第 4 節 11:10~12:00），時間到就會自動分段錄音。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onGoToTimetable,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("前往設定週課表")
            }
        }
    }
}

@Composable
private fun ScheduledSlotTimelineCard(
    slotInstance: ScheduledSlotInstance,
    recordings: List<RecordingEntity>,
    activeRecordingId: Long?,
    playingRecordingId: Long?,
    isAudioPlaying: Boolean,
    onToggleSkip: () -> Unit,
    onStartSlotNow: () -> Unit,
    onTogglePlay: (RecordingEntity) -> Unit,
    onEditNotes: (RecordingEntity) -> Unit,
    onShare: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit
) {
    val now = ZonedDateTime.now()
    val isPast = !now.isBefore(slotInstance.effectiveEnd)
    val isCurrent = !now.isBefore(slotInstance.effectiveStart) && now.isBefore(slotInstance.effectiveEnd)
    val tagColor = CourseTagColors[slotInstance.slot.colorTag.mod(CourseTagColors.size)]
    val isLiveNow = recordings.any { it.id == activeRecordingId }
    val hasCompleted = recordings.any { it.status == RecordingStatus.COMPLETED }

    val statusText = when {
        isLiveNow -> "錄音中"
        slotInstance.isSkipped -> "今日跳過"
        hasCompleted -> "已錄音 (${recordings.count { it.status == RecordingStatus.COMPLETED }} 段)"
        isCurrent -> "上課時段中"
        isPast -> "時段已結束"
        else -> "待自動錄音"
    }

    val statusBg = when {
        isLiveNow -> MaterialTheme.colorScheme.errorContainer
        hasCompleted -> MaterialTheme.colorScheme.secondaryContainer
        slotInstance.isSkipped -> MaterialTheme.colorScheme.surfaceVariant
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isLiveNow || isCurrent) 1.5.dp else 1.dp,
            color = if (isLiveNow) MaterialTheme.colorScheme.error else if (isCurrent) tagColor else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (slotInstance.isSkipped) MaterialTheme.colorScheme.outline else tagColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${slotInstance.period.name} · ${slotInstance.timeRangeText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = slotInstance.displayTitle + if (slotInstance.slot.classroom.isNotBlank()) "（${slotInstance.slot.classroom}）" else "",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (!isPast && !isLiveNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCurrent && !slotInstance.isSkipped) {
                        FilledTonalButton(
                            onClick = onStartSlotNow,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("立即開始錄音")
                        }
                    }
                    OutlinedButton(
                        onClick = onToggleSkip,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (slotInstance.isSkipped) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (slotInstance.isSkipped) "恢復今日排程" else "今日跳過")
                    }
                }
            }

            recordings.forEach { rec ->
                RecordingInlineRow(
                    recording = rec,
                    isCurrentlyRecording = (rec.id == activeRecordingId),
                    isPlaying = (rec.id == playingRecordingId && isAudioPlaying),
                    onTogglePlay = { onTogglePlay(rec) },
                    onEditNotes = { onEditNotes(rec) },
                    onShare = { onShare(rec) },
                    onDelete = { onDelete(rec) }
                )
            }
        }
    }
}

@Composable
fun RecordingItemCard(
    recording: RecordingEntity,
    isCurrentlyRecording: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onEditNotes: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${recording.periodName} · ${AiExportFormatter.formatRecordingTimeRange(recording)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = recording.courseName.ifBlank { recording.periodName },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            RecordingInlineRow(
                recording = recording,
                isCurrentlyRecording = isCurrentlyRecording,
                isPlaying = isPlaying,
                onTogglePlay = onTogglePlay,
                onEditNotes = onEditNotes,
                onShare = onShare,
                onDelete = onDelete
            )
        }
    }
}

@Composable
fun RecordingInlineRow(
    recording: RecordingEntity,
    isCurrentlyRecording: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onEditNotes: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCurrentlyRecording && recording.status == RecordingStatus.COMPLETED) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暫停" else "播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val info = when {
                        isCurrentlyRecording -> "正在錄音中…"
                        recording.status == RecordingStatus.FAILED -> "錄音失敗或檔案過短"
                        else -> "${FilenameHelper.formatDuration(recording.durationMs)} · ${FilenameHelper.formatFileSize(recording.fileSizeBytes)}"
                    }
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = recording.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEditNotes, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = "課堂筆記",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (!isCurrentlyRecording && recording.status == RecordingStatus.COMPLETED) {
                    IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "分享音檔"
                        )
                    }
                }
                if (!isCurrentlyRecording) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "刪除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (recording.classNote.isNotBlank() || recording.homeworkNote.isNotBlank() || recording.aiTranscriptOrSummary.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (recording.classNote.isNotBlank()) {
                        Text(
                            text = "筆記：${recording.classNote}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (recording.homeworkNote.isNotBlank()) {
                        Text(
                            text = "作業／提醒：${recording.homeworkNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (recording.aiTranscriptOrSummary.isNotBlank()) {
                        Text(
                            text = "單節摘要：${recording.aiTranscriptOrSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualRecordDialog(
    onStart: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手動立即錄音") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("可輸入這段錄音的名稱（例如：社團開會、補課、班會），留空則預設為「手動錄音」。")
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("事件／課程名稱（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onStart(label) }) {
                Text("開始錄音")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

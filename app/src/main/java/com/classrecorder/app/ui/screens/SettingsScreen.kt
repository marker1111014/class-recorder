package com.classrecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classrecorder.app.data.prefs.AudioQualityPreset
import com.classrecorder.app.data.prefs.UserSettings
import com.classrecorder.app.domain.FilenameHelper
import com.classrecorder.app.ui.MainUiState
import com.classrecorder.app.ui.PermissionStatus
import com.classrecorder.app.ui.components.PermissionChecklistCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    permissionStatus: PermissionStatus,
    onRequestRecordAudio: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onSelectAudioQuality: (AudioQualityPreset) -> Unit,
    onUpdateBuffers: (startSec: Int, endSec: Int) -> Unit,
    onToggleShowWeekends: (Boolean) -> Unit,
    onToggleKeepArmedOvernight: (Boolean) -> Unit,
    onUpdatePromptTemplate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPromptDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "偏好與系統設定",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            PermissionChecklistCard(
                status = permissionStatus,
                onRequestRecordAudio = onRequestRecordAudio,
                onRequestNotifications = onRequestNotifications,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("錄音格式與音質（.m4a / AAC）", style = MaterialTheme.typography.titleMedium)
                    AudioQualityPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAudioQuality(preset) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (uiState.settings.audioQuality == preset),
                                onClick = { onSelectAudioQuality(preset) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(preset.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("上下課前後緩衝時間", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "若老師常在打鐘前開口或拖堂，可設定自動提前開始或延後停止。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("提前開錄", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = (uiState.settings.startBufferSeconds == sec),
                                onClick = { onUpdateBuffers(sec, uiState.settings.endBufferSeconds) },
                                label = { Text(if (sec == 0) "準時" else "提早 ${sec} 秒") }
                            )
                        }
                    }

                    Text("延後停止", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 30, 60, 120).forEach { sec ->
                            FilterChip(
                                selected = (uiState.settings.endBufferSeconds == sec),
                                onClick = { onUpdateBuffers(uiState.settings.startBufferSeconds, sec) },
                                label = { Text(if (sec == 0) "準時" else "延後 ${sec} 秒") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("課表與排程行為", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("顯示週六與週日課表", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "適合週末補習班、研討會或社團活動排程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.settings.showWeekends,
                            onCheckedChange = onToggleShowWeekends
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("放學後跨夜維持待機守候", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "關閉時（推薦）：今日最後一節課結束後自動卸除待機並通知整理總結；開啟時：隔天早上免再開 App 即可自動開錄。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.settings.keepArmedOvernight,
                            onCheckedChange = onToggleKeepArmedOvernight
                        )
                    }
                }
            }
        }

        item {
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
                    Text("AI 總結提示詞範本", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = uiState.settings.aiPromptTemplate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4
                    )
                    OutlinedButton(
                        onClick = { showPromptDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("自訂 AI 提示詞範本")
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("本機儲存空間", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "目前已使用 ${FilenameHelper.formatFileSize(uiState.totalStorageBytes)}（所有錄音皆存於本機私有空間，未經您的匯出不會上傳至任何伺服器）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPromptDialog) {
        PromptEditorDialog(
            currentTemplate = uiState.settings.aiPromptTemplate,
            onSave = {
                onUpdatePromptTemplate(it)
                showPromptDialog = false
            },
            onDismiss = { showPromptDialog = false }
        )
    }
}

@Composable
private fun PromptEditorDialog(
    currentTemplate: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(currentTemplate) { mutableStateOf(currentTemplate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯 AI 總結提示詞範本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { text = UserSettings.DEFAULT_AI_PROMPT_TEMPLATE }) {
                    Text("恢復預設範本")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

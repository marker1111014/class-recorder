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
import androidx.compose.ui.graphics.RectangleShape
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "設定",
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
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("錄音音質（.m4a）", style = MaterialTheme.typography.titleMedium)
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
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("緩衝時間", style = MaterialTheme.typography.titleMedium)

                    Text("提前開錄", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = (uiState.settings.startBufferSeconds == sec),
                                onClick = { onUpdateBuffers(sec, uiState.settings.endBufferSeconds) },
                                shape = RectangleShape,
                                label = { Text(if (sec == 0) "準時" else "${sec}秒") }
                            )
                        }
                    }

                    Text("延後停止", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 30, 60, 120).forEach { sec ->
                            FilterChip(
                                selected = (uiState.settings.endBufferSeconds == sec),
                                onClick = { onUpdateBuffers(uiState.settings.startBufferSeconds, sec) },
                                shape = RectangleShape,
                                label = { Text(if (sec == 0) "準時" else "${sec}秒") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("排程選項", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("顯示週六日", style = MaterialTheme.typography.bodyMedium)
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
                            Text("跨夜維持守候", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "關閉：放學後結束待機；開啟：隔日自動開錄",
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
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("AI 提示詞範本", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = uiState.settings.aiPromptTemplate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                    OutlinedButton(
                        onClick = { showPromptDialog = true },
                        shape = RectangleShape
                    ) {
                        Text("編輯範本")
                    }
                }
            }
        }

        item {
            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("本機空間", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "已使用 ${FilenameHelper.formatFileSize(uiState.totalStorageBytes)}",
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
        shape = RectangleShape,
        title = { Text("提示詞範本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 6,
                    maxLines = 12,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { text = UserSettings.DEFAULT_AI_PROMPT_TEMPLATE },
                    shape = RectangleShape
                ) {
                    Text("恢復預設")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, shape = RectangleShape) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RectangleShape) {
                Text("取消")
            }
        }
    )
}

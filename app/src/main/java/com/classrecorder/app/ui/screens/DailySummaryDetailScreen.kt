package com.classrecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.domain.FilenameHelper
import com.classrecorder.app.ui.DayArchiveGroup
import com.classrecorder.app.ui.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryDetailScreen(
    dateIso: String,
    uiState: MainUiState,
    onBack: () -> Unit,
    onOpenAiExport: () -> Unit,
    onCopyMarkdownReport: () -> Unit,
    onTogglePlayRecording: (RecordingEntity) -> Unit,
    onEditRecordingNotes: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit,
    onDeleteRecording: (RecordingEntity) -> Unit
) {
    val group: DayArchiveGroup? = uiState.dayGroups.firstOrNull { it.dateIso == dateIso }
    val recordings = group?.recordings ?: emptyList()
    val summary = group?.summary
    val dow = group?.dayOfWeek ?: 1
    var pendingDelete by remember { mutableStateOf<RecordingEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$dateIso · ${FilenameHelper.dayOfWeekLabel(dow)}")
                        Text(
                            text = "共 ${recordings.size} 段錄音",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCopyMarkdownReport) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "複製 Markdown 報告")
                    }
                    IconButton(onClick = onOpenAiExport) {
                        Icon(Icons.Rounded.Share, contentDescription = "匯出給 AI")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = summary?.title?.takeIf { it.isNotBlank() } ?: "今日總結",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TextButton(onClick = onOpenAiExport) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("編輯／匯出")
                            }
                        }

                        if (summary != null && summary.summaryText.isNotBlank()) {
                            Text(
                                text = summary.summaryText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "尚未撰寫這天的總結。可以匯出本日全部音檔給 AI 整理後，把總結貼回這裡永久保存。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = onOpenAiExport,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("匯出給 AI 總結")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "當日時間軸",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(
                items = recordings,
                key = { it.id }
            ) { rec ->
                RecordingItemCard(
                    recording = rec,
                    isCurrentlyRecording = (rec.id == uiState.serviceState.activeRecordingId),
                    isPlaying = (rec.id == uiState.playbackState.activeRecordingId && uiState.playbackState.isPlaying),
                    onTogglePlay = { onTogglePlayRecording(rec) },
                    onEditNotes = { onEditRecordingNotes(rec) },
                    onShare = { onShareRecording(rec) },
                    onDelete = { pendingDelete = rec }
                )
            }
        }
    }

    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("刪除這段錄音？") },
            text = { Text("確定要刪除「${rec.courseName.ifBlank { rec.periodName }}」(${rec.fileName}) 嗎？") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteRecording(rec)
                        pendingDelete = null
                    }
                ) {
                    Text("刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

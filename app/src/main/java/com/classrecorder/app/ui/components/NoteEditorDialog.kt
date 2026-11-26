package com.classrecorder.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.domain.AiExportFormatter

@Composable
fun NoteEditorDialog(
    recording: RecordingEntity,
    onSave: (classNote: String, homeworkNote: String, aiSummary: String) -> Unit,
    onDismiss: () -> Unit
) {
    var classNote by remember(recording.id) { mutableStateOf(recording.classNote) }
    var homeworkNote by remember(recording.id) { mutableStateOf(recording.homeworkNote) }
    var aiSummary by remember(recording.id) { mutableStateOf(recording.aiTranscriptOrSummary) }

    val title = recording.courseName.ifBlank { recording.periodName }
    val timeRange = AiExportFormatter.formatRecordingTimeRange(recording)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "$title · 課堂筆記")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "${recording.dateIso} · $timeRange（${recording.periodName}）")
                OutlinedTextField(
                    value = classNote,
                    onValueChange = { classNote = it },
                    label = { Text("上課重點 / 發生了什麼事") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeworkNote,
                    onValueChange = { homeworkNote = it },
                    label = { Text("作業 / 考試 / 老師交代事項") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = aiSummary,
                    onValueChange = { aiSummary = it },
                    label = { Text("單節 AI 摘要 / 逐字重點（選填）") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(classNote, homeworkNote, aiSummary)
                onDismiss()
            }) {
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

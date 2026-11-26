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
import androidx.compose.ui.graphics.RectangleShape
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
        shape = RectangleShape,
        title = {
            Text(text = "$title · 筆記")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "${recording.dateIso} · $timeRange")
                OutlinedTextField(
                    value = classNote,
                    onValueChange = { classNote = it },
                    label = { Text("課程重點") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeworkNote,
                    onValueChange = { homeworkNote = it },
                    label = { Text("作業／提醒") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = aiSummary,
                    onValueChange = { aiSummary = it },
                    label = { Text("單節摘要（選填）") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(classNote, homeworkNote, aiSummary)
                onDismiss()
            }, shape = RectangleShape) {
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

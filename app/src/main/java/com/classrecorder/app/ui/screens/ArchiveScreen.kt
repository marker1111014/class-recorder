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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classrecorder.app.data.db.RecordingStatus
import com.classrecorder.app.domain.FilenameHelper
import com.classrecorder.app.ui.DayArchiveGroup
import com.classrecorder.app.ui.MainUiState

@Composable
fun ArchiveScreen(
    uiState: MainUiState,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(uiState.dayGroups, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            uiState.dayGroups
        } else {
            uiState.dayGroups.filter { group ->
                group.dateIso.contains(q) ||
                    (group.summary?.title?.lowercase()?.contains(q) == true) ||
                    (group.summary?.summaryText?.lowercase()?.contains(q) == true) ||
                    group.recordings.any { rec ->
                        rec.courseName.lowercase().contains(q) ||
                            rec.periodName.lowercase().contains(q) ||
                            rec.classNote.lowercase().contains(q) ||
                            rec.homeworkNote.lowercase().contains(q) ||
                            rec.aiTranscriptOrSummary.lowercase().contains(q)
                    }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "紀錄與總結",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜尋課名、筆記或總結") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filteredGroups.isEmpty()) {
            item {
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "尚無紀錄" else "查無符合資料",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        items(
            items = filteredGroups,
            key = { it.dateIso }
        ) { group ->
            DayGroupCard(
                group = group,
                isToday = (group.dateIso == uiState.todayDateIso),
                onClick = { onSelectDate(group.dateIso) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayGroupCard(
    group: DayArchiveGroup,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val completedCount = group.recordings.count { it.status == RecordingStatus.COMPLETED }
    val courseLabels = group.recordings
        .map { it.courseName.ifBlank { it.periodName } }
        .distinct()
        .take(5)

    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${group.dateIso} · ${FilenameHelper.dayOfWeekLabel(group.dayOfWeek)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isToday) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RectangleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "今日",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "$completedCount 段 · ${FilenameHelper.formatDuration(group.totalDurationMs)} · ${FilenameHelper.formatFileSize(group.totalSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (courseLabels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    courseLabels.forEach { label ->
                        AssistChip(
                            onClick = onClick,
                            shape = RectangleShape,
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }

            if (group.summary != null && group.summary.summaryText.isNotBlank()) {
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (group.summary.title.isNotBlank()) {
                            Text(
                                text = group.summary.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = group.summary.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

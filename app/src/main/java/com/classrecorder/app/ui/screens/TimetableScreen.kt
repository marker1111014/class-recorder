package com.classrecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classrecorder.app.data.db.PeriodDefinitionEntity
import com.classrecorder.app.data.db.TimetableSlotEntity
import com.classrecorder.app.domain.FilenameHelper
import com.classrecorder.app.domain.ScheduleCalculator
import com.classrecorder.app.ui.MainUiState
import com.classrecorder.app.ui.theme.CourseTagColors

@Composable
fun TimetableScreen(
    uiState: MainUiState,
    onSaveTimetableSlot: (dayOfWeek: Int, periodId: Long, enabled: Boolean, courseName: String, classroom: String, colorTag: Int) -> Unit,
    onApplySampleMorning34: () -> Unit,
    onSavePeriod: (PeriodDefinitionEntity) -> Unit,
    onDeletePeriod: (PeriodDefinitionEntity) -> Unit,
    onApplyUniversityPreset: () -> Unit,
    onApplyHighSchoolPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCell by remember { mutableStateOf<Pair<Int, PeriodDefinitionEntity>?>(null) }
    var showPeriodManager by remember { mutableStateOf(false) }

    val daysToShow = if (uiState.settings.showWeekends) (1..7).toList() else (1..5).toList()
    val slotMap = remember(uiState.timetableSlots) {
        uiState.timetableSlots.associateBy { it.dayOfWeek to it.periodId }
    }
    val hasAnyEnabledSlot = uiState.timetableSlots.any { it.enabled }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "每週課表排程",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "點選格子即可勾選錄音並填寫課名；第 3 節預設 10:10~11:00、第 4 節 11:10~12:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { showPeriodManager = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("編輯節次")
                }
            }
        }

        if (!hasAnyEnabledSlot) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "想快速試用看看？",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "可一鍵套用「早上第 3、4 節（10:10~11:00、11:10~12:00）」範例課表，再自由調整。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onApplySampleMorning34) {
                            Text("套用範例", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Weekday column headers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(66.dp)) {
                    Text(
                        text = "節次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                daysToShow.forEach { dow ->
                    val isToday = (dow == uiState.todayDayOfWeek)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = FilenameHelper.dayOfWeekShortLabel(dow),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        items(
            items = uiState.periods,
            key = { it.id }
        ) { period ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .width(66.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPeriodManager = true }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = period.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FilenameHelper.formatMinuteOfDay(period.startMinuteOfDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FilenameHelper.formatMinuteOfDay(period.endMinuteOfDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                daysToShow.forEach { dow ->
                    val slot = slotMap[dow to period.id]
                    val enabled = slot?.enabled == true
                    val tagColor = CourseTagColors[(slot?.colorTag ?: 0).mod(CourseTagColors.size)]
                    val cellBg = if (enabled) {
                        tagColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val borderColor = if (enabled) tagColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cellBg,
                        border = BorderStroke(if (enabled) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { editingCell = dow to period }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (enabled) {
                                val label = slot?.courseName?.takeIf { it.isNotBlank() } ?: "錄音"
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tagColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingCell?.let { (dow, period) ->
        val existingSlot = slotMap[dow to period.id]
        EditTimetableCellDialog(
            dayOfWeek = dow,
            period = period,
            existingSlot = existingSlot,
            onSave = { enabled, courseName, classroom, colorTag ->
                onSaveTimetableSlot(dow, period.id, enabled, courseName, classroom, colorTag)
                editingCell = null
            },
            onDismiss = { editingCell = null }
        )
    }

    if (showPeriodManager) {
        PeriodManagerDialog(
            periods = uiState.periods,
            onSavePeriod = onSavePeriod,
            onDeletePeriod = onDeletePeriod,
            onApplyUniversityPreset = onApplyUniversityPreset,
            onApplyHighSchoolPreset = onApplyHighSchoolPreset,
            onDismiss = { showPeriodManager = false }
        )
    }
}

@Composable
private fun EditTimetableCellDialog(
    dayOfWeek: Int,
    period: PeriodDefinitionEntity,
    existingSlot: TimetableSlotEntity?,
    onSave: (enabled: Boolean, courseName: String, classroom: String, colorTag: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember(existingSlot) { mutableStateOf(existingSlot?.enabled ?: true) }
    var courseName by remember(existingSlot) { mutableStateOf(existingSlot?.courseName ?: "") }
    var classroom by remember(existingSlot) { mutableStateOf(existingSlot?.classroom ?: "") }
    var colorTag by remember(existingSlot) { mutableIntStateOf(existingSlot?.colorTag ?: ((dayOfWeek + period.orderIndex) % CourseTagColors.size)) }

    val timeRange = "${FilenameHelper.formatMinuteOfDay(period.startMinuteOfDay)}~${FilenameHelper.formatMinuteOfDay(period.endMinuteOfDay)}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${FilenameHelper.dayOfWeekLabel(dayOfWeek)} ${period.name}（$timeRange）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("啟用此時段自動錄音", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("課程／事件名稱（例如：微積分）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = classroom,
                    onValueChange = { classroom = it },
                    label = { Text("教室／老師備註（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("課表標籤顏色", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CourseTagColors.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { colorTag = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorTag == idx) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(enabled, courseName, classroom, colorTag) }) {
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

@Composable
private fun PeriodManagerDialog(
    periods: List<PeriodDefinitionEntity>,
    onSavePeriod: (PeriodDefinitionEntity) -> Unit,
    onDeletePeriod: (PeriodDefinitionEntity) -> Unit,
    onApplyUniversityPreset: () -> Unit,
    onApplyHighSchoolPreset: () -> Unit,
    onDismiss: () -> Unit
) {
    var editingPeriod by remember { mutableStateOf<PeriodDefinitionEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理學校節次與時間") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onApplyUniversityPreset,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("大學 08:10 起", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onApplyHighSchoolPreset,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("高中 13:05 午課", maxLines = 1)
                    }
                }

                periods.forEach { period ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(period.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${FilenameHelper.formatMinuteOfDay(period.startMinuteOfDay)} ~ ${FilenameHelper.formatMinuteOfDay(period.endMinuteOfDay)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingPeriod = period }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Edit, contentDescription = "編輯", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onDeletePeriod(period) },
                                enabled = periods.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "刪除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val last = periods.lastOrNull()
                        val nextOrder = (last?.orderIndex ?: 0) + 1
                        val nextStart = ((last?.endMinuteOfDay ?: (17 * 60)) + 10).coerceAtMost(22 * 60)
                        val nextEnd = (nextStart + 50).coerceAtMost(23 * 60 + 50)
                        editingPeriod = PeriodDefinitionEntity(
                            orderIndex = nextOrder,
                            name = "第 $nextOrder 節",
                            startMinuteOfDay = nextStart,
                            endMinuteOfDay = nextEnd
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新增節次（例如第 9 節／晚自習）")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("完成")
            }
        }
    )

    editingPeriod?.let { target ->
        EditSinglePeriodDialog(
            period = target,
            onSave = { updated ->
                onSavePeriod(updated)
                editingPeriod = null
            },
            onDismiss = { editingPeriod = null }
        )
    }
}

@Composable
private fun EditSinglePeriodDialog(
    period: PeriodDefinitionEntity,
    onSave: (PeriodDefinitionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(period) { mutableStateOf(period.name) }
    var startText by remember(period) { mutableStateOf(FilenameHelper.formatMinuteOfDay(period.startMinuteOfDay)) }
    var endText by remember(period) { mutableStateOf(FilenameHelper.formatMinuteOfDay(period.endMinuteOfDay)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (period.id == 0L) "新增節次" else "編輯「${period.name}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("節次名稱（例如：第 3 節）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it; errorText = null },
                        label = { Text("開始 (HH:mm)") },
                        placeholder = { Text("10:10") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it; errorText = null },
                        label = { Text("結束 (HH:mm)") },
                        placeholder = { Text("11:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sMin = parseHmToMinute(startText)
                val eMin = parseHmToMinute(endText)
                if (name.isBlank()) {
                    errorText = "請輸入節次名稱"
                    return@Button
                }
                if (sMin == null || eMin == null) {
                    errorText = "時間格式請輸入 HH:mm，例如 10:10"
                    return@Button
                }
                val validationError = ScheduleCalculator.validatePeriodTime(sMin, eMin)
                if (validationError != null) {
                    errorText = validationError
                    return@Button
                }
                onSave(
                    period.copy(
                        name = name.trim(),
                        startMinuteOfDay = sMin,
                        endMinuteOfDay = eMin
                    )
                )
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

private fun parseHmToMinute(input: String): Int? {
    val parts = input.trim().replace("：", ":").split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

package com.classrecorder.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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

data class CourseBrushTemplate(
    val courseName: String,
    val classroom: String,
    val colorTag: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableScreen(
    uiState: MainUiState,
    onSaveTimetableSlotsBatch: (dayOfWeek: Int, periodIds: List<Long>, enabled: Boolean, courseName: String, classroom: String, colorTag: Int) -> Unit,
    onQuickPaintSlot: (dayOfWeek: Int, periodId: Long, courseName: String, classroom: String, colorTag: Int, eraseMode: Boolean) -> Unit,
    onApplySampleMorning34: () -> Unit,
    onSavePeriod: (PeriodDefinitionEntity) -> Unit,
    onDeletePeriod: (PeriodDefinitionEntity) -> Unit,
    onApplyUniversityPreset: () -> Unit,
    onApplyHighSchoolPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCell by remember { mutableStateOf<Pair<Int, PeriodDefinitionEntity>?>(null) }
    var showPeriodManager by remember { mutableStateOf(false) }

    // Quick-fill brush mode state
    var quickPaintEnabled by remember { mutableStateOf(false) }
    var brushCourseName by remember { mutableStateOf("") }
    var brushClassroom by remember { mutableStateOf("") }
    var brushColorTag by remember { mutableIntStateOf(0) }
    var brushEraseMode by remember { mutableStateOf(false) }

    val daysToShow = if (uiState.settings.showWeekends) (1..7).toList() else (1..5).toList()
    val slotMap = remember(uiState.timetableSlots) {
        uiState.timetableSlots.associateBy { it.dayOfWeek to it.periodId }
    }
    val hasAnyEnabledSlot = uiState.timetableSlots.any { it.enabled }

    val existingCourses = remember(uiState.timetableSlots) {
        uiState.timetableSlots
            .filter { it.enabled && it.courseName.isNotBlank() }
            .distinctBy { Triple(it.courseName.trim(), it.classroom.trim(), it.colorTag) }
            .map { CourseBrushTemplate(it.courseName.trim(), it.classroom.trim(), it.colorTag) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "每週課表排程",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "預設早 1~4 節 08:10 起、午 5~9 節 12:50 起（每節 50 分鐘）；可點「批次填課」快速連點多節或單格設定連堂。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (quickPaintEnabled) {
                        Button(
                            onClick = { quickPaintEnabled = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("完成批次填課")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { quickPaintEnabled = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("批次填課（連選）")
                        }
                    }

                    OutlinedButton(
                        onClick = { showPeriodManager = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("編輯節次")
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = quickPaintEnabled) {
                QuickPaintBrushCard(
                    courseName = brushCourseName,
                    classroom = brushClassroom,
                    colorTag = brushColorTag,
                    eraseMode = brushEraseMode,
                    existingCourses = existingCourses,
                    onCourseNameChange = { brushCourseName = it; brushEraseMode = false },
                    onClassroomChange = { brushClassroom = it },
                    onColorChange = { brushColorTag = it; brushEraseMode = false },
                    onToggleEraseMode = { brushEraseMode = !brushEraseMode },
                    onSelectTemplate = { tpl ->
                        brushCourseName = tpl.courseName
                        brushClassroom = tpl.classroom
                        brushColorTag = tpl.colorTag
                        brushEraseMode = false
                    }
                )
            }
        }

        if (!hasAnyEnabledSlot && !quickPaintEnabled) {
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
                    val borderColor = when {
                        enabled -> tagColor.copy(alpha = 0.7f)
                        quickPaintEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cellBg,
                        border = BorderStroke(if (enabled) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (quickPaintEnabled) {
                                    onQuickPaintSlot(
                                        dow,
                                        period.id,
                                        brushCourseName,
                                        brushClassroom,
                                        brushColorTag,
                                        brushEraseMode
                                    )
                                } else {
                                    editingCell = dow to period
                                }
                            }
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
            tappedPeriod = period,
            allPeriods = uiState.periods,
            existingSlot = existingSlot,
            existingCourses = existingCourses,
            onSaveBatch = { periodIds, enabled, courseName, classroom, colorTag ->
                onSaveTimetableSlotsBatch(dow, periodIds, enabled, courseName, classroom, colorTag)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPaintBrushCard(
    courseName: String,
    classroom: String,
    colorTag: Int,
    eraseMode: Boolean,
    existingCourses: List<CourseBrushTemplate>,
    onCourseNameChange: (String) -> Unit,
    onClassroomChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onToggleEraseMode: () -> Unit,
    onSelectTemplate: (CourseBrushTemplate) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (eraseMode) "清除模式：點課表格子立即清空" else "畫筆設定：設定後直接點擊下方格子連續填入",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = eraseMode,
                    onClick = onToggleEraseMode,
                    leadingIcon = {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("橡皮擦") }
                )
            }

            if (existingCourses.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "快速切換已填課程：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        existingCourses.forEach { tpl ->
                            val c = CourseTagColors[tpl.colorTag.mod(CourseTagColors.size)]
                            AssistChip(
                                onClick = { onSelectTemplate(tpl) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                    )
                                },
                                label = { Text(tpl.courseName, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = onCourseNameChange,
                    label = { Text("課程名稱") },
                    placeholder = { Text("例如：微積分") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = classroom,
                    onValueChange = onClassroomChange,
                    label = { Text("教室(選填)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("顏色：", style = MaterialTheme.typography.labelMedium)
                CourseTagColors.forEachIndexed { idx, color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorChange(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!eraseMode && colorTag == idx) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTimetableCellDialog(
    dayOfWeek: Int,
    tappedPeriod: PeriodDefinitionEntity,
    allPeriods: List<PeriodDefinitionEntity>,
    existingSlot: TimetableSlotEntity?,
    existingCourses: List<CourseBrushTemplate>,
    onSaveBatch: (periodIds: List<Long>, enabled: Boolean, courseName: String, classroom: String, colorTag: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember(existingSlot) { mutableStateOf(existingSlot?.enabled ?: true) }
    var courseName by remember(existingSlot) { mutableStateOf(existingSlot?.courseName ?: "") }
    var classroom by remember(existingSlot) { mutableStateOf(existingSlot?.classroom ?: "") }
    var colorTag by remember(existingSlot) { mutableIntStateOf(existingSlot?.colorTag ?: ((dayOfWeek + tappedPeriod.orderIndex) % CourseTagColors.size)) }
    var selectedPeriodIds by remember(tappedPeriod.id) { mutableStateOf(setOf(tappedPeriod.id)) }

    val tappedIndex = allPeriods.indexOfFirst { it.id == tappedPeriod.id }.coerceAtLeast(0)
    val timeRange = "${FilenameHelper.formatMinuteOfDay(tappedPeriod.startMinuteOfDay)}~${FilenameHelper.formatMinuteOfDay(tappedPeriod.endMinuteOfDay)}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${FilenameHelper.dayOfWeekLabel(dayOfWeek)} ${tappedPeriod.name}（$timeRange）") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("啟用此時段自動錄音", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (existingCourses.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "快速套用已有課程（帶入名稱與顏色）：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            existingCourses.forEach { tpl ->
                                val c = CourseTagColors[tpl.colorTag.mod(CourseTagColors.size)]
                                AssistChip(
                                    onClick = {
                                        courseName = tpl.courseName
                                        classroom = tpl.classroom
                                        colorTag = tpl.colorTag
                                        enabled = true
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                        )
                                    },
                                    label = { Text(tpl.courseName, maxLines = 1) }
                                )
                            }
                        }
                    }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "連堂快選（同時套用至${FilenameHelper.dayOfWeekLabel(dayOfWeek)}多節）：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "僅本節", 2 to "連 2 節", 3 to "連 3 節").forEach { (span, label) ->
                            val targetIds = allPeriods
                                .drop(tappedIndex)
                                .take(span)
                                .map { it.id }
                                .toSet()
                            val isSelected = selectedPeriodIds == targetIds
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPeriodIds = targetIds },
                                label = { Text(label) }
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allPeriods.forEach { p ->
                            val selected = p.id in selectedPeriodIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedPeriodIds = if (selected && selectedPeriodIds.size > 1) {
                                        selectedPeriodIds - p.id
                                    } else {
                                        selectedPeriodIds + p.id
                                    }
                                },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val orderedSelected = allPeriods
                    .filter { it.id in selectedPeriodIds }
                    .map { it.id }
                    .ifEmpty { listOf(tappedPeriod.id) }
                onSaveBatch(orderedSelected, enabled, courseName, classroom, colorTag)
            }) {
                Text(if (selectedPeriodIds.size > 1) "套用至 ${selectedPeriodIds.size} 節" else "儲存")
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
                        Text("預設 1~9 節", maxLines = 1)
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
                        val nextStart = ((last?.endMinuteOfDay ?: (17 * 60 + 40)) + 10).coerceAtMost(22 * 60)
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
                    Text("新增節次（例如第 10 節／晚自習）")
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
                    label = { Text("節次名稱（例如：第 5 節）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it; errorText = null },
                        label = { Text("開始 (HH:mm)") },
                        placeholder = { Text("12:50") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it; errorText = null },
                        label = { Text("結束 (HH:mm)") },
                        placeholder = { Text("13:40") },
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
                    errorText = "時間格式請輸入 HH:mm，例如 12:50"
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

package com.classrecorder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classrecorder.app.ui.PermissionStatus

@Composable
fun PermissionChecklistCard(
    status: PermissionStatus,
    onRequestRecordAudio: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "權限狀態",
                style = MaterialTheme.typography.titleMedium
            )

            PermissionItemRow(
                label = "麥克風",
                description = "錄製課堂音訊",
                granted = status.hasRecordAudio,
                actionLabel = "授權",
                onAction = onRequestRecordAudio
            )
            PermissionItemRow(
                label = "通知",
                description = "狀態列與排程提醒",
                granted = status.hasNotifications,
                actionLabel = "授權",
                onAction = onRequestNotifications
            )
            PermissionItemRow(
                label = "精準鬧鐘",
                description = "準時啟動排程",
                granted = status.hasExactAlarm,
                actionLabel = "設定",
                onAction = onOpenExactAlarmSettings
            )
            PermissionItemRow(
                label = "電池不受限制",
                description = "避免背景休眠中斷",
                granted = status.isIgnoringBatteryOptimizations,
                actionLabel = "設定",
                onAction = onOpenBatteryOptimizationSettings
            )
        }
    }
}

@Composable
private fun PermissionItemRow(
    label: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.Check else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                text = "已就緒",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                shape = RectangleShape
            ) {
                Text(actionLabel)
            }
        }
    }
}

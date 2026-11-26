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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "背景自動錄音穩定度檢查",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Android 14+ 會限制背景自動啟用麥克風；完成以下設定並在上課前開啟「排程守候」，可確保每節課準時開錄不斷線。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionItemRow(
                label = "麥克風錄音權限",
                description = "錄製課堂音訊必備",
                granted = status.hasRecordAudio,
                actionLabel = "授權",
                onAction = onRequestRecordAudio
            )
            PermissionItemRow(
                label = "通知顯示權限",
                description = "顯示錄音守候狀態列與時間到提醒",
                granted = status.hasNotifications,
                actionLabel = "授權",
                onAction = onRequestNotifications
            )
            PermissionItemRow(
                label = "精準鬧鐘與提醒權限",
                description = "確保 10:10、11:10 等節次準時觸發",
                granted = status.hasExactAlarm,
                actionLabel = "前往設定",
                onAction = onOpenExactAlarmSettings
            )
            PermissionItemRow(
                label = "背景電池無限制",
                description = "避免手機休眠或省電模式中斷錄音",
                granted = status.isIgnoringBatteryOptimizations,
                actionLabel = "設為不受限制",
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
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
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
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

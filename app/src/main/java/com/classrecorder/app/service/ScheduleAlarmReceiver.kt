package com.classrecorder.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.classrecorder.app.domain.FilenameHelper

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_START_SLOT -> {
                val serviceIntent = Intent(context, ClassRecorderService::class.java).apply {
                    action = ClassRecorderService.ACTION_START_SLOT
                    putExtras(intent)
                }
                val periodName = intent.getStringExtra(ClassRecorderService.EXTRA_PERIOD_NAME) ?: "課程"
                val courseName = intent.getStringExtra(ClassRecorderService.EXTRA_COURSE_NAME) ?: ""
                val startMin = intent.getIntExtra(ClassRecorderService.EXTRA_START_MINUTE, -1)
                val endMin = intent.getIntExtra(ClassRecorderService.EXTRA_END_MINUTE, -1)
                val title = courseName.ifBlank { periodName }
                val timeRange = if (startMin >= 0 && endMin >= 0) {
                    "${FilenameHelper.formatMinuteOfDay(startMin)}~${FilenameHelper.formatMinuteOfDay(endMin)}"
                } else {
                    ""
                }

                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: Exception) {
                    NotificationHelper.showManualStartPromptNotification(
                        context = context,
                        startServiceIntent = serviceIntent,
                        title = title,
                        timeRange = timeRange
                    )
                }
            }

            ACTION_ALARM_STOP_SLOT -> {
                val periodId = intent.getLongExtra(ClassRecorderService.EXTRA_PERIOD_ID, -1L)
                if (ClassRecorderService.isServiceAlive) {
                    val stopIntent = Intent(context, ClassRecorderService::class.java).apply {
                        action = ClassRecorderService.ACTION_ALARM_STOP_SLOT
                        putExtra(ClassRecorderService.EXTRA_PERIOD_ID, periodId)
                    }
                    runCatching { context.startService(stopIntent) }
                }
            }
        }
    }

    companion object {
        const val ACTION_ALARM_START_SLOT = "com.classrecorder.app.alarm.START_SLOT"
        const val ACTION_ALARM_STOP_SLOT = "com.classrecorder.app.alarm.STOP_SLOT"
    }
}

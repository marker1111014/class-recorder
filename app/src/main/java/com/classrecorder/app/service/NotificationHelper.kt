package com.classrecorder.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.classrecorder.app.MainActivity
import com.classrecorder.app.R
import com.classrecorder.app.domain.FilenameHelper

object NotificationHelper {

    const val CHANNEL_RECORDING_SERVICE = "channel_recording_service"
    const val CHANNEL_SCHEDULE_ALERTS = "channel_schedule_alerts"

    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val NOTIFICATION_ID_START_PROMPT = 1002
    const val NOTIFICATION_ID_AFTER_SCHOOL = 1003

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            CHANNEL_RECORDING_SERVICE,
            "排程守候與錄音狀態",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "顯示自動排程錄音待機狀態或正在進行的課堂錄音"
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_SCHEDULE_ALERTS,
            "上課與放學提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "當系統限制背景啟動麥克風時提示一鍵開錄，以及放學後提醒整理今日總結"
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertsChannel)
    }

    private fun mainActivityPendingIntent(context: Context, openExportDateIso: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openExportDateIso != null) {
                putExtra(MainActivity.EXTRA_OPEN_EXPORT_DATE, openExportDateIso)
            }
        }
        return PendingIntent.getActivity(
            context,
            openExportDateIso?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildArmedNotification(context: Context, nextSlotDescription: String): Notification {
        ensureChannels(context)
        val disarmIntent = Intent(context, ClassRecorderService::class.java).apply {
            action = ClassRecorderService.ACTION_DISARM
        }
        val disarmPi = PendingIntent.getService(
            context,
            201,
            disarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quickRecIntent = Intent(context, ClassRecorderService::class.java).apply {
            action = ClassRecorderService.ACTION_START_MANUAL
        }
        val quickRecPi = PendingIntent.getForegroundService(
            context,
            202,
            quickRecIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_RECORDING_SERVICE)
            .setSmallIcon(R.drawable.ic_schedule_notification)
            .setContentTitle("自動錄音排程守候中")
            .setContentText(nextSlotDescription)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nextSlotDescription))
            .setContentIntent(mainActivityPendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_mic_notification, "手動立即錄音", quickRecPi)
            .addAction(0, "停止守候", disarmPi)
            .build()
    }

    fun buildRecordingNotification(
        context: Context,
        title: String,
        timeRange: String,
        elapsedMs: Long,
        isPaused: Boolean
    ): Notification {
        ensureChannels(context)
        val pauseResumeIntent = Intent(context, ClassRecorderService::class.java).apply {
            action = ClassRecorderService.ACTION_PAUSE_OR_RESUME
        }
        val pauseResumePi = PendingIntent.getService(
            context,
            203,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, ClassRecorderService::class.java).apply {
            action = ClassRecorderService.ACTION_STOP_RECORDING
        }
        val stopPi = PendingIntent.getService(
            context,
            204,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedStr = FilenameHelper.formatDuration(elapsedMs)
        val statusPrefix = if (isPaused) "已暫停錄音" else "正在錄音"
        val contentText = buildString {
            if (timeRange.isNotBlank()) append("$timeRange · ")
            append("已錄製 $elapsedStr")
        }

        return NotificationCompat.Builder(context, CHANNEL_RECORDING_SERVICE)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle("$statusPrefix：$title")
            .setContentText(contentText)
            .setContentIntent(mainActivityPendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (isPaused) "繼續" else "暫停", pauseResumePi)
            .addAction(0, "停止並儲存", stopPi)
            .build()
    }

    fun showManualStartPromptNotification(
        context: Context,
        startServiceIntent: Intent,
        title: String,
        timeRange: String
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val actionPi = PendingIntent.getForegroundService(
            context,
            205,
            startServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_ALERTS)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle("上課時間到：$title")
            .setContentText("時段 $timeRange，因系統背景麥克風限制，請點「立即開始錄音」並同時開啟今日自動守候。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "時段 $timeRange 已開始。因 Android 14+ 限制背景自動開啟麥克風，點擊下方按鈕即可立即錄音並保持後續節次自動錄製。"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(actionPi)
            .addAction(R.drawable.ic_mic_notification, "立即開始錄音", actionPi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_START_PROMPT, notification)
        }
    }

    fun showAfterSchoolSummaryNotification(
        context: Context,
        recordedCountToday: Int,
        dateIso: String
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_ALERTS)
            .setSmallIcon(R.drawable.ic_schedule_notification)
            .setContentTitle("今日排程錄音已完成（共 $recordedCountToday 段）")
            .setContentText("放學了！點擊查看今日時間軸、補記筆記，或一鍵匯出給 AI 總結。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context, openExportDateIso = dateIso))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AFTER_SCHOOL, notification)
        }
    }

    fun cancelStartPrompt(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_START_PROMPT)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

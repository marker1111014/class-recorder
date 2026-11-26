package com.classrecorder.app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classrecorder.app.data.repository.ClassRecorderRepository
import com.classrecorder.app.domain.NextAlarmTarget
import com.classrecorder.app.domain.ScheduleCalculator
import com.classrecorder.app.domain.ScheduledSlotInstance
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {

    private const val REQUEST_CODE_START = 4001
    private const val REQUEST_CODE_STOP = 4002

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_ALARM_START_SLOT
        }
        val stopIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_ALARM_STOP_SLOT
        }
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_START,
            startIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { alarmManager.cancel(it) }

        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { alarmManager.cancel(it) }
    }

    suspend fun rescheduleNextAlarm(
        context: Context,
        repository: ClassRecorderRepository,
        now: ZonedDateTime = ZonedDateTime.now(),
        afterJustFinishedPeriodId: Long? = null
    ): NextAlarmTarget? {
        cancelAll(context)
        val settings = repository.preferences.getSettings()
        if (!settings.scheduleEnabled) return null

        val periods = repository.getPeriods()
        val slots = repository.getTimetableSlots()
        val skippedKeys = repository.getSkippedKeysFrom(LocalDate.now().toString())
        val zoneId = ZoneId.systemDefault()

        var target = ScheduleCalculator.computeNextAlarmTarget(
            now = now,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = skippedKeys,
            startBufferSeconds = settings.startBufferSeconds,
            endBufferSeconds = settings.endBufferSeconds,
            zoneId = zoneId
        )

        // If user manually stopped an active slot early, don't immediately stop or re-trigger the same slot.
        if (afterJustFinishedPeriodId != null &&
            target is NextAlarmTarget.StopSlot &&
            target.instance.period.id == afterJustFinishedPeriodId
        ) {
            val next = ScheduleCalculator.findNextUpcomingSlot(
                now = now,
                periods = periods,
                timetableSlots = slots,
                skippedKeys = skippedKeys,
                startBufferSeconds = settings.startBufferSeconds,
                endBufferSeconds = settings.endBufferSeconds,
                zoneId = zoneId
            )
            target = next?.let { NextAlarmTarget.StartSlot(it) }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when (target) {
            is NextAlarmTarget.StartSlot -> {
                val triggerAtMs = target.instance.effectiveStart.toInstant().toEpochMilli()
                val pi = buildStartPendingIntent(context, target.instance)
                scheduleExactOrFallback(context, alarmManager, triggerAtMs, pi)
            }
            is NextAlarmTarget.StopSlot -> {
                val triggerAtMs = target.instance.effectiveEnd.toInstant().toEpochMilli()
                val pi = buildStopPendingIntent(context, target.instance)
                scheduleExactOrFallback(context, alarmManager, triggerAtMs, pi)
            }
            null -> Unit
        }
        return target
    }

    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun scheduleExactOrFallback(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMs: Long,
        pendingIntent: PendingIntent
    ) {
        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
    }

    private fun buildStartPendingIntent(
        context: Context,
        instance: ScheduledSlotInstance
    ): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_ALARM_START_SLOT
            putExtra(ClassRecorderService.EXTRA_DATE_ISO, instance.dateIso)
            putExtra(ClassRecorderService.EXTRA_DAY_OF_WEEK, instance.date.dayOfWeek.value)
            putExtra(ClassRecorderService.EXTRA_PERIOD_ID, instance.period.id)
            putExtra(ClassRecorderService.EXTRA_PERIOD_ORDER, instance.period.orderIndex)
            putExtra(ClassRecorderService.EXTRA_PERIOD_NAME, instance.period.name)
            putExtra(ClassRecorderService.EXTRA_COURSE_NAME, instance.slot.courseName)
            putExtra(ClassRecorderService.EXTRA_START_MINUTE, instance.period.startMinuteOfDay)
            putExtra(ClassRecorderService.EXTRA_END_MINUTE, instance.period.endMinuteOfDay)
            putExtra(
                ClassRecorderService.EXTRA_END_EPOCH_MS,
                instance.effectiveEnd.toInstant().toEpochMilli()
            )
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_START,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildStopPendingIntent(
        context: Context,
        instance: ScheduledSlotInstance
    ): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_ALARM_STOP_SLOT
            putExtra(ClassRecorderService.EXTRA_PERIOD_ID, instance.period.id)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

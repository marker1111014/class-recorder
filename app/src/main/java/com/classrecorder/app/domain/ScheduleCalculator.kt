package com.classrecorder.app.domain

import com.classrecorder.app.data.db.PeriodDefinitionEntity
import com.classrecorder.app.data.db.SkippedSlotEntity
import com.classrecorder.app.data.db.TimetableSlotEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduledSlotInstance(
    val date: LocalDate,
    val period: PeriodDefinitionEntity,
    val slot: TimetableSlotEntity,
    val isSkipped: Boolean,
    val effectiveStart: ZonedDateTime,
    val effectiveEnd: ZonedDateTime
) {
    val dateIso: String get() = date.toString()
    val displayTitle: String
        get() = slot.courseName.trim().ifEmpty { period.name }
    val timeRangeText: String
        get() = "${FilenameHelper.formatMinuteOfDay(period.startMinuteOfDay)}~${FilenameHelper.formatMinuteOfDay(period.endMinuteOfDay)}"
}

sealed class NextAlarmTarget {
    data class StartSlot(val instance: ScheduledSlotInstance) : NextAlarmTarget()
    data class StopSlot(val instance: ScheduledSlotInstance) : NextAlarmTarget()
}

object ScheduleCalculator {

    fun validatePeriodTime(startMinuteOfDay: Int, endMinuteOfDay: Int): String? {
        if (startMinuteOfDay !in 0..1439 || endMinuteOfDay !in 0..1439) {
            return "時間必須介於 00:00 至 23:59 之間"
        }
        if (endMinuteOfDay <= startMinuteOfDay) {
            return "結束時間必須晚於開始時間"
        }
        return null
    }

    fun getSlotsForDate(
        date: LocalDate,
        periods: List<PeriodDefinitionEntity>,
        timetableSlots: List<TimetableSlotEntity>,
        skippedKeys: Set<String>,
        startBufferSeconds: Int = 0,
        endBufferSeconds: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ScheduledSlotInstance> {
        val dayOfWeek = date.dayOfWeek.value
        val periodById = periods.associateBy { it.id }
        val dateIso = date.toString()

        return timetableSlots
            .asSequence()
            .filter { it.dayOfWeek == dayOfWeek && it.enabled }
            .mapNotNull { slot ->
                val period = periodById[slot.periodId] ?: return@mapNotNull null
                val startTime = LocalTime.of(period.startMinuteOfDay / 60, period.startMinuteOfDay % 60)
                val endTime = LocalTime.of(period.endMinuteOfDay / 60, period.endMinuteOfDay % 60)
                val nominalStart = ZonedDateTime.of(date, startTime, zoneId)
                val nominalEnd = ZonedDateTime.of(date, endTime, zoneId)

                val effectiveStart = nominalStart.minusSeconds(startBufferSeconds.coerceAtLeast(0).toLong())
                val effectiveEnd = nominalEnd.plusSeconds(endBufferSeconds.coerceAtLeast(0).toLong())
                val skipped = SkippedSlotEntity.makeKey(dateIso, period.id) in skippedKeys

                ScheduledSlotInstance(
                    date = date,
                    period = period,
                    slot = slot,
                    isSkipped = skipped,
                    effectiveStart = effectiveStart,
                    effectiveEnd = effectiveEnd
                )
            }
            .sortedBy { it.period.startMinuteOfDay }
            .toList()
    }

    fun findActiveSlot(
        now: ZonedDateTime,
        slots: List<ScheduledSlotInstance>
    ): ScheduledSlotInstance? {
        return slots.firstOrNull { slot ->
            !slot.isSkipped &&
                !now.isBefore(slot.effectiveStart) &&
                now.isBefore(slot.effectiveEnd)
        }
    }

    fun findNextUpcomingSlot(
        now: ZonedDateTime,
        periods: List<PeriodDefinitionEntity>,
        timetableSlots: List<TimetableSlotEntity>,
        skippedKeys: Set<String>,
        startBufferSeconds: Int = 0,
        endBufferSeconds: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lookAheadDays: Int = 8
    ): ScheduledSlotInstance? {
        val baseDate = now.toLocalDate()
        for (offset in 0..lookAheadDays) {
            val targetDate = baseDate.plusDays(offset.toLong())
            val daySlots = getSlotsForDate(
                date = targetDate,
                periods = periods,
                timetableSlots = timetableSlots,
                skippedKeys = skippedKeys,
                startBufferSeconds = startBufferSeconds,
                endBufferSeconds = endBufferSeconds,
                zoneId = zoneId
            )
            val candidate = daySlots.firstOrNull { !it.isSkipped && it.effectiveStart.isAfter(now) }
            if (candidate != null) return candidate
        }
        return null
    }

    fun hasRemainingSlotsToday(
        now: ZonedDateTime,
        todaySlots: List<ScheduledSlotInstance>
    ): Boolean {
        return todaySlots.any { !it.isSkipped && it.effectiveEnd.isAfter(now) }
    }

    fun computeNextAlarmTarget(
        now: ZonedDateTime,
        periods: List<PeriodDefinitionEntity>,
        timetableSlots: List<TimetableSlotEntity>,
        skippedKeys: Set<String>,
        startBufferSeconds: Int = 0,
        endBufferSeconds: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): NextAlarmTarget? {
        val today = now.toLocalDate()
        val todaySlots = getSlotsForDate(
            date = today,
            periods = periods,
            timetableSlots = timetableSlots,
            skippedKeys = skippedKeys,
            startBufferSeconds = startBufferSeconds,
            endBufferSeconds = endBufferSeconds,
            zoneId = zoneId
        )
        val active = findActiveSlot(now, todaySlots)
        if (active != null) {
            return NextAlarmTarget.StopSlot(active)
        }
        val upcoming = findNextUpcomingSlot(
            now = now,
            periods = periods,
            timetableSlots = timetableSlots,
            skippedKeys = skippedKeys,
            startBufferSeconds = startBufferSeconds,
            endBufferSeconds = endBufferSeconds,
            zoneId = zoneId
        )
        return upcoming?.let { NextAlarmTarget.StartSlot(it) }
    }
}

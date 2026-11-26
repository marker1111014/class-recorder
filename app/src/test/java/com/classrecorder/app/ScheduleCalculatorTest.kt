package com.classrecorder.app.domain

import com.classrecorder.app.data.db.PeriodDefinitionEntity
import com.classrecorder.app.data.db.SkippedSlotEntity
import com.classrecorder.app.data.db.TimetableSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleCalculatorTest {

    private val zone = ZoneId.of("Asia/Taipei")

    private val period3 = PeriodDefinitionEntity(
        id = 3L,
        orderIndex = 3,
        name = "第 3 節",
        startMinuteOfDay = 10 * 60 + 10,
        endMinuteOfDay = 11 * 60
    )

    private val period4 = PeriodDefinitionEntity(
        id = 4L,
        orderIndex = 4,
        name = "第 4 節",
        startMinuteOfDay = 11 * 60 + 10,
        endMinuteOfDay = 12 * 60
    )

    private val periods = listOf(period3, period4)

    // Wednesday 2026-11-25 (dayOfWeek = 3)
    private val wednesday = LocalDate.of(2026, 11, 25)
    private val slots = listOf(
        TimetableSlotEntity(id = 1, dayOfWeek = 3, periodId = 3L, enabled = true, courseName = "國文"),
        TimetableSlotEntity(id = 2, dayOfWeek = 3, periodId = 4L, enabled = true, courseName = "英文"),
        // Friday 2026-11-27 (dayOfWeek = 5)
        TimetableSlotEntity(id = 3, dayOfWeek = 5, periodId = 3L, enabled = true, courseName = "數學")
    )

    @Test
    fun morningPeriods3And4_transitionsThroughDayCorrectly() {
        val t0950 = ZonedDateTime.of(wednesday, LocalTime.of(9, 50), zone)
        val targetBefore = ScheduleCalculator.computeNextAlarmTarget(
            now = t0950,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = emptySet(),
            zoneId = zone
        )
        assertTrue(targetBefore is NextAlarmTarget.StartSlot)
        assertEquals(3L, (targetBefore as NextAlarmTarget.StartSlot).instance.period.id)
        assertEquals(LocalTime.of(10, 10), targetBefore.instance.effectiveStart.toLocalTime())

        val t1015 = ZonedDateTime.of(wednesday, LocalTime.of(10, 15), zone)
        val targetDuringP3 = ScheduleCalculator.computeNextAlarmTarget(
            now = t1015,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = emptySet(),
            zoneId = zone
        )
        assertTrue(targetDuringP3 is NextAlarmTarget.StopSlot)
        assertEquals(3L, (targetDuringP3 as NextAlarmTarget.StopSlot).instance.period.id)
        assertEquals(LocalTime.of(11, 0), targetDuringP3.instance.effectiveEnd.toLocalTime())

        // Break between 11:00 and 11:10
        val t1100 = ZonedDateTime.of(wednesday, LocalTime.of(11, 0), zone)
        val targetBreak = ScheduleCalculator.computeNextAlarmTarget(
            now = t1100,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = emptySet(),
            zoneId = zone
        )
        assertTrue(targetBreak is NextAlarmTarget.StartSlot)
        assertEquals(4L, (targetBreak as NextAlarmTarget.StartSlot).instance.period.id)
        assertEquals("英文", targetBreak.instance.displayTitle)

        // After school at 12:05 -> rolls forward to Friday
        val t1205 = ZonedDateTime.of(wednesday, LocalTime.of(12, 5), zone)
        val todaySlots = ScheduleCalculator.getSlotsForDate(wednesday, periods, slots, emptySet(), zoneId = zone)
        assertFalse(ScheduleCalculator.hasRemainingSlotsToday(t1205, todaySlots))

        val targetAfterSchool = ScheduleCalculator.computeNextAlarmTarget(
            now = t1205,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = emptySet(),
            zoneId = zone
        )
        assertTrue(targetAfterSchool is NextAlarmTarget.StartSlot)
        assertEquals(LocalDate.of(2026, 11, 27), (targetAfterSchool as NextAlarmTarget.StartSlot).instance.date)
        assertEquals("數學", targetAfterSchool.instance.displayTitle)
    }

    @Test
    fun skippedPeriodForToday_isIgnoredByScheduler() {
        val skipped = setOf(SkippedSlotEntity.makeKey(wednesday.toString(), 3L))
        val t0950 = ZonedDateTime.of(wednesday, LocalTime.of(9, 50), zone)

        val nextTarget = ScheduleCalculator.computeNextAlarmTarget(
            now = t0950,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = skipped,
            zoneId = zone
        )
        assertTrue(nextTarget is NextAlarmTarget.StartSlot)
        assertEquals(4L, (nextTarget as NextAlarmTarget.StartSlot).instance.period.id)

        val t1015 = ZonedDateTime.of(wednesday, LocalTime.of(10, 15), zone)
        val todaySlots = ScheduleCalculator.getSlotsForDate(wednesday, periods, slots, skipped, zoneId = zone)
        assertNull(ScheduleCalculator.findActiveSlot(t1015, todaySlots))
    }

    @Test
    fun buffersAdjustEffectiveStartAndEnd() {
        val t100940 = ZonedDateTime.of(wednesday, LocalTime.of(10, 9, 40), zone)
        val todaySlots = ScheduleCalculator.getSlotsForDate(
            date = wednesday,
            periods = periods,
            timetableSlots = slots,
            skippedKeys = emptySet(),
            startBufferSeconds = 30,
            endBufferSeconds = 60,
            zoneId = zone
        )
        val active = ScheduleCalculator.findActiveSlot(t100940, todaySlots)
        assertNotNull(active)
        assertEquals(3L, active!!.period.id)
        assertEquals(LocalTime.of(10, 9, 30), active.effectiveStart.toLocalTime())
        assertEquals(LocalTime.of(11, 1, 0), active.effectiveEnd.toLocalTime())
    }

    @Test
    fun validatePeriodTime_rejectsInvalidRanges() {
        assertNotNull(ScheduleCalculator.validatePeriodTime(600, 600))
        assertNotNull(ScheduleCalculator.validatePeriodTime(700, 600))
        assertNull(ScheduleCalculator.validatePeriodTime(610, 660))
    }

    @Test
    fun defaultUniversityPeriods_matchExpectedNinePeriodSchedule() {
        val defaults = com.classrecorder.app.data.db.AppDatabase.DEFAULT_UNIVERSITY_PERIODS
        assertEquals(9, defaults.size)

        val expectedRanges = listOf(
            1 to ("08:10" to "09:00"),
            2 to ("09:10" to "10:00"),
            3 to ("10:10" to "11:00"),
            4 to ("11:10" to "12:00"),
            5 to ("12:50" to "13:40"),
            6 to ("13:50" to "14:40"),
            7 to ("14:50" to "15:40"),
            8 to ("15:50" to "16:40"),
            9 to ("16:50" to "17:40")
        )

        defaults.zip(expectedRanges).forEach { (period, expected) ->
            val (order, times) = expected
            assertEquals(order, period.orderIndex)
            assertEquals(times.first, FilenameHelper.formatMinuteOfDay(period.startMinuteOfDay))
            assertEquals(times.second, FilenameHelper.formatMinuteOfDay(period.endMinuteOfDay))
            assertEquals(50, period.endMinuteOfDay - period.startMinuteOfDay)
        }

        // Verify afternoon period 5 (12:50~13:40) and period 9 (16:50~17:40) schedule resolution
        val withIds = defaults.map { it.copy(id = it.orderIndex.toLong()) }
        val wedSlots = listOf(
            TimetableSlotEntity(id = 10, dayOfWeek = 3, periodId = 5L, enabled = true, courseName = "資料結構"),
            TimetableSlotEntity(id = 11, dayOfWeek = 3, periodId = 6L, enabled = true, courseName = "資料結構"),
            TimetableSlotEntity(id = 12, dayOfWeek = 3, periodId = 9L, enabled = true, courseName = "通識講座")
        )
        val t1245 = ZonedDateTime.of(wednesday, LocalTime.of(12, 45), zone)
        val next = ScheduleCalculator.computeNextAlarmTarget(
            now = t1245,
            periods = withIds,
            timetableSlots = wedSlots,
            skippedKeys = emptySet(),
            zoneId = zone
        )
        assertTrue(next is NextAlarmTarget.StartSlot)
        assertEquals(5L, (next as NextAlarmTarget.StartSlot).instance.period.id)
        assertEquals("12:50~13:40", next.instance.timeRangeText)

        val t1655 = ZonedDateTime.of(wednesday, LocalTime.of(16, 55), zone)
        val active9 = ScheduleCalculator.computeNextAlarmTarget(
            now = t1655,
            periods = withIds,
            timetableSlots = wedSlots,
            skippedKeys = emptySet(),
            zoneId = zone
        )
        assertTrue(active9 is NextAlarmTarget.StopSlot)
        assertEquals(9L, (active9 as NextAlarmTarget.StopSlot).instance.period.id)
        assertEquals("16:50~17:40", active9.instance.timeRangeText)
    }
}

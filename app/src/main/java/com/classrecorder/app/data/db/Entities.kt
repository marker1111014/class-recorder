package com.classrecorder.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "period_definitions")
data class PeriodDefinitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val name: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
)

@Entity(
    tableName = "timetable_slots",
    foreignKeys = [
        ForeignKey(
            entity = PeriodDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["periodId"]),
        Index(value = ["dayOfWeek", "periodId"], unique = true)
    ]
)
data class TimetableSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayOfWeek: Int,
    val periodId: Long,
    val enabled: Boolean = true,
    val courseName: String = "",
    val classroom: String = "",
    val colorTag: Int = 0
)

@Entity(
    tableName = "skipped_slots",
    indices = [Index(value = ["dateIso"])]
)
data class SkippedSlotEntity(
    @PrimaryKey
    val key: String,
    val dateIso: String,
    val periodId: Long
) {
    companion object {
        fun makeKey(dateIso: String, periodId: Long): String = "${dateIso}_${periodId}"
    }
}

object RecordingStatus {
    const val RECORDING = "RECORDING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}

@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["dateIso"]),
        Index(value = ["actualStartEpochMs"])
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateIso: String,
    val dayOfWeek: Int,
    val periodId: Long? = null,
    val periodName: String,
    val courseName: String = "",
    val scheduledStartMinute: Int? = null,
    val scheduledEndMinute: Int? = null,
    val actualStartEpochMs: Long,
    val actualEndEpochMs: Long? = null,
    val durationMs: Long = 0L,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val status: String = RecordingStatus.RECORDING,
    val isManual: Boolean = false,
    val classNote: String = "",
    val homeworkNote: String = "",
    val aiTranscriptOrSummary: String = ""
)

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey
    val dateIso: String,
    val title: String = "",
    val summaryText: String = "",
    val moodOrTag: String = "",
    val updatedAtEpochMs: Long = 0L
)

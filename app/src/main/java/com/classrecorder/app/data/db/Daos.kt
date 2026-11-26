package com.classrecorder.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {
    @Query("SELECT * FROM period_definitions ORDER BY startMinuteOfDay ASC, orderIndex ASC")
    fun observeAll(): Flow<List<PeriodDefinitionEntity>>

    @Query("SELECT * FROM period_definitions ORDER BY startMinuteOfDay ASC, orderIndex ASC")
    suspend fun getAll(): List<PeriodDefinitionEntity>

    @Query("SELECT COUNT(*) FROM period_definitions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: PeriodDefinitionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<PeriodDefinitionEntity>)

    @Update
    suspend fun update(period: PeriodDefinitionEntity)

    @Delete
    suspend fun delete(period: PeriodDefinitionEntity)

    @Query("DELETE FROM period_definitions")
    suspend fun deleteAll()
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek ASC, periodId ASC")
    fun observeAll(): Flow<List<TimetableSlotEntity>>

    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek ASC, periodId ASC")
    suspend fun getAll(): List<TimetableSlotEntity>

    @Query("SELECT * FROM timetable_slots WHERE dayOfWeek = :dayOfWeek AND periodId = :periodId LIMIT 1")
    suspend fun getSlot(dayOfWeek: Int, periodId: Long): TimetableSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: TimetableSlotEntity): Long

    @Query("DELETE FROM timetable_slots WHERE dayOfWeek = :dayOfWeek AND periodId = :periodId")
    suspend fun deleteSlot(dayOfWeek: Int, periodId: Long)

    @Query("DELETE FROM timetable_slots")
    suspend fun clearAll()
}

@Dao
interface SkippedSlotDao {
    @Query("SELECT * FROM skipped_slots WHERE dateIso = :dateIso")
    fun observeByDate(dateIso: String): Flow<List<SkippedSlotEntity>>

    @Query("SELECT * FROM skipped_slots")
    fun observeAll(): Flow<List<SkippedSlotEntity>>

    @Query("SELECT * FROM skipped_slots WHERE dateIso >= :fromDateIso")
    suspend fun getFromDate(fromDateIso: String): List<SkippedSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun skip(entity: SkippedSlotEntity)

    @Query("DELETE FROM skipped_slots WHERE key = :key")
    suspend fun unskip(key: String)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY actualStartEpochMs DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE dateIso = :dateIso ORDER BY actualStartEpochMs ASC")
    fun observeByDate(dateIso: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE dateIso = :dateIso ORDER BY actualStartEpochMs ASC")
    suspend fun getByDate(dateIso: String): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status IN ('RECORDING', 'PAUSED')")
    suspend fun getActiveRecordings(): List<RecordingEntity>

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("UPDATE recordings SET classNote = :classNote, homeworkNote = :homeworkNote, aiTranscriptOrSummary = :aiSummary WHERE id = :id")
    suspend fun updateNotes(id: Long, classNote: String, homeworkNote: String, aiSummary: String)

    @Query("SELECT SUM(fileSizeBytes) FROM recordings")
    fun observeTotalSizeBytes(): Flow<Long?>
}

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summaries ORDER BY dateIso DESC")
    fun observeAll(): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries WHERE dateIso = :dateIso LIMIT 1")
    fun observeByDate(dateIso: String): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summaries WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getByDate(dateIso: String): DailySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)
}

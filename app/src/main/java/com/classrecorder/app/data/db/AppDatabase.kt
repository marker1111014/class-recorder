package com.classrecorder.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PeriodDefinitionEntity::class,
        TimetableSlotEntity::class,
        SkippedSlotEntity::class,
        RecordingEntity::class,
        DailySummaryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun periodDao(): PeriodDao
    abstract fun timetableDao(): TimetableDao
    abstract fun skippedSlotDao(): SkippedSlotDao
    abstract fun recordingDao(): RecordingDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val DEFAULT_UNIVERSITY_PERIODS = listOf(
            PeriodDefinitionEntity(orderIndex = 1, name = "第 1 節", startMinuteOfDay = 8 * 60 + 10, endMinuteOfDay = 9 * 60),
            PeriodDefinitionEntity(orderIndex = 2, name = "第 2 節", startMinuteOfDay = 9 * 60 + 10, endMinuteOfDay = 10 * 60),
            PeriodDefinitionEntity(orderIndex = 3, name = "第 3 節", startMinuteOfDay = 10 * 60 + 10, endMinuteOfDay = 11 * 60),
            PeriodDefinitionEntity(orderIndex = 4, name = "第 4 節", startMinuteOfDay = 11 * 60 + 10, endMinuteOfDay = 12 * 60),
            PeriodDefinitionEntity(orderIndex = 5, name = "第 5 節", startMinuteOfDay = 13 * 60 + 10, endMinuteOfDay = 14 * 60),
            PeriodDefinitionEntity(orderIndex = 6, name = "第 6 節", startMinuteOfDay = 14 * 60 + 10, endMinuteOfDay = 15 * 60),
            PeriodDefinitionEntity(orderIndex = 7, name = "第 7 節", startMinuteOfDay = 15 * 60 + 10, endMinuteOfDay = 16 * 60),
            PeriodDefinitionEntity(orderIndex = 8, name = "第 8 節", startMinuteOfDay = 16 * 60 + 10, endMinuteOfDay = 17 * 60)
        )

        val HIGH_SCHOOL_PERIODS = listOf(
            PeriodDefinitionEntity(orderIndex = 1, name = "第 1 節", startMinuteOfDay = 8 * 60 + 10, endMinuteOfDay = 9 * 60),
            PeriodDefinitionEntity(orderIndex = 2, name = "第 2 節", startMinuteOfDay = 9 * 60 + 10, endMinuteOfDay = 10 * 60),
            PeriodDefinitionEntity(orderIndex = 3, name = "第 3 節", startMinuteOfDay = 10 * 60 + 10, endMinuteOfDay = 11 * 60),
            PeriodDefinitionEntity(orderIndex = 4, name = "第 4 節", startMinuteOfDay = 11 * 60 + 10, endMinuteOfDay = 12 * 60),
            PeriodDefinitionEntity(orderIndex = 5, name = "第 5 節", startMinuteOfDay = 13 * 60 + 5, endMinuteOfDay = 13 * 60 + 55),
            PeriodDefinitionEntity(orderIndex = 6, name = "第 6 節", startMinuteOfDay = 14 * 60 + 5, endMinuteOfDay = 14 * 60 + 55),
            PeriodDefinitionEntity(orderIndex = 7, name = "第 7 節", startMinuteOfDay = 15 * 60 + 10, endMinuteOfDay = 16 * 60),
            PeriodDefinitionEntity(orderIndex = 8, name = "第 8 節", startMinuteOfDay = 16 * 60 + 10, endMinuteOfDay = 17 * 60)
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_recorder.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            DEFAULT_UNIVERSITY_PERIODS.forEach { p ->
                                db.execSQL(
                                    "INSERT INTO period_definitions (orderIndex, name, startMinuteOfDay, endMinuteOfDay) VALUES (?, ?, ?, ?)",
                                    arrayOf<Any>(p.orderIndex, p.name, p.startMinuteOfDay, p.endMinuteOfDay)
                                )
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

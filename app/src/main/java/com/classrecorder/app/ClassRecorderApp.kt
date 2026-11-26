package com.classrecorder.app

import android.app.Application
import com.classrecorder.app.data.db.AppDatabase
import com.classrecorder.app.data.prefs.AppPreferences
import com.classrecorder.app.data.repository.ClassRecorderRepository
import com.classrecorder.app.service.AlarmScheduler
import com.classrecorder.app.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClassRecorderApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: AppPreferences
        private set

    lateinit var repository: ClassRecorderRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = AppPreferences(this)
        repository = ClassRecorderRepository(this, database, preferences)
        NotificationHelper.ensureChannels(this)

        applicationScope.launch {
            repository.recoverDanglingRecordings()
            AlarmScheduler.rescheduleNextAlarm(this@ClassRecorderApp, repository)
        }
    }
}

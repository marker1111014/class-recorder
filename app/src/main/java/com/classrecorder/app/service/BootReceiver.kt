package com.classrecorder.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.classrecorder.app.ClassRecorderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            val app = context.applicationContext as ClassRecorderApp
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.recoverDanglingRecordings()
                    AlarmScheduler.rescheduleNextAlarm(context, app.repository)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

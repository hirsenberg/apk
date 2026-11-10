package com.kaoyan.studybell.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.*

class BellAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_RING) return
        val pending = goAsync()
        val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studybell:alarm-receiver")
        wake.acquire(15000)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(9000) { AlarmScheduler.receive(context.applicationContext, intent) } }
            catch (_: Exception) { AlarmScheduler.recordResult(context, "响铃处理失败，请打开应用检查闹钟设置") }
            finally { if (wake.isHeld) wake.release(); pending.finish() }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studybell:reschedule")
        wake.acquire(15000)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(9000) { AlarmScheduler.reconcile(context.applicationContext) } }
            finally { if (wake.isHeld) wake.release(); pending.finish() }
        }
    }
}

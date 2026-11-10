package com.kaoyan.studybell.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kaoyan.studybell.MainActivity
import com.kaoyan.studybell.data.ScheduleRepository
import com.kaoyan.studybell.data.StudyDatabase
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AlarmStatus(val message: String = "正在检查自动响铃", val next: Long = 0, val test: Long = 0, val lastResult: String = "")

object AlarmScheduler {
    const val ACTION_RING = "com.kaoyan.studybell.RING"
    private val mutex = Mutex()
    private val _status = MutableStateFlow(AlarmStatus())
    val status = _status.asStateFlow()
    private fun preferences(context: Context) = context.getSharedPreferences("bell_alarms", Context.MODE_PRIVATE)
    fun canSchedule(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun refresh(context: Context) {
        val prefs = preferences(context)
        _status.value = AlarmStatus(prefs.getString("message", "请开启闹钟和提醒权限") ?: "", prefs.getLong("normal_at", 0), prefs.getLong("test_at", 0), prefs.getString("last_result", "") ?: "")
    }
    fun recordResult(context: Context, message: String) {
        preferences(context).edit().putString("last_result", message).apply()
        refresh(context)
    }
    private fun pending(context: Context, test: Boolean, event: BellEvent? = null, token: String = ""): PendingIntent {
        val intent = Intent(context, BellAlarmReceiver::class.java).setAction(ACTION_RING)
            .setData(Uri.parse("studybell://alarm/${if (test) "test" else "normal"}"))
            .putExtra("test", test).putExtra("token", token)
        event?.let { intent.putExtra("at", it.at.toEpochMilli()).putExtra("kind", it.kind.name).putExtra("label", it.label) }
        return PendingIntent.getBroadcast(context, if (test) 102 else 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun schedule(context: Context, event: BellEvent, test: Boolean) {
        val prefs = preferences(context)
        val prefix = if (test) "test" else "normal"
        val token = UUID.randomUUID().toString()
        val operation = pending(context, test, event, token)
        val show = PendingIntent.getActivity(context, 103, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            // Alarm-clock alarms can wake a device in Doze without allow-while-idle's per-app interval quota.
            context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(event.at.toEpochMilli(), show), operation)
            prefs.edit().putString(prefix + "_token", token).putLong(prefix + "_at", event.at.toEpochMilli()).apply()
        } catch (e: Exception) {
            operation.cancel()
            prefs.edit().remove(prefix + "_token").remove(prefix + "_at").apply()
            throw e
        }
    }
    private suspend fun reconcileLocked(context: Context) {
        val prefs = preferences(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val previousAt = prefs.getLong("normal_at", 0)
        manager.cancel(pending(context, false))
        prefs.edit().remove("normal_token").remove("normal_at").apply()
        if (!canSchedule(context)) {
            manager.cancel(pending(context, true))
            prefs.edit().remove("test_token").remove("test_at").putString("message", "未安排：请允许“闹钟和提醒”精确闹钟权限").apply()
            refresh(context); return
        }
        val testAt = prefs.getLong("test_at", 0)
        if (testAt > 0 && testAt >= System.currentTimeMillis() - 120000 && prefs.getLong("test_last", -1) != testAt) {
            schedule(context, BellEvent(Instant.ofEpochMilli(testAt), BellKind.START, "一分钟测试响铃"), true)
        } else {
            prefs.edit().remove("test_token").remove("test_at").apply()
        }
        val database = StudyDatabase.get(context)
        ScheduleRepository(database).initialize()
        val dao = database.scheduleDao()
        val zone = ZoneId.of(dao.settings()?.zoneId ?: "Asia/Shanghai")
        val now = Instant.now()
        val events = BellPlanner.events(dao.listCourses(), now.atZone(zone).toLocalDate(), zone)
        val lastAt = prefs.getLong("normal_last", -1)
        // Opening the app at a boundary must not cancel a just-due alarm before its receiver runs.
        val justDue = events.firstOrNull {
            it.at.toEpochMilli() == previousAt && previousAt <= now.toEpochMilli() && previousAt >= now.toEpochMilli() - 120000 && previousAt != lastAt
        }
        val next = justDue ?: events.firstOrNull { it.at.isAfter(now) && it.at.toEpochMilli() != lastAt }
        if (next != null) schedule(context, next, false)
        prefs.edit().putString("message", if (next == null) "暂无启用课程，未安排课程铃声" else "自动响铃已安排 · 时区 ${zone.id}").apply()
        refresh(context)
    }
    suspend fun reconcile(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { reconcileLocked(context.applicationContext) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                preferences(context).edit().putString("message", "闹钟安排失败，请检查权限后重新安排").apply()
                refresh(context)
            }
        }
    }
    suspend fun testInOneMinute(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!canSchedule(context)) {
                    preferences(context).edit().putString("message", "无法测试：请先允许“闹钟和提醒”权限").apply()
                } else {
                    context.getSystemService(AlarmManager::class.java).cancel(pending(context, true))
                    schedule(context, BellEvent(Instant.now().plusSeconds(60), BellKind.START, "一分钟测试响铃"), true)
                    preferences(context).edit().putString("message", "测试已安排，可返回桌面并锁屏验证").apply()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                preferences(context).edit().putString("message", "测试安排失败，请检查精确闹钟权限").apply()
            }
            refresh(context)
        }
    }
    suspend fun receive(context: Context, intent: Intent) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = preferences(context)
            val test = intent.getBooleanExtra("test", false)
            val prefix = if (test) "test" else "normal"
            val token = intent.getStringExtra("token") ?: return@withLock
            if (prefs.getString(prefix + "_token", null) != token) return@withLock
            val at = intent.getLongExtra("at", 0)
            prefs.edit().remove(prefix + "_token").remove(prefix + "_at").apply()
            val now = System.currentTimeMillis()
            var event: BellEvent? = null
            // Do not replay obsolete alarms after a long shutdown/delay, or an edited/deleted course.
            if (now - at in -2000..120000 && prefs.getLong(prefix + "_last", -1) != at) {
                event = if (test) BellEvent(Instant.ofEpochMilli(at), BellKind.START, "一分钟测试响铃") else {
                    val dao = StudyDatabase.get(context).scheduleDao()
                    val zone = ZoneId.of(dao.settings()?.zoneId ?: "Asia/Shanghai")
                    BellPlanner.events(dao.listCourses(), Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), zone).firstOrNull { it.at.toEpochMilli() == at }
                }
            }
            if (test) prefs.edit().putString("message", if (event != null) "一分钟测试已触发，课程闹钟保持原安排" else "测试已过期，请重新测试").apply()
            if (event != null) {
                prefs.edit().putLong(prefix + "_last", at).apply()
                try {
                    context.startForegroundService(Intent(context, BellPlaybackService::class.java)
                        .putExtra("kind", event.kind.name).putExtra("label", event.label))
                } catch (_: Exception) { recordResult(context, "启动响铃失败：请开启自启动、后台无限制和通知权限") }
            }
            if (!test) {
                try { reconcileLocked(context) }
                catch (_: Exception) {
                    prefs.edit().putString("message", "下次铃声安排失败，请打开应用重新安排").apply()
                }
            }
            refresh(context)
        }
    }
}

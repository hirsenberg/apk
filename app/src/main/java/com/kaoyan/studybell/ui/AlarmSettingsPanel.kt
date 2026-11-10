package com.kaoyan.studybell.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaoyan.studybell.alarm.AlarmScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun AlarmSettingsPanel() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val status by AlarmScheduler.status.collectAsStateWithLifecycle()
    var exact by remember { mutableStateOf(AlarmScheduler.canSchedule(context)) }
    var notifications by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var guidance by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        exact = AlarmScheduler.canSchedule(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        notifications = manager.areNotificationsEnabled() &&
            manager.getNotificationChannel("course_bell")?.importance != NotificationManager.IMPORTANCE_NONE &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        AlarmScheduler.refresh(context)
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    DisposableEffect(lifecycle) {
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun settings(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: Exception) { guidance = "无法自动打开设置，请在系统设置 → 应用管理 → 考研自习铃中手动设置。" }
    }
    fun format(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Shanghai"))
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss"))
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("自动响铃", style = MaterialTheme.typography.titleLarge)
            Text(status.message)
            if (status.next > 0) Text("下次课程铃声：${format(status.next)}（上海时间）")
            if (status.test > 0) Text("测试响铃：${format(status.test)}")
            if (status.lastResult.isNotBlank()) Text("最近状态：${status.lastResult}")
            Text("精确闹钟：${if (exact) "已允许" else "未允许，无法自动响铃"}")
            if (!exact && Build.VERSION.SDK_INT >= 31) Button(onClick = {
                settings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("允许闹钟和提醒") }
            Text("通知：${if (notifications) "已允许" else "未允许，请开启以显示响铃和停止按钮"}")
            if (!notifications) Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                else settings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("允许通知") }
            OutlinedButton(onClick = { settings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("打开通知设置") }
            Button(enabled = exact && !busy, onClick = {
                scope.launch { busy = true; try { AlarmScheduler.testInOneMinute(context.applicationContext) } finally { busy = false } }
            }) { Text("一分钟后测试响铃") }
            OutlinedButton(enabled = !busy, onClick = {
                scope.launch { busy = true; try { AlarmScheduler.reconcile(context.applicationContext); refresh() } finally { busy = false } }
            }) { Text("重新安排课程闹钟") }
            Text("小米平板必设：应用管理 → 考研自习铃 → 允许自启动；省电策略设为“无限制”，允许后台运行。系统闹钟音量需大于0；勿扰模式需允许闹钟。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("打开应用设置（自启动／省电）") }
            OutlinedButton(onClick = { settings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("打开电池优化设置") }
            Text("课程每天重复；名称含“休息、午休、早餐、午餐、晚餐”视为休息。交接只响一次；系统状态栏可能显示下次闹钟图标。重启后首次解锁会恢复安排。强行停止应用后需重新打开。", style = MaterialTheme.typography.bodySmall)
            guidance?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

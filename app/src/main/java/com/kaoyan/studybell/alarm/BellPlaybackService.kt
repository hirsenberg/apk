package com.kaoyan.studybell.alarm

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import com.kaoyan.studybell.MainActivity
import com.kaoyan.studybell.R

/** Lives only for one short bell. No network, background polling, or permanent foreground service. */
class BellPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishBell("响铃已结束（超时保护）") }
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { finishBell("响铃已停止"); return START_NOT_STICKY }
        val label = intent?.getStringExtra("label") ?: "课程铃声"
        val channel = NotificationChannel("course_bell", "课程响铃", NotificationManager.IMPORTANCE_LOW).apply {
            description = "响铃时短暂显示播放状态"; setSound(null, null); enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 201, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 202, Intent(this, BellPlaybackService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "course_bell").setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("考研自习铃").setContentText(label).setContentIntent(open).setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        startForeground(200, notification)
        releasePlayback()
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studybell:playback").apply { acquire(30000) }
        handler.postDelayed(timeout, 25000)
        try {
            val audio = getSystemService(AudioManager::class.java)
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { if (it < 0) finishBell("音频焦点中断") }.build()
            focus = request
            if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                finishBell("无法获得音频焦点，请检查系统闹钟和勿扰设置"); return START_NOT_STICKY
            }
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            val gain = getSharedPreferences("bell_audio", MODE_PRIVATE).getInt("volume_percent", 70).coerceIn(0, 100) / 100f
            next.setVolume(gain, gain)
            val resource = if (intent?.getStringExtra("kind") == BellKind.END.name) R.raw.class_end else R.raw.class_start
            resources.openRawResourceFd(resource).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            next.setOnPreparedListener {
                if (player === it) {
                    try { it.start(); AlarmScheduler.recordResult(this, "$label · 已触发播放") }
                    catch (_: Exception) { finishBell("播放失败，请检查系统闹钟音量") }
                }
            }
            next.setOnCompletionListener { if (player === it) finishBell("$label · 播放完成") }
            next.setOnErrorListener { failed, _, _ -> if (player === failed) finishBell("音频播放失败"); true }
            next.prepareAsync()
        } catch (_: Exception) { finishBell("响铃失败，请检查应用权限和系统闹钟音量") }
        return START_NOT_STICKY
    }
    private fun releasePlayback() {
        handler.removeCallbacks(timeout)
        player?.apply { setOnPreparedListener(null); setOnCompletionListener(null); setOnErrorListener(null); release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
    }
    private fun finishBell(message: String) {
        AlarmScheduler.recordResult(this, message)
        releasePlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { releasePlayback(); super.onDestroy() }
}

package com.kaoyan.studybell.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Main-thread only. One MediaPlayer at a time, no background playback or automatic scheduling. */
class BellPreviewPlayer(context: Context) {
    private val context = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
        .build()
    private var focused = false
    private var player: MediaPlayer? = null
    private var gain = 0.7f
    var status by mutableStateOf("点击按钮试听，支持离线播放")
        private set

    fun setVolume(percent: Int) {
        gain = percent.coerceIn(0, 100) / 100f
        try { player?.setVolume(gain, gain) } catch (_: IllegalStateException) { stop() }
    }

    fun play(@RawRes resource: Int, label: String) {
        stop()
        try {
            focused = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!focused) { status = "暂时无法获得音频焦点，请稍后重试"; return }
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setVolume(gain, gain)
            context.resources.openRawResourceFd(resource).use { file ->
                next.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            next.setOnPreparedListener {
                if (player === it) {
                    try { it.start(); status = "正在试听$label" }
                    catch (_: IllegalStateException) { stop(); status = "播放失败，请重试" }
                }
            }
            next.setOnCompletionListener { if (player === it) { stop(); status = "试听结束" } }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) { stop(); status = "播放失败，请重试" }
                true
            }
            status = "正在加载$label"
            next.prepareAsync()
        } catch (_: Exception) { stop(); status = "音频加载失败，请重试" }
    }

    fun stop() {
        val previous = player
        player = null
        previous?.setOnPreparedListener(null)
        previous?.setOnCompletionListener(null)
        previous?.setOnErrorListener(null)
        previous?.release()
        if (focused) { audioManager.abandonAudioFocusRequest(focus); focused = false }
        status = "已停止试听"
    }
}

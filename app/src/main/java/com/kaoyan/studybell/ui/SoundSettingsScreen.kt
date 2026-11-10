package com.kaoyan.studybell.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kaoyan.studybell.R
import com.kaoyan.studybell.audio.*
import kotlin.math.roundToInt

@Composable
fun SoundSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val settings = remember {
        val preferences = context.getSharedPreferences("bell_audio", Context.MODE_PRIVATE)
        VolumeSettings(object : VolumeStore {
            override fun read() = preferences.getInt("volume_percent", 70)
            override fun write(percent: Int) { preferences.edit().putInt("volume_percent", percent).apply() }
        })
    }
    var volume by remember { mutableIntStateOf(settings.percent) }
    val player = remember { BellPreviewPlayer(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.stop() }
    }
    SideEffect { player.setVolume(volume) }
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack) { Text("返回课程表") }
        Text("铃声设置", style = MaterialTheme.typography.headlineMedium)
        Text("仅用于本地试听，不会自动响铃。", style = MaterialTheme.typography.bodyLarge)
        Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("铃声音量 · $volume%", style = MaterialTheme.typography.titleLarge)
                Slider(value = volume.toFloat(), onValueChange = {
                    settings.set(it.roundToInt()); volume = settings.percent
                }, valueRange = 0f..100f, steps = 99, modifier = Modifier.semantics { contentDescription = "铃声音量"; stateDescription = "$volume%" })
                Text("音量已在本机保存；实际响度还受系统媒体音量影响。0% 为静音。", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { player.play(R.raw.class_start, "上课铃") }) { Text("试听上课铃") }
                Text("Happy bells notification · Mixkit", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { player.play(R.raw.class_end, "下课铃") }) { Text("试听下课铃") }
                Text("Achievement bell · Mixkit", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = player::stop) { Text("停止试听") }
                Text(player.status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

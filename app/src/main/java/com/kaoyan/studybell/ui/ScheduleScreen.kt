package com.kaoyan.studybell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import com.kaoyan.studybell.data.Course
import com.kaoyan.studybell.data.CourseRules
import com.kaoyan.studybell.alarm.AlarmScheduler
import com.kaoyan.studybell.schedule.minuteText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.studybell.schedule.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)
private val dateFormat = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier, model: ScheduleViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    var managing by rememberSaveable { mutableStateOf(false) }
    var soundSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = soundSettings) { soundSettings = false }
    if (soundSettings) {
        SoundSettingsScreen(modifier) { soundSettings = false }
        return
    }
    BackHandler(enabled = managing && editor == null) { managing = false }
    when (val value = state) {
        ScheduleUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ScheduleUiState.Error -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("课程加载失败，请重新打开应用。")
        }
        is ScheduleUiState.Ready -> if (managing) {
            CourseManagement(value.courses, modifier, { managing = false }, { model.openEditor() }, model::openEditor)
        } else ScheduleContent(value.snapshot, modifier, model::openEditor, { model.openEditor() }, { managing = true }, { soundSettings = true })
    }
    editor?.let { CourseEditor(it, model::edit, model::closeEditor, model::saveEditor, model::deleteEditor) }
}

@Composable
private fun CourseManagement(courses: List<Course>, modifier: Modifier, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Course) -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("课程管理", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onAdd) { Text("新增课程") }
        }
        Text("所有课程每天重复，包含停用课程，点击进行编辑。", Modifier.padding(vertical = 12.dp))
        val hasConflict = remember(courses) {
            courses.indices.any { i -> courses.drop(i + 1).any { CourseRules.overlaps(courses[i], it) } }
        }
        if (hasConflict) Text("现有课程存在时间重叠。旧课程已改为每天重复，请编辑调整时间或停用冲突课程；所有课程均已保留。", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (courses.isEmpty()) item { Text("暂无课程，点击新增课程开始安排。") }
            items(courses, key = { it.id }) { course ->
                Card(onClick = { onEdit(course) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(course.name, style = MaterialTheme.typography.titleLarge)
                        Text("${minuteText(course.startMinute)} — ${minuteText(course.endMinute)}${if (course.endMinute < course.startMinute) "（次日）" else ""} · ${if (course.enabled) "已启用" else "已停用"}")
                        Text("每天重复")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleContent(snapshot: ScheduleSnapshot, modifier: Modifier, onEdit: (Course) -> Unit, onAdd: () -> Unit, onManage: () -> Unit, onSound: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp)) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                LazyColumn(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item { Summary(snapshot, onAdd, onManage, onSound) }
                }
                Timeline(snapshot, Modifier.weight(1f), onEdit, onAdd, onManage, onSound)
            }
        } else {
            Timeline(snapshot, Modifier.fillMaxSize(), onEdit, onAdd, onManage, onSound, showSummary = true)
        }
    }
}

@Composable
private fun Summary(snapshot: ScheduleSnapshot, onAdd: () -> Unit, onManage: () -> Unit, onSound: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("考研自习铃", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text("新增课程") }
            OutlinedButton(onClick = onManage) { Text("管理课程") }
        }
        OutlinedButton(onClick = onSound) { Text("铃声与自动响铃设置") }
        val alarmStatus by AlarmScheduler.status.collectAsStateWithLifecycle()
        Text(alarmStatus.message, style = MaterialTheme.typography.bodyMedium)
        Text(snapshot.now.format(dateFormat), style = MaterialTheme.typography.titleMedium)
        Text(snapshot.now.format(clockFormat), style = MaterialTheme.typography.displayMedium)
        Text("时区 · ${snapshot.now.zone.id}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前科目", style = MaterialTheme.typography.labelLarge)
                Text(snapshot.current?.course?.name ?: "自由安排", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                snapshot.current?.let { Text("${it.start.format(timeFormat)} — ${it.end.format(timeFormat)}") }
                HorizontalDivider()
                Text("距下次课程交接", style = MaterialTheme.typography.labelLarge)
                val seconds = (snapshot.remaining.seconds + if (snapshot.remaining.nano > 0) 1 else 0).coerceAtLeast(0)
                Text(if (snapshot.nextBoundary == null) "暂无安排" else String.format(Locale.CHINA, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60), style = MaterialTheme.typography.headlineMedium)
                snapshot.nextBoundary?.let { Text("${it.format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))} · 交接") }
            }
        }
        OutlinedCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下一课程", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(snapshot.next?.course?.name ?: "暂无安排", style = MaterialTheme.typography.titleLarge)
                snapshot.next?.let {
                    val prefix = if (it.start.toLocalDate() == snapshot.now.toLocalDate()) "今天" else if (it.start.toLocalDate() == snapshot.now.toLocalDate().plusDays(1)) "明天" else it.start.format(dateFormat)
                    Text("$prefix ${it.start.format(timeFormat)}")
                }
            }
        }
    }
}

@Composable
private fun Timeline(snapshot: ScheduleSnapshot, modifier: Modifier, onEdit: (Course) -> Unit, onAdd: () -> Unit, onManage: () -> Unit, onSound: () -> Unit, showSummary: Boolean = false) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showSummary) item(key = "summary") { Summary(snapshot, onAdd, onManage, onSound) }
        item(key = "title") {
            Text("今日课程 · ${snapshot.today.size} 节", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (snapshot.today.isEmpty()) item(key = "empty") { Text("今天没有启用的课程，自由安排时间。") }
        items(snapshot.today, key = { it.course.id }) { occurrence ->
            val active = snapshot.current == occurrence
            val completed = !snapshot.now.isBefore(occurrence.end)
            val status = if (active) "进行中" else if (completed) "已结束" else "待开始"
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "编辑课程") { onEdit(occurrence.course) }.semantics { stateDescription = status },
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                border = if (active) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${occurrence.start.format(timeFormat)} — ${occurrence.end.format(timeFormat)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(occurrence.course.name, style = MaterialTheme.typography.titleLarge, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                    Text(status, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

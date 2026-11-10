package com.kaoyan.studybell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kaoyan.studybell.schedule.CourseEditorState

@Composable
fun CourseEditor(draft: CourseEditorState, onChange: (CourseEditorState) -> Unit, onClose: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(0.95f).safeDrawingPadding().imePadding(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp)) {
                Text(if (draft.original.id == 0L) "新增课程" else "编辑课程", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("课程名称") }, enabled = !draft.busy, singleLine = true)
                    OutlinedTextField(draft.start, { onChange(draft.copy(start = it)) }, Modifier.fillMaxWidth(), label = { Text("开始时间（HH:mm）") }, enabled = !draft.busy, singleLine = true)
                    OutlinedTextField(draft.end, { onChange(draft.copy(end = it)) }, Modifier.fillMaxWidth(), label = { Text("结束时间（HH:mm）") }, enabled = !draft.busy, singleLine = true)
                    Text("结束早于开始表示跨夜，课程每天重复。起止时间不能相同。", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用课程", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(draft.enabled, { onChange(draft.copy(enabled = it)) }, enabled = !draft.busy)
                    }
                    Text("每天重复", style = MaterialTheme.typography.bodyMedium)
                    draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (draft.original.id != 0L) {
                        if (draft.confirmDelete) {
                            Text("确定删除「${draft.original.name}」？删除后无法撤销。", color = MaterialTheme.colorScheme.error)
                            Row {
                                TextButton(onClick = { onChange(draft.copy(confirmDelete = false)) }, enabled = !draft.busy) { Text("取消删除") }
                                TextButton(onClick = onDelete, enabled = !draft.busy) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
                            }
                        } else TextButton(onClick = { onChange(draft.copy(confirmDelete = true)) }, enabled = !draft.busy) { Text("删除课程", color = MaterialTheme.colorScheme.error) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (draft.busy) CircularProgressIndicator(Modifier.size(24.dp))
                    TextButton(onClick = onClose, enabled = !draft.busy) { Text("取消") }
                    Button(onClick = onSave, enabled = !draft.busy && !draft.confirmDelete) { Text("保存") }
                }
            }
        }
    }
}

package com.kaoyan.studybell.schedule

import android.app.Application
import com.kaoyan.studybell.alarm.AlarmScheduler
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.studybell.data.Course
import com.kaoyan.studybell.data.SaveResult
import com.kaoyan.studybell.data.ScheduleRepository
import com.kaoyan.studybell.data.StudyDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Ready(val snapshot: ScheduleSnapshot, val courses: List<Course>) : ScheduleUiState
    data object Error : ScheduleUiState
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScheduleRepository(StudyDatabase.get(application))
    private val _editor = MutableStateFlow<CourseEditorState?>(null)
    val editor = _editor.asStateFlow()

    fun openEditor(course: Course = Course(name = "", startMinute = 480, endMinute = 510)) {
        if (_editor.value?.busy == true) return
        _editor.value = CourseEditorState(course, course.name, minuteText(course.startMinute), minuteText(course.endMinute), course.enabled)
    }
    fun edit(value: CourseEditorState) {
        if (_editor.value?.busy != true) _editor.value = value.copy(error = null)
    }
    fun closeEditor() { if (_editor.value?.busy != true) _editor.value = null }

    fun saveEditor() {
        val draft = _editor.value ?: return
        if (draft.busy) return
        val course = draft.toCourse()
        if (course == null) {
            _editor.value = draft.copy(error = "请输入有效时间，格式为 HH:mm，例如 08:30")
            return
        }
        _editor.value = draft.copy(busy = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = repository.save(course)) {
                    is SaveResult.Saved -> {
                        AlarmScheduler.reconcile(getApplication<Application>())
                        _editor.value = null
                    }
                    is SaveResult.Rejected -> _editor.value = draft.copy(error = result.message)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _editor.value = draft.copy(error = "保存失败，请重试，原有课程未被覆盖")
            }
        }
    }

    fun deleteEditor() {
        val draft = _editor.value ?: return
        if (draft.busy || !draft.confirmDelete) return
        _editor.value = draft.copy(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (repository.delete(draft.original.id)) {
                    AlarmScheduler.reconcile(getApplication<Application>())
                    _editor.value = null
                }
                else _editor.value = draft.copy(confirmDelete = false, error = "课程已不存在，请返回后重试")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _editor.value = draft.copy(confirmDelete = false, error = "删除失败，请重试")
            }
        }
    }

    private val ticks = flow {
        while (true) {
            emit(Instant.now())
            delay(1000)
        }
    }
    val state = flow<ScheduleUiState> {
        repository.initialize()
        emitAll(combine(repository.courses, repository.settings, ticks) { courses, settings, now ->
            ScheduleUiState.Ready(ScheduleCalculator.calculate(courses, now, ZoneId.of(settings?.zoneId ?: "Asia/Shanghai")), courses)
        })
    }.catch { emit(ScheduleUiState.Error) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState.Loading)
}

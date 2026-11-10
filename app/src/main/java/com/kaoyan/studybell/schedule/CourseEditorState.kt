package com.kaoyan.studybell.schedule

import com.kaoyan.studybell.data.Course
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

fun minuteText(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

data class CourseEditorState(
    val original: Course,
    val name: String,
    val start: String,
    val end: String,
    val enabled: Boolean,
    val busy: Boolean = false,
    val error: String? = null,
    val confirmDelete: Boolean = false,
) {
    fun toCourse(): Course? = try {
        val format = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
        val startTime = LocalTime.parse(start.trim(), format)
        val endTime = LocalTime.parse(end.trim(), format)
        original.copy(name = name, startMinute = startTime.hour * 60 + startTime.minute,
            endMinute = endTime.hour * 60 + endTime.minute, enabled = enabled, weekdays = 127)
    } catch (_: java.time.DateTimeException) { null }
}

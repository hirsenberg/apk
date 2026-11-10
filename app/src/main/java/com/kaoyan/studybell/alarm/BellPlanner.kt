package com.kaoyan.studybell.alarm

import com.kaoyan.studybell.data.Course
import java.time.*

enum class BellKind { START, END }
data class BellEvent(val at: Instant, val kind: BellKind, val label: String)

object BellPlanner {
    fun isRest(name: String): Boolean = listOf("休息", "午休", "早餐", "午餐", "晚餐").any { name.contains(it) }

    /** One event per timestamp. A starting course takes priority over the ending course. */
    fun events(courses: List<Course>, date: LocalDate, zone: ZoneId): List<BellEvent> {
        val starts = mutableMapOf<Instant, Course>()
        val ends = mutableMapOf<Instant, Course>()
        for (offset in -1L..2L) {
            for (course in courses.filter { it.enabled }.sortedWith(compareBy<Course> { it.sortOrder }.thenBy { it.id })) {
                val day = date.plusDays(offset)
                val start = day.atStartOfDay().plusMinutes(course.startMinute.toLong()).atZone(zone).toInstant()
                val endDate = if (course.endMinute <= course.startMinute) day.plusDays(1) else day
                val end = endDate.atStartOfDay().plusMinutes(course.endMinute.toLong()).atZone(zone).toInstant()
                starts.putIfAbsent(start, course)
                ends.putIfAbsent(end, course)
            }
        }
        return (starts.keys + ends.keys).sorted().map { instant ->
            val starting = starts[instant]
            if (starting != null) BellEvent(instant, if (isRest(starting.name)) BellKind.END else BellKind.START,
                if (isRest(starting.name)) "进入${starting.name}" else "${starting.name} · 上课")
            else BellEvent(instant, BellKind.END, "${ends.getValue(instant).name} · 下课")
        }
    }

    fun next(courses: List<Course>, now: Instant, zone: ZoneId): BellEvent? =
        events(courses, now.atZone(zone).toLocalDate(), zone).firstOrNull { it.at.isAfter(now) }
}

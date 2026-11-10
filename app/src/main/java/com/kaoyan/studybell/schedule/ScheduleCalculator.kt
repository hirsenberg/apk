package com.kaoyan.studybell.schedule

import com.kaoyan.studybell.data.Course
import java.time.*

/** All courses repeat daily; keep legacy weekday columns only for database compatibility. */
data class Occurrence(val course: Course, val start: ZonedDateTime, val end: ZonedDateTime)
data class ScheduleSnapshot(
    val now: ZonedDateTime,
    val today: List<Occurrence>,
    val current: Occurrence?,
    val next: Occurrence?,
    val nextBoundary: ZonedDateTime?,
    val remaining: Duration,
)

object ScheduleCalculator {
    fun calculate(courses: List<Course>, instant: Instant, zone: ZoneId): ScheduleSnapshot {
        val now = instant.atZone(zone)
        val date = now.toLocalDate()
        val occurrences = (-1L..7L).flatMap { offset ->
            val day = date.plusDays(offset)
            courses.filter { it.enabled }
                .map { course ->
                    val start = day.atStartOfDay().plusMinutes(course.startMinute.toLong()).atZone(zone)
                    val endDay = if (course.endMinute <= course.startMinute) day.plusDays(1) else day
                    val end = endDay.atStartOfDay().plusMinutes(course.endMinute.toLong()).atZone(zone)
                    Occurrence(course, start, end)
                }
        }.sortedWith(compareBy<Occurrence> { it.start.toInstant() }.thenBy { it.course.sortOrder }.thenBy { it.course.id })
        val current = occurrences.lastOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
        val next = occurrences.firstOrNull { it.start.isAfter(now) }
        val boundary = occurrences.flatMap { listOf(it.start, it.end) }.filter { it.isAfter(now) }.minByOrNull { it.toInstant() }
        return ScheduleSnapshot(
            now, occurrences.filter { it.start.toLocalDate() == date }, current, next, boundary,
            boundary?.let { Duration.between(now, it) } ?: Duration.ZERO,
        )
    }
}

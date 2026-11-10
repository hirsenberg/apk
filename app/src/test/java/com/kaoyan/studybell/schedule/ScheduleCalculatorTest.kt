package com.kaoyan.studybell.schedule

import com.kaoyan.studybell.data.Course
import com.kaoyan.studybell.data.PresetCourses
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(time: String, courses: List<Course> = PresetCourses.all): ScheduleSnapshot =
        ScheduleCalculator.calculate(courses, LocalDateTime.parse(time).atZone(zone).toInstant(), zone)

    @Test fun presetIsCompleteAndContinuous() {
        val courses = PresetCourses.all
        assertEquals(16, courses.size)
        assertEquals(16, courses.map { it.id }.toSet().size)
        assertEquals(480, courses.first().startMinute)
        assertEquals(1380, courses.last().endMinute)
        courses.zipWithNext().forEach { (a, b) -> assertEquals(a.endMinute, b.startMinute) }
        assertEquals(900, courses.sumOf { it.endMinute - it.startMinute })
    }

    @Test fun beforeDayStarts() {
        val s = at("2026-11-10T07:59:59")
        assertNull(s.current)
        assertEquals("英语单词", s.next?.course?.name)
        assertEquals(Duration.ofSeconds(1), s.remaining)
    }

    @Test fun everyCourseBoundaryIsStartInclusiveEndExclusive() {
        for ((index, course) in PresetCourses.all.withIndex()) {
            val time = LocalDate.of(2026, 11, 10).atStartOfDay().plusMinutes(course.startMinute.toLong())
            val s = at(time.toString())
            assertEquals(course.id, s.current?.course?.id)
            assertEquals(PresetCourses.all.getOrNull(index + 1)?.id ?: PresetCourses.all.first().id, s.next?.course?.id)
            assertEquals(Duration.ofMinutes((course.endMinute - course.startMinute).toLong()), s.remaining)
        }
    }

    @Test fun afterLastCourseLooksAheadToTomorrow() {
        val s = at("2026-11-10T23:00:00")
        assertNull(s.current)
        assertEquals(LocalDate.of(2026, 11, 11), s.next?.start?.toLocalDate())
        assertEquals("英语单词", s.next?.course?.name)
        assertEquals(Duration.ofHours(9), s.remaining)
    }

    @Test fun midnightReplacesTodayAndKeepsNextMorning() {
        val s = at("2026-11-11T00:00:00")
        assertNull(s.current)
        assertEquals(16, s.today.size)
        assertTrue(s.today.all { it.start.toLocalDate() == s.now.toLocalDate() })
        assertEquals(Duration.ofHours(8), s.remaining)
    }

    @Test fun overnightSurvivesMidnightDespiteLegacyWeekdayFlags() {
        val mondayOnly = Course(id = 50, name = "跨夜课程", startMinute = 23 * 60, endMinute = 60, weekdays = 1)
        val s = at("2026-11-10T00:30:00", listOf(mondayOnly)) // Tuesday
        assertEquals(50L, s.current?.course?.id)
        assertEquals(DayOfWeek.MONDAY, s.current?.start?.dayOfWeek)
        assertEquals(Duration.ofMinutes(30), s.remaining)
        assertEquals(1, s.today.size)
    }

    @Test fun legacyWeeklyCourseNowRepeatsEveryDay() {
        val monday = Course(id = 1, name = "周一", startMinute = 480, endMinute = 540, weekdays = 1)
        val s = at("2026-11-09T09:00:00", listOf(monday))
        assertNull(s.current)
        assertEquals(LocalDate.of(2026, 11, 10), s.next?.start?.toLocalDate())
        assertEquals(Duration.ofHours(23), s.remaining)
    }

    @Test fun disabledAndEmptyScheduleHaveNoCountdown() {
        for (courses in listOf(emptyList(), PresetCourses.all.map { it.copy(enabled = false) })) {
            val s = at("2026-11-10T09:00:00", courses)
            assertNull(s.current)
            assertNull(s.next)
            assertNull(s.nextBoundary)
            assertEquals(Duration.ZERO, s.remaining)
        }
    }

    @Test fun countdownIncludesGapsAndUsesConfiguredZone() {
        val courses = listOf(Course(id = 1, name = "A", startMinute = 480, endMinute = 540), Course(id = 2, name = "B", startMinute = 600, endMinute = 660))
        assertEquals(Duration.ofMinutes(30), at("2026-11-10T09:30:00", courses).remaining)
        val s = ScheduleCalculator.calculate(PresetCourses.all, Instant.parse("2026-11-10T00:15:00Z"), zone)
        assertEquals("英语单词", s.current?.course?.name)
        assertEquals(Duration.ofMinutes(15), s.remaining)
    }
}

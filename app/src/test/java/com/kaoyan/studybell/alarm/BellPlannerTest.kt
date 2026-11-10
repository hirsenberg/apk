package com.kaoyan.studybell.alarm

import com.kaoyan.studybell.data.Course
import com.kaoyan.studybell.data.PresetCourses
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BellPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 10)
    @Test fun adjacentTransitionsRingOnlyOnceAndRestUsesEnd() {
        val events = BellPlanner.events(PresetCourses.all, date, zone).filter { it.at.atZone(zone).toLocalDate() == date }
        assertEquals(17, events.size) // 16 starts + final end, no duplicate handoffs
        assertEquals(17, events.map { it.at }.toSet().size)
        fun at(time: String) = events.single { it.at.atZone(zone).toLocalTime() == LocalTime.parse(time) }.kind
        assertEquals(BellKind.START, at("08:00"))
        assertEquals(BellKind.START, at("08:30"))
        assertEquals(BellKind.END, at("12:00"))
        assertEquals(BellKind.END, at("15:00"))
        assertEquals(BellKind.START, at("15:15"))
        assertEquals(BellKind.END, at("18:00"))
        assertEquals(BellKind.END, at("21:45"))
        assertEquals(BellKind.START, at("21:50"))
        assertEquals(BellKind.END, at("23:00"))
    }
    @Test fun lastBellSchedulesTomorrowAndDisabledCoursesDoNotRing() {
        val now = date.atTime(23, 1).atZone(zone).toInstant()
        val next = BellPlanner.next(PresetCourses.all, now, zone)!!
        assertEquals(date.plusDays(1).atTime(8, 0).atZone(zone).toInstant(), next.at)
        assertNull(BellPlanner.next(PresetCourses.all.map { it.copy(enabled = false) }, now, zone))
    }
    @Test fun overnightEndAndNewStartMergeAtMidnightBoundary() {
        val courses = listOf(Course(id = 1, name = "学习", startMinute = 1380, endMinute = 60), Course(id = 2, name = "休息", startMinute = 60, endMinute = 90))
        val at = date.atTime(1, 0).atZone(zone).toInstant()
        val event = BellPlanner.events(courses, date, zone).filter { it.at == at }
        assertEquals(1, event.size)
        assertEquals(BellKind.END, event.single().kind)
    }
    @Test fun deletingOrMovingCourseRemovesOldBoundaries() {
        val now = date.atTime(7, 0).atZone(zone).toInstant()
        val moved = listOf(Course(id = 1, name = "学习", startMinute = 600, endMinute = 660))
        assertEquals(date.atTime(10, 0).atZone(zone).toInstant(), BellPlanner.next(moved, now, zone)?.at)
        assertNull(BellPlanner.next(emptyList(), now, zone))
    }
}

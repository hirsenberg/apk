package com.kaoyan.studybell.schedule

import com.kaoyan.studybell.data.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CourseManagementTest {
    private class Store(initial: List<Course> = emptyList()) : CourseStore {
        val rows = initial.toMutableList()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
        override suspend fun listCourses() = rows.toList()
        override suspend fun insertCourse(course: Course): Long {
            val id = nextId++
            rows.add(course.copy(id = id))
            return id
        }
        override suspend fun updateCourse(course: Course): Int {
            val index = rows.indexOfFirst { it.id == course.id }
            if (index < 0) return 0
            rows[index] = course
            return 1
        }
        override suspend fun deleteCourse(id: Long): Int = if (rows.removeAll { it.id == id }) 1 else 0
    }
    private fun course(id: Long = 0, name: String = "数学", start: Int = 480, end: Int = 540, enabled: Boolean = true, days: Int = 127) =
        Course(id, name, start, end, enabled, days)
    private fun snapshot(store: Store, date: String = "2026-11-09T08:30:00") = ScheduleCalculator.calculate(
        store.rows, LocalDateTime.parse(date).atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"))

    @Test fun addAssignsIdAndDefaultsToEveryDay() = runBlocking {
        val store = Store(); val manager = CourseManager(store)
        val result = manager.save(course(name = "  数学  ")) as SaveResult.Saved
        assertTrue(result.id > 0)
        assertEquals("数学", store.rows.single().name)
        assertEquals(127, store.rows.single().weekdays)
        assertEquals("数学", snapshot(store).current?.course?.name)
    }

    @Test fun editPreservesIdentityAndUpdatesCurrentAndCountdown() = runBlocking {
        val store = Store(listOf(course(id = 9))); val manager = CourseManager(store)
        assertTrue(manager.save(store.rows.single().copy(name = "英语", endMinute = 600)) is SaveResult.Saved)
        assertEquals(1, store.rows.size)
        assertEquals(9L, store.rows.single().id)
        assertEquals("英语", snapshot(store).current?.course?.name)
        assertEquals(Duration.ofMinutes(90), snapshot(store).remaining)
    }

    @Test fun deleteRemovesCurrentAndUpdatesNext() = runBlocking {
        val store = Store(listOf(course(id = 1), course(id = 2, name = "英语", start = 600, end = 660)))
        assertTrue(CourseManager(store).delete(1))
        assertNull(snapshot(store).current)
        assertEquals("英语", snapshot(store).next?.course?.name)
        assertEquals(Duration.ofMinutes(90), snapshot(store).remaining)
    }

    @Test fun deletingUnknownOrUnsavedIdDoesNotDeleteOthers() = runBlocking {
        val store = Store(listOf(course(id = 1))); val manager = CourseManager(store)
        assertFalse(manager.delete(99)); assertFalse(manager.delete(0))
        assertEquals(1, store.rows.size)
    }

    @Test fun disablingAndEnablingRecomputesSchedule() = runBlocking {
        val store = Store(listOf(course(id = 1))); val manager = CourseManager(store)
        assertTrue(manager.save(store.rows.single().copy(enabled = false)) is SaveResult.Saved)
        assertNull(snapshot(store).current); assertNull(snapshot(store).next)
        assertTrue(manager.save(store.rows.single().copy(enabled = true)) is SaveResult.Saved)
        assertEquals(1L, snapshot(store).current?.course?.id)
    }

    @Test fun enablingConflictingCourseIsRejectedWithoutChangingEither() = runBlocking {
        val store = Store(listOf(course(id = 1), course(id = 2, enabled = false))); val before = store.rows.toList()
        val result = CourseManager(store).save(store.rows[1].copy(enabled = true))
        assertTrue(result is SaveResult.Rejected)
        assertEquals(before, store.rows)
    }

    @Test fun legacyWeekdayInputIsNormalizedToDaily() = runBlocking {
        val store = Store(listOf(course(id = 1))); val manager = CourseManager(store)
        assertTrue(manager.save(store.rows.single().copy(weekdays = 2)) is SaveResult.Saved) // Tue only
        assertNotNull(snapshot(store).current)
        assertEquals(127, store.rows.single().weekdays)
        assertEquals(DayOfWeek.TUESDAY, snapshot(store).next?.start?.dayOfWeek)
        assertEquals(1, snapshot(store, "2026-11-10T08:30:00").today.size)
        assertEquals("数学", snapshot(store, "2026-11-10T08:30:00").current?.course?.name)
    }

    @Test fun legacyDifferentWeekdaysStillConflictWhenRepeatingDaily() = runBlocking {
        val store = Store(listOf(course(id = 1, days = 1))); val manager = CourseManager(store)
        assertTrue(manager.save(course(name = "英语", days = 2)) is SaveResult.Rejected)
        assertTrue(manager.save(course(name = "政治", days = 3)) is SaveResult.Rejected)
        assertEquals(1, store.rows.size)
    }

    @Test fun conflictMessageNamesOtherCourseAndNeverOverwritesIt() = runBlocking {
        val store = Store(listOf(course(id = 1, name = "原课程"))); val before = store.rows.toList()
        val result = CourseManager(store).save(course(start = 500, end = 560)) as SaveResult.Rejected
        assertTrue(result.message.contains("原课程")); assertTrue(result.message.contains("重叠"))
        assertEquals(before, store.rows)
    }

    @Test fun editingIntoOverlapKeepsOriginalValues() = runBlocking {
        val store = Store(listOf(course(id = 1), course(id = 2, start = 600, end = 660))); val before = store.rows.toList()
        assertTrue(CourseManager(store).save(store.rows[1].copy(startMinute = 500)) is SaveResult.Rejected)
        assertEquals(before, store.rows)
    }

    @Test fun touchingEndpointsAreAllowed() = runBlocking {
        val store = Store(listOf(course(id = 1)))
        assertTrue(CourseManager(store).save(course(start = 540, end = 600)) is SaveResult.Saved)
        assertEquals(2, store.rows.size)
    }

    @Test fun overnightConflictIsDailyRegardlessOfLegacyWeekdays() {
        val overnight = course(start = 1380, end = 60, days = 64)
        assertTrue(CourseRules.overlaps(overnight, course(start = 30, end = 90, days = 1)))
        assertFalse(CourseRules.overlaps(overnight, course(start = 60, end = 90, days = 1)))
        assertTrue(CourseRules.overlaps(overnight, course(start = 30, end = 90, days = 2)))
    }

    @Test fun disabledOverlapsCanBeSaved() = runBlocking {
        val store = Store(listOf(course(id = 1)))
        assertTrue(CourseManager(store).save(course(enabled = false)) is SaveResult.Saved)
        assertEquals(2, store.rows.size)
    }

    @Test fun invalidInputsNeverWrite() = runBlocking {
        val store = Store(); val manager = CourseManager(store)
        val invalid = listOf(course(name = "  "), course(start = -1), course(end = 1440), course(end = 480), course(end = -1), course(start = 1440))
        invalid.forEach { assertTrue(manager.save(it) is SaveResult.Rejected) }
        assertTrue(store.rows.isEmpty())
    }

    @Test fun editingDeletedCourseDoesNotRecreateItAndAnotherManagerSeesStoredChanges() = runBlocking {
        val store = Store(listOf(course(id = 1)))
        CourseManager(store).delete(1)
        assertTrue(CourseManager(store).save(course(id = 1)) is SaveResult.Rejected)
        assertTrue(CourseManager(store).save(course(name = "新课程")) is SaveResult.Saved)
        val saved = store.rows.single()
        assertTrue(CourseManager(store).save(saved.copy(name = "已修改")) is SaveResult.Saved)
        assertEquals("已修改", store.rows.single().name)
    }

    @Test fun editorParsesStrictTimesAndPreservesMetadata() {
        val original = course(id = 99).copy(sortOrder = 12)
        val draft = CourseEditorState(original, "英语", "23:30", "00:30", false)
        val result = draft.toCourse()!!
        assertEquals(99L, result.id); assertEquals(12, result.sortOrder)
        assertEquals(1410, result.startMinute); assertEquals(30, result.endMinute)
        assertEquals(127, result.weekdays); assertFalse(result.enabled)
        listOf("24:00", "12:60", "abc", "8:00", "08:30:00").forEach { assertNull(draft.copy(start = it).toCourse()) }
    }
}

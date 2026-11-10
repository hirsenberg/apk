package com.kaoyan.studybell.data

/** Repository runs each operation in one Room transaction; tests use an in-memory store. */
interface CourseStore {
    suspend fun listCourses(): List<Course>
    suspend fun insertCourse(course: Course): Long
    suspend fun updateCourse(course: Course): Int
    suspend fun deleteCourse(id: Long): Int
}

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Rejected(val message: String) : SaveResult
}

object CourseRules {
    fun validate(course: Course): String? = when {
        course.name.isBlank() -> "请输入课程名称"
        course.startMinute !in 0..1439 || course.endMinute !in 0..1439 -> "时间必须在 00:00 至 23:59 之间"
        course.startMinute == course.endMinute -> "开始和结束时间不能相同"
        else -> null
    }

    /** Compare daily half-open intervals, including overnight courses. Legacy weekday flags are ignored. */
    fun overlaps(a: Course, b: Course): Boolean {
        if (!a.enabled || !b.enabled) return false
        fun intervals(c: Course): List<Pair<Int, Int>> = (0..6).map {
            val start = it * 1440 + c.startMinute
            start to (start + (c.endMinute - c.startMinute + 1440) % 1440)
        }
        return intervals(a).any { (startA, endA) -> intervals(b).any { (startB, endB) ->
            listOf(-10080, 0, 10080).any { shift -> startA < endB + shift && startB + shift < endA }
        } }
    }
}

class CourseManager(private val store: CourseStore) {
    suspend fun save(input: Course): SaveResult {
        val course = input.copy(name = input.name.trim(), weekdays = 127)
        CourseRules.validate(course)?.let { return SaveResult.Rejected(it) }
        val existing = store.listCourses()
        if (course.id != 0L && existing.none { it.id == course.id }) return SaveResult.Rejected("课程已不存在，请返回后重试")
        val conflict = existing.firstOrNull { (course.id == 0L || it.id != course.id) && CourseRules.overlaps(course, it) }
        if (conflict != null) return SaveResult.Rejected("与「${conflict.name}」时间重叠，请调整时间或先停用课程")
        return if (course.id == 0L) SaveResult.Saved(store.insertCourse(course)) else {
            if (store.updateCourse(course) == 1) SaveResult.Saved(course.id)
            else SaveResult.Rejected("课程已不存在，请返回后重试")
        }
    }

    suspend fun delete(id: Long): Boolean = id > 0 && store.deleteCourse(id) == 1
}

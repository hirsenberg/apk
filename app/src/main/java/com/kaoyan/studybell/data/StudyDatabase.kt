package com.kaoyan.studybell.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao : CourseStore {
    @Query("UPDATE courses SET weekdays = 127 WHERE weekdays != 127")
    suspend fun makeCoursesDaily()

    @Query("SELECT * FROM courses ORDER BY startMinute, sortOrder, id")
    override suspend fun listCourses(): List<Course>

    @Insert
    override suspend fun insertCourse(course: Course): Long

    @Update
    override suspend fun updateCourse(course: Course): Int

    @Query("DELETE FROM courses WHERE id = :id")
    override suspend fun deleteCourse(id: Long): Int

    @Query("SELECT * FROM courses ORDER BY startMinute, sortOrder, id")
    fun observeCourses(): Flow<List<Course>>

    @Query("SELECT * FROM schedule_settings WHERE id = 1")
    fun observeSettings(): Flow<ScheduleSettings?>

    @Query("SELECT * FROM schedule_settings WHERE id = 1")
    suspend fun settings(): ScheduleSettings?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCourses(courses: List<Course>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ScheduleSettings)
}

@Database(entities = [Course::class, ScheduleSettings::class], version = 1, exportSchema = true)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile private var instance: StudyDatabase? = null
        fun get(context: Context): StudyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StudyDatabase::class.java, "study-bell.db")
                .build().also { instance = it }
        }
    }
}

class ScheduleRepository(private val database: StudyDatabase) {
    suspend fun save(course: Course): SaveResult = database.withTransaction {
        CourseManager(database.scheduleDao()).save(course)
    }

    suspend fun delete(id: Long): Boolean = database.withTransaction {
        CourseManager(database.scheduleDao()).delete(id)
    }

    val courses = database.scheduleDao().observeCourses()
    val settings = database.scheduleDao().observeSettings()

    /** Seed and marker commit together; deleting/turning off courses later must not re-seed them. */
    suspend fun initialize() = database.withTransaction {
        val dao = database.scheduleDao()
        val settings = dao.settings() ?: ScheduleSettings()
        if (!settings.presetsInitialized) {
            dao.insertCourses(PresetCourses.all)
            dao.saveSettings(settings.copy(presetsInitialized = true))
        }
        dao.makeCoursesDaily()
    }
}

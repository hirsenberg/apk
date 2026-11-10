package com.kaoyan.studybell.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Minutes since midnight; end <= start denotes an overnight course. Legacy weekday column is retained for Room compatibility; all courses now repeat daily. */
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val weekdays: Int = 127,
    val sortOrder: Int = 0,
)

@Entity(tableName = "schedule_settings")
data class ScheduleSettings(
    @PrimaryKey val id: Int = 1,
    val zoneId: String = "Asia/Shanghai",
    val presetsInitialized: Boolean = false,
)

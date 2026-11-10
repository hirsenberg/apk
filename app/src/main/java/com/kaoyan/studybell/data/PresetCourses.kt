package com.kaoyan.studybell.data

object PresetCourses {
    val all: List<Course> = listOf(
        Triple("08:00", "08:30", "英语单词"),
        Triple("08:30", "11:30", "数学一主训练"),
        Triple("11:30", "12:00", "数学复盘"),
        Triple("12:00", "13:30", "午休"),
        Triple("13:30", "15:00", "数学专项补弱"),
        Triple("15:00", "15:15", "休息"),
        Triple("15:15", "17:15", "专业课"),
        Triple("17:15", "18:00", "政治选择题"),
        Triple("18:00", "19:00", "晚餐"),
        Triple("19:00", "19:40", "单词与错句回测"),
        Triple("19:40", "20:00", "休息"),
        Triple("20:00", "21:00", "英语作文"),
        Triple("21:00", "21:45", "英语翻译"),
        Triple("21:45", "21:50", "休息"),
        Triple("21:50", "22:20", "政治背诵"),
        Triple("22:20", "23:00", "专业课背诵"),
    ).mapIndexed { index, (start, end, name) ->
        fun minute(value: String) = java.time.LocalTime.parse(value).let { it.hour * 60 + it.minute }
        Course(id = index + 1L, name = name, startMinute = minute(start), endMinute = minute(end), sortOrder = index)
    }
}

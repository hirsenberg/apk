package com.kaoyan.studybell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kaoyan.studybell.ui.ScheduleScreen
import com.kaoyan.studybell.ui.theme.StudyBellTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyBellTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScheduleScreen(Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

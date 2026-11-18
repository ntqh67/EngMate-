package com.engmate.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.engmate.app.core.ui.theme.EngMateTheme
import com.engmate.app.core.ui.theme.SplashScreen
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import com.engmate.app.navigation.EngMateApp
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EngMateTheme {
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(800); showSplash = false }
            if (showSplash) SplashScreen() else EngMateApp()
        } }
    }
}

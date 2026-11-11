package com.engmate.app.core.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Colors = lightColorScheme(primary = Color(0xFF2C71B7), secondary = Color(0xFFC55F09),
    tertiary = Color(0xFF694CCD), background = Color(0xFFF3F9FF), surface = Color.White)
@Composable fun EngMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

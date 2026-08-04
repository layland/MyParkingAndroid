package com.my.parking.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E4D8F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF2E6F95),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF1F4F9),
    onSurface = Color(0xFF1A1C20),
    onSurfaceVariant = Color(0xFF5D626B),
    error = Color(0xFF9A241B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002B62),
    primaryContainer = Color(0xFF17457F),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF9CCEFF),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF252A33),
    onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC3C7CF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MyParkingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

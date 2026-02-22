package com.aufait.alpha.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EnFaitLightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAF5),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF5F8FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF2),
    onSurfaceVariant = Color(0xFF475569),
    outlineVariant = Color(0xFFD0D8E0)
)

@Composable
fun EnFaitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EnFaitLightColors,
        content = content
    )
}

package com.aufait.alpha.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnFaitLightColors = lightColorScheme(
    primary = Color(0xFF0B7668),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF6F0),
    onPrimaryContainer = Color(0xFF032D28),
    secondary = Color(0xFF1D5ED8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color(0xFF122B63),
    tertiary = Color(0xFF0B90B1),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F0F8),
    onTertiaryContainer = Color(0xFF083844),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF4),
    onSurfaceVariant = Color(0xFF465467),
    outline = Color(0xFF6E7E93),
    outlineVariant = Color(0xFFD2DAE4),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF55160F)
)

private val EnFaitTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val EnFaitShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun EnFaitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EnFaitLightColors,
        typography = EnFaitTypography,
        shapes = EnFaitShapes,
        content = content
    )
}

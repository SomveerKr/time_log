package com.timelog.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF286747),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EFDF),
    onPrimaryContainer = Color(0xFF17231D),
    secondary = Color(0xFF56645B),
    secondaryContainer = Color(0xFFD7EFDF),
    onSecondaryContainer = Color(0xFF17231D),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF17231D),
    surface = Color.White,
    onSurface = Color(0xFF17231D),
    surfaceVariant = Color(0xFFEAF0EB),
    onSurfaceVariant = Color(0xFF56645B),
    surfaceContainer = Color(0xFFF7F8F5),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEAF0EB),
    outline = Color(0xFF56645B),
    outlineVariant = Color(0xFFDDE5DE),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91D6AE),
    onPrimary = Color(0xFF102B1D),
    primaryContainer = Color(0xFF234B35),
    onPrimaryContainer = Color(0xFFE8EFEA),
    secondary = Color(0xFFAFBEB4),
    secondaryContainer = Color(0xFF234B35),
    onSecondaryContainer = Color(0xFFE8EFEA),
    background = Color(0xFF111714),
    onBackground = Color(0xFFE8EFEA),
    surface = Color(0xFF1B241F),
    onSurface = Color(0xFFE8EFEA),
    surfaceVariant = Color(0xFF26372C),
    onSurfaceVariant = Color(0xFFAFBEB4),
    surfaceContainer = Color(0xFF111714),
    surfaceContainerLow = Color(0xFF1B241F),
    surfaceContainerHigh = Color(0xFF26372C),
    outline = Color(0xFFAFBEB4),
    outlineVariant = Color(0xFF34463A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF601410),
)

private val TimeLogTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
)

@Composable
fun TimeLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = TimeLogTypography,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp)),
        content = content,
    )
}

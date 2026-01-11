package com.tej.protojunc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Protojunc Sovereign Palette
val DarkCharcoal = Color(0xFF121212)
val SurfaceGrey = Color(0xFF1E1E1E)
val PrimaryTeal = Color(0xFF00CEC9)
val SecondaryAmber = Color(0xFFFAB1A0)
val CyberGreen = Color(0xFF00FF41)
val ErrorRed = Color(0xFFFF5252)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    secondary = SecondaryAmber,
    tertiary = CyberGreen,
    background = DarkCharcoal,
    surface = SurfaceGrey,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    secondary = SecondaryAmber,
    tertiary = CyberGreen,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    error = ErrorRed
)

val ProtojuncTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun ProtojuncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ProtojuncTypography,
        content = content
    )
}

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedDarkColorScheme = darkColorScheme(
    primary = MedMint,
    onPrimary = Color(0xFF00382B),
    primaryContainer = MedMintContainer,
    onPrimaryContainer = MedMint,
    
    secondary = MedLavender,
    onSecondary = Color(0xFF3B1E61),
    secondaryContainer = MedLavenderContainer,
    onSecondaryContainer = MedLavender,
    
    tertiary = MedCyan,
    onTertiary = Color(0xFF003547),
    tertiaryContainer = MedCyanContainer,
    onTertiaryContainer = MedCyan,
    
    background = MedBackgroundDark,
    onBackground = MedTextPrimaryDark,
    
    surface = MedSurfaceDark,
    onSurface = MedTextPrimaryDark,
    surfaceVariant = MedSurfaceVariantDark,
    onSurfaceVariant = MedTextSecondaryDark,
    
    outline = MedBorderDark,
    outlineVariant = Color(0xFF132A4C),
    
    error = MedCoral,
    onError = Color(0xFF49000C),
    errorContainer = MedCoralContainer,
    onErrorContainer = MedCoral
)

private val MedLightColorScheme = lightColorScheme(
    primary = MedMintLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    
    secondary = MedLavenderLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF075985),
    
    background = MedBackgroundLight,
    onBackground = MedTextPrimaryLight,
    
    surface = MedSurfaceLight,
    onSurface = MedTextPrimaryLight,
    surfaceVariant = MedSurfaceVariantLight,
    onSurfaceVariant = MedTextSecondaryLight,
    
    outline = MedBorderLight,
    
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun MedStudyTheme(
    darkTheme: Boolean = true, // Default to stunning dark theme matching brief #010A19
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MedDarkColorScheme else MedLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for template compatibility if needed
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MedStudyTheme(darkTheme = darkTheme, content = content)
}

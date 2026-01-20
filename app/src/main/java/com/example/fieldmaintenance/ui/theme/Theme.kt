package com.example.fieldmaintenance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OutlookDarkPrimary,
    secondary = OutlookDarkPrimary,
    tertiary = OutlookDarkPrimaryVariant,
    background = OutlookDarkBackground,
    surface = OutlookDarkSurface,
    surfaceVariant = OutlookDarkSurfaceVariant,
    outline = OutlookDarkOutline,
    outlineVariant = OutlookDarkOutlineVariant,
    error = OutlookDarkError,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = OutlookDarkTextPrimary,
    onSurface = OutlookDarkTextPrimary,
    onSurfaceVariant = OutlookDarkTextSecondary,
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = OutlookLightPrimary,
    secondary = OutlookLightPrimary,
    tertiary = OutlookLightPrimaryVariant,
    background = OutlookLightBackground,
    surface = OutlookLightSurface,
    surfaceVariant = OutlookLightSurfaceVariant,
    outline = OutlookLightOutline,
    outlineVariant = OutlookLightOutlineVariant,
    error = OutlookLightError,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = OutlookLightTextPrimary,
    onSurface = OutlookLightTextPrimary,
    onSurfaceVariant = OutlookLightTextSecondary,
    onError = White
)

@Composable
fun FieldMaintenanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep brand color stable; ignore dynamic colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

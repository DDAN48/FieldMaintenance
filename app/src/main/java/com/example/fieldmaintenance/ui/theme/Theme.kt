package com.example.fieldmaintenance.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlueDark,
    secondary = BrandBlueDark,
    tertiary = BrandBlueDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    onPrimary = DarkOnSurface,
    onSecondary = DarkOnSurface,
    onTertiary = DarkOnSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorDark,
    onError = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlueLight,
    secondary = BrandBlueLight,
    tertiary = BrandBlueLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightDivider,
    onPrimary = LightSurface,
    onSecondary = LightSurface,
    onTertiary = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorLight,
    onError = LightSurface
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

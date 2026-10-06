package com.wais.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4EDEA3),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF10B981),
    onPrimaryContainer = Color(0xFF00422B),
    secondary = Color(0xFF9ED2B5),
    onSecondary = Color(0xFF013824),
    secondaryContainer = Color(0xFF21523C),
    onSecondaryContainer = Color(0xFF91C4A8),
    tertiary = Color(0xFFFFB3AF),
    onTertiary = Color(0xFF650911),
    tertiaryContainer = Color(0xFFFC7C78),
    onTertiaryContainer = Color(0xFF711419),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131313),
    onBackground = Color(0xFFE5E2E1),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFE5E2E1),
    surfaceVariant = Color(0xFF201F1F),
    onSurfaceVariant = Color(0xFFBBCABF),
    outline = Color(0xFF86948A),
    outlineVariant = Color(0xFF3C4A42),
    inverseSurface = Color(0xFFE5E2E1),
    inverseOnSurface = Color(0xFF313030),
    inversePrimary = Color(0xFF006C49),
    surfaceTint = Color(0xFF4EDEA3),
    scrim = Color(0xFF131313)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4EDEA3),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF00422B),
    secondary = Color(0xFF5C8A6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8E6D4),
    onSecondaryContainer = Color(0xFF1A3D2B),
    tertiary = Color(0xFFC4423A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD7),
    onTertiaryContainer = Color(0xFF5C1210),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5C5C5C),
    outline = Color(0xFF7A7A7A),
    outlineVariant = Color(0xFFC4C4C4),
    inverseSurface = Color(0xFF313030),
    inverseOnSurface = Color(0xFFF5F4F4),
    inversePrimary = Color(0xFF6FFBBE),
    surfaceTint = Color(0xFF4EDEA3),
    scrim = Color(0xFF000000)
)

@Composable
fun UntitledTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

package com.hfremovalsadelaide.invoices.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.settings.ThemeMode

private val HFRemovalsLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = PureWhite,
    primaryContainer = ForestSoftTint,
    onPrimaryContainer = ForestGreen,
    secondary = GoldAccent,
    onSecondary = ForestCanopy,
    secondaryContainer = GoldSoftTint,
    onSecondaryContainer = GoldDeep,
    tertiary = ForestActive,
    onTertiary = PureWhite,
    background = SageSurface,
    onBackground = DeepInk,
    surface = WarmWhite,
    onSurface = DeepInk,
    surfaceVariant = SageSurface,
    onSurfaceVariant = MutedSage,
    outline = SageOutline,
    outlineVariant = SageOutline,
    error = ErrorCrimson,
    onError = PureWhite,
    errorContainer = ErrorContainerTint,
    onErrorContainer = ErrorCrimson
)

private val HFRemovalsDarkColorScheme = darkColorScheme(
    primary = DarkForestPrimary,
    onPrimary = PureWhite,
    primaryContainer = ForestActive,
    onPrimaryContainer = DarkInk,
    secondary = DarkGoldAccent,
    onSecondary = DarkCanvas,
    secondaryContainer = Color(0xFF3B2E14),
    onSecondaryContainer = DarkGoldAccent,
    tertiary = GoldAccent,
    onTertiary = DarkCanvas,
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = DarkErrorContainer,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkError
)

val HFRemovalsShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HFRemovalsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) HFRemovalsDarkColorScheme else HFRemovalsLightColorScheme,
        typography = HFRemovalsTypography,
        shapes = HFRemovalsShapes,
        content = content
    )
}

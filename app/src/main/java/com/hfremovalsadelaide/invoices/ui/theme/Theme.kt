package com.hfremovalsadelaide.invoices.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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
    surface = PureWhite,
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

val HFRemovalsShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HFRemovalsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HFRemovalsLightColorScheme,
        typography = HFRemovalsTypography,
        shapes = HFRemovalsShapes,
        content = content
    )
}

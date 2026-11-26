package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val InkCleanLightScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = SheetWhite,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = CobaltOnContainer,
    secondary = EmeraldClean,
    onSecondary = SheetWhite,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldOnContainer,
    tertiary = AmberEnclave,
    onTertiary = SheetWhite,
    tertiaryContainer = AmberContainer,
    background = PaperIvory,
    onBackground = PrussianInk,
    surface = SheetWhite,
    onSurface = PrussianInk,
    surfaceVariant = WarmParchment,
    onSurfaceVariant = SlateInk,
    surfaceContainerLow = PaperIvory,
    surfaceContainer = WarmParchment,
    surfaceContainerHigh = WarmParchmentDeep,
    outline = SubtleHairline,
    outlineVariant = SubtleHairline,
    error = ErrorCrimson,
    onError = SheetWhite,
    errorContainer = ErrorContainerLight,
)

private val InkCleanDarkScheme = darkColorScheme(
    primary = CobaltLight,
    onPrimary = DeskCharcoal,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = CobaltOnContainerDark,
    secondary = EmeraldLight,
    onSecondary = DeskCharcoal,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = EmeraldOnContainerDark,
    background = DeskCharcoal,
    onBackground = WarmPaperText,
    surface = DeskSurface,
    onSurface = WarmPaperText,
    surfaceVariant = DeskSurfaceElevated,
    onSurfaceVariant = MutedSlateText,
    surfaceContainerLow = DeskSurface,
    surfaceContainer = DeskSurfaceElevated,
    surfaceContainerHigh = Color(0xFF283246),
    outline = DarkHairline,
    outlineVariant = DarkHairline,
    error = ErrorLight,
    onError = DeskCharcoal,
    errorContainer = ErrorContainerDark,
)

private val InkCleanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun InkCleanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) InkCleanDarkScheme else InkCleanLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = InkCleanTypography,
        shapes = InkCleanShapes,
        content = content,
    )
}

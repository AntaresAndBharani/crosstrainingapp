package com.fractanomics.crosstraining.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = VoltGreenPrimary,
    onPrimary = VoltGreenOnPrimary,
    primaryContainer = VoltGreenContainerDark,
    onPrimaryContainer = VoltGreenOnContainerDark,
    secondary = ElectricCyanSecondary,
    onSecondary = ElectricCyanOnSecondary,
    secondaryContainer = ElectricCyanContainerDark,
    onSecondaryContainer = ElectricCyanOnContainerDark,
    tertiary = SolarAmberTertiary,
    onTertiary = SolarAmberOnTertiary,
    tertiaryContainer = SolarAmberContainerDark,
    onTertiaryContainer = SolarAmberOnContainerDark,
    background = CarbonBackground,
    onBackground = TextHighContrastWhite,
    surface = CarbonSurface,
    onSurface = TextHighContrastWhite,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextHighContrastSilver,
    surfaceContainer = CarbonSurfaceContainer,
    surfaceContainerHigh = CarbonSurfaceContainerHigh,
    outline = BorderHighContrastSlate,
    outlineVariant = BorderHighContrastSubtle,
    error = HighVisErrorDark,
    onError = HighVisOnErrorDark,
    errorContainer = HighVisErrorContainerDark,
    onErrorContainer = HighVisOnErrorContainerDark
)

private val LightColors = lightColorScheme(
    primary = VoltGreenPrimaryLight,
    onPrimary = VoltGreenOnPrimaryLight,
    primaryContainer = VoltGreenContainerLight,
    onPrimaryContainer = VoltGreenOnContainerLight,
    secondary = ElectricCyanSecondaryLight,
    onSecondary = ElectricCyanOnSecondaryLight,
    secondaryContainer = ElectricCyanContainerLight,
    onSecondaryContainer = ElectricCyanOnContainerLight,
    tertiary = SolarAmberTertiaryLight,
    onTertiary = SolarAmberOnTertiaryLight,
    tertiaryContainer = SolarAmberContainerLight,
    onTertiaryContainer = SolarAmberOnContainerLight,
    background = LightBackground,
    onBackground = TextLightModeBlack,
    surface = LightSurface,
    onSurface = TextLightModeBlack,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextLightModeSlate,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = BorderLightModeSlate,
    outlineVariant = BorderLightModeSubtle,
    error = HighVisErrorLight,
    onError = HighVisOnErrorLight,
    errorContainer = HighVisErrorContainerLight,
    onErrorContainer = HighVisOnErrorContainerLight
)

@Composable
fun CrossTrainingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // High contrast & brand integrity preserved
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

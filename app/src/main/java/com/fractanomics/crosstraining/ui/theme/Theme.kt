package com.fractanomics.crosstraining.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Available theme modes for the application.
 */
enum class AppThemeMode(
    val title: String,
    val description: String
) {
    LIGHT("Light", "Clean athletic light mode (Default)"),
    DARK("Dark", "Sleek charcoal dark mode"),
    LIGHT_HIGH_CONTRAST("Light High Contrast", "Maximum daylight contrast & bold outlines"),
    DARK_HIGH_CONTRAST("Dark High Contrast", "True OLED pitch black with neon volt accents");

    val isDark: Boolean
        get() = this == DARK || this == DARK_HIGH_CONTRAST
}

// 1. Standard Light Color Scheme (Default)
val LightColors = lightColorScheme(
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

// 2. Standard Dark Color Scheme
val DarkColors = darkColorScheme(
    primary = VoltGreenPrimaryDark,
    onPrimary = VoltGreenOnPrimaryDark,
    primaryContainer = VoltGreenContainerDark,
    onPrimaryContainer = VoltGreenOnContainerDark,
    secondary = ElectricCyanSecondaryDark,
    onSecondary = ElectricCyanOnSecondaryDark,
    secondaryContainer = ElectricCyanContainerDark,
    onSecondaryContainer = ElectricCyanOnContainerDark,
    tertiary = SolarAmberTertiaryDark,
    onTertiary = SolarAmberOnTertiaryDark,
    tertiaryContainer = SolarAmberContainerDark,
    onTertiaryContainer = SolarAmberOnContainerDark,
    background = DarkBackground,
    onBackground = TextDarkModeWhite,
    surface = DarkSurface,
    onSurface = TextDarkModeWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextDarkModeSlate,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = BorderDarkModeSlate,
    outlineVariant = BorderDarkModeSubtle,
    error = HighVisErrorDark,
    onError = HighVisOnErrorDark,
    errorContainer = HighVisErrorContainerDark,
    onErrorContainer = HighVisOnErrorContainerDark
)

// 3. Light High Contrast Scheme (WCAG AAA)
val LightHighContrastColors = lightColorScheme(
    primary = VoltGreenPrimaryLightHc,
    onPrimary = VoltGreenOnPrimaryLightHc,
    primaryContainer = VoltGreenContainerLightHc,
    onPrimaryContainer = VoltGreenOnContainerLightHc,
    secondary = ElectricCyanSecondaryLightHc,
    onSecondary = ElectricCyanOnSecondaryLightHc,
    secondaryContainer = ElectricCyanContainerLightHc,
    onSecondaryContainer = ElectricCyanOnContainerLightHc,
    tertiary = SolarAmberTertiaryLightHc,
    onTertiary = SolarAmberOnTertiaryLightHc,
    tertiaryContainer = SolarAmberContainerLightHc,
    onTertiaryContainer = SolarAmberOnContainerLightHc,
    background = LightHcBackground,
    onBackground = TextLightHcBlack,
    surface = LightHcSurface,
    onSurface = TextLightHcBlack,
    surfaceVariant = LightHcSurfaceVariant,
    onSurfaceVariant = TextLightHcSlate,
    surfaceContainer = LightHcSurfaceContainer,
    surfaceContainerHigh = LightHcSurfaceContainerHigh,
    outline = BorderLightHcSolid,
    outlineVariant = BorderLightHcDivider,
    error = HighVisErrorLightHc,
    onError = HighVisOnErrorLightHc,
    errorContainer = HighVisErrorContainerLightHc,
    onErrorContainer = HighVisOnErrorContainerLightHc
)

// 4. Dark High Contrast Scheme (WCAG AAA / OLED Pitch Black)
val DarkHighContrastColors = darkColorScheme(
    primary = VoltGreenPrimaryDarkHc,
    onPrimary = VoltGreenOnPrimaryDarkHc,
    primaryContainer = VoltGreenContainerDarkHc,
    onPrimaryContainer = VoltGreenOnContainerDarkHc,
    secondary = ElectricCyanSecondaryDarkHc,
    onSecondary = ElectricCyanOnSecondaryDarkHc,
    secondaryContainer = ElectricCyanContainerDarkHc,
    onSecondaryContainer = ElectricCyanOnContainerDarkHc,
    tertiary = SolarAmberTertiaryDarkHc,
    onTertiary = SolarAmberOnTertiaryDarkHc,
    tertiaryContainer = SolarAmberContainerDarkHc,
    onTertiaryContainer = SolarAmberOnContainerDarkHc,
    background = DarkHcBackground,
    onBackground = TextDarkHcWhite,
    surface = DarkHcSurface,
    onSurface = TextDarkHcWhite,
    surfaceVariant = DarkHcSurfaceVariant,
    onSurfaceVariant = TextDarkHcSilver,
    surfaceContainer = DarkHcSurfaceContainer,
    surfaceContainerHigh = DarkHcSurfaceContainerHigh,
    outline = BorderDarkHcSolid,
    outlineVariant = BorderDarkHcDivider,
    error = HighVisErrorDarkHc,
    onError = HighVisOnErrorDarkHc,
    errorContainer = HighVisErrorContainerDarkHc,
    onErrorContainer = HighVisOnErrorContainerDarkHc
)

@Composable
fun CrossTrainingTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (themeMode.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == AppThemeMode.DARK -> DarkColors
        themeMode == AppThemeMode.LIGHT_HIGH_CONTRAST -> LightHighContrastColors
        themeMode == AppThemeMode.DARK_HIGH_CONTRAST -> DarkHighContrastColors
        else -> LightColors // Default is Light Mode
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

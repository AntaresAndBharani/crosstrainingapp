package com.fractanomics.crosstraining.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * Available theme modes for the application.
 */
enum class AppThemeMode(
    val title: String,
    val description: String
) {
    LIGHT("Light", "Porcelain ivory & deep bronze daylight mode"),
    DARK("Dark", "Graphite & warm bronze precision mode (Default)"),
    LIGHT_HIGH_CONTRAST("Light High Contrast", "Pure white WCAG AAA daylight contrast"),
    DARK_HIGH_CONTRAST("Dark High Contrast", "OLED pitch black with high-contrast bronze & sage");

    val isDark: Boolean
        get() = this == DARK || this == DARK_HIGH_CONTRAST
}

// 1. Standard Light Color Scheme (Porcelain Ivory & Deep Bronze)
val LightColors = lightColorScheme(
    primary = BronzePrimaryLight,
    onPrimary = BronzeOnPrimaryLight,
    primaryContainer = BronzeContainerLight,
    onPrimaryContainer = BronzeOnContainerLight,
    secondary = TitaniumSecondaryLight,
    onSecondary = TitaniumOnSecondaryLight,
    secondaryContainer = TitaniumContainerLight,
    onSecondaryContainer = TitaniumOnContainerLight,
    tertiary = SageTertiaryLight,
    onTertiary = SageOnTertiaryLight,
    tertiaryContainer = SageContainerLight,
    onTertiaryContainer = SageOnContainerLight,
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
    error = CrimsonErrorLight,
    onError = CrimsonOnErrorLight,
    errorContainer = CrimsonErrorContainerLight,
    onErrorContainer = CrimsonOnErrorContainerLight
)

// 2. Standard Dark Color Scheme (Default — Graphite & Warm Bronze)
val DarkColors = darkColorScheme(
    primary = BronzePrimaryDark,
    onPrimary = BronzeOnPrimaryDark,
    primaryContainer = BronzeContainerDark,
    onPrimaryContainer = BronzeOnContainerDark,
    secondary = TitaniumSecondaryDark,
    onSecondary = TitaniumOnSecondaryDark,
    secondaryContainer = TitaniumContainerDark,
    onSecondaryContainer = TitaniumOnContainerDark,
    tertiary = SageTertiaryDark,
    onTertiary = SageOnTertiaryDark,
    tertiaryContainer = SageContainerDark,
    onTertiaryContainer = SageOnContainerDark,
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
    error = CrimsonErrorDark,
    onError = CrimsonOnErrorDark,
    errorContainer = CrimsonErrorContainerDark,
    onErrorContainer = CrimsonOnErrorContainerDark
)

// 3. Light High Contrast Scheme (WCAG AAA)
val LightHighContrastColors = lightColorScheme(
    primary = BronzePrimaryLightHc,
    onPrimary = BronzeOnPrimaryLightHc,
    primaryContainer = BronzeContainerLightHc,
    onPrimaryContainer = BronzeOnContainerLightHc,
    secondary = TitaniumSecondaryLightHc,
    onSecondary = TitaniumOnSecondaryLightHc,
    secondaryContainer = TitaniumContainerLightHc,
    onSecondaryContainer = TitaniumOnContainerLightHc,
    tertiary = SageTertiaryLightHc,
    onTertiary = SageOnTertiaryLightHc,
    tertiaryContainer = SageContainerLightHc,
    onTertiaryContainer = SageOnContainerLightHc,
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
    error = CrimsonErrorLightHc,
    onError = CrimsonOnErrorLightHc,
    errorContainer = CrimsonErrorContainerLightHc,
    onErrorContainer = CrimsonOnErrorContainerLightHc
)

// 4. Dark High Contrast Scheme (WCAG AAA / OLED Pitch Black)
val DarkHighContrastColors = darkColorScheme(
    primary = BronzePrimaryDarkHc,
    onPrimary = BronzeOnPrimaryDarkHc,
    primaryContainer = BronzeContainerDarkHc,
    onPrimaryContainer = BronzeOnContainerDarkHc,
    secondary = TitaniumSecondaryDarkHc,
    onSecondary = TitaniumOnSecondaryDarkHc,
    secondaryContainer = TitaniumContainerDarkHc,
    onSecondaryContainer = TitaniumOnContainerDarkHc,
    tertiary = SageTertiaryDarkHc,
    onTertiary = SageOnTertiaryDarkHc,
    tertiaryContainer = SageContainerDarkHc,
    onTertiaryContainer = SageOnContainerDarkHc,
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
    error = CrimsonErrorDarkHc,
    onError = CrimsonOnErrorDarkHc,
    errorContainer = CrimsonErrorContainerDarkHc,
    onErrorContainer = CrimsonOnErrorContainerDarkHc
)

/**
 * Returns whether the active in-app [MaterialTheme.colorScheme] uses a dark background,
 * ensuring UI badges and auxiliary surfaces follow the user's selected [AppThemeMode]
 * even when it differs from the device OS dark mode setting.
 */
@Composable
fun isAppDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun CrossTrainingTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (themeMode.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == AppThemeMode.LIGHT -> LightColors
        themeMode == AppThemeMode.LIGHT_HIGH_CONTRAST -> LightHighContrastColors
        themeMode == AppThemeMode.DARK_HIGH_CONTRAST -> DarkHighContrastColors
        else -> DarkColors // Default is Dark Graphite Mode
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

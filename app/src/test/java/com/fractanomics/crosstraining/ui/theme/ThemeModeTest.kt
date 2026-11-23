package com.fractanomics.crosstraining.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.fractanomics.crosstraining.data.DataModeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeModeTest {

    @Test
    fun defaultThemeMode_isDarkGraphite() {
        val defaultManager = DataModeManager(context = null)
        assertEquals(AppThemeMode.DARK, defaultManager.themeMode.value)
        assertEquals("Dark", AppThemeMode.DARK.title)
        assertTrue(AppThemeMode.DARK.isDark)
    }

    @Test
    fun appThemeModes_haveCorrectDarkFlags() {
        assertFalse(AppThemeMode.LIGHT.isDark)
        assertTrue(AppThemeMode.DARK.isDark)
        assertFalse(AppThemeMode.LIGHT_HIGH_CONTRAST.isDark)
        assertTrue(AppThemeMode.DARK_HIGH_CONTRAST.isDark)
    }

    @Test
    fun appThemeModes_canBeParsedByName() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.valueOf("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.valueOf("DARK"))
        assertEquals(AppThemeMode.LIGHT_HIGH_CONTRAST, AppThemeMode.valueOf("LIGHT_HIGH_CONTRAST"))
        assertEquals(AppThemeMode.DARK_HIGH_CONTRAST, AppThemeMode.valueOf("DARK_HIGH_CONTRAST"))
    }

    @Test
    fun colorSchemes_areInitializedProperly() {
        assertNotNull(LightColors)
        assertNotNull(DarkColors)
        assertNotNull(LightHighContrastColors)
        assertNotNull(DarkHighContrastColors)

        assertEquals(DarkBackground, DarkColors.background)
        assertEquals(BronzePrimaryDark, DarkColors.primary)
        assertEquals(LightBackground, LightColors.background)
        assertEquals(BronzePrimaryLight, LightColors.primary)
        assertEquals(DarkHcBackground, DarkHighContrastColors.background)
        assertEquals(BronzePrimaryDarkHc, DarkHighContrastColors.primary)
        assertEquals(LightHcBackground, LightHighContrastColors.background)
        assertEquals(BronzePrimaryLightHc, LightHighContrastColors.primary)
    }

    @Test
    fun standardColorSchemes_meetWcagAaContrast() {
        assertSchemeContrast("DarkColors", DarkColors, minTextRatio = 4.5, minOutlineRatio = 3.0)
        assertSchemeContrast("LightColors", LightColors, minTextRatio = 4.5, minOutlineRatio = 3.0)
    }

    @Test
    fun highContrastColorSchemes_meetWcagAaaContrast() {
        assertSchemeContrast("DarkHighContrastColors", DarkHighContrastColors, minTextRatio = 7.0, minOutlineRatio = 7.0)
        assertSchemeContrast("LightHighContrastColors", LightHighContrastColors, minTextRatio = 7.0, minOutlineRatio = 7.0)
    }

    @Test
    fun auxiliarySemanticTokens_meetWcagAaContrast() {
        assertRatioAtLeast("Ochre dark on surface", OchreWarningDark, DarkSurface, 4.5)
        assertRatioAtLeast("Ochre onWarning dark", OchreOnWarningDark, OchreWarningDark, 4.5)
        assertRatioAtLeast("Ochre container dark", OchreOnContainerDark, OchreContainerDark, 4.5)
        assertRatioAtLeast("Ochre light on surface", OchreWarningLight, LightSurface, 4.5)
        assertRatioAtLeast("Ochre onWarning light", OchreOnWarningLight, OchreWarningLight, 4.5)
        assertRatioAtLeast("Ochre container light", OchreOnContainerLight, OchreContainerLight, 4.5)

        assertRatioAtLeast("FatLoss dark pill", SubcycleFatLossOnDark, SubcycleFatLossContainerDark, 4.5)
        assertRatioAtLeast("FatLoss light pill", SubcycleFatLossOnLight, SubcycleFatLossContainerLight, 4.5)
        assertRatioAtLeast("MuscleGain dark pill", SubcycleGainOnDark, SubcycleGainContainerDark, 4.5)
        assertRatioAtLeast("MuscleGain light pill", SubcycleGainOnLight, SubcycleGainContainerLight, 4.5)
    }

    private fun assertSchemeContrast(
        label: String,
        scheme: ColorScheme,
        minTextRatio: Double,
        minOutlineRatio: Double
    ) {
        assertRatioAtLeast("$label onBackground/background", scheme.onBackground, scheme.background, minTextRatio)
        assertRatioAtLeast("$label onSurface/surface", scheme.onSurface, scheme.surface, minTextRatio)
        assertRatioAtLeast("$label onSurfaceVariant/surface", scheme.onSurfaceVariant, scheme.surface, minTextRatio)
        assertRatioAtLeast("$label outline/surface", scheme.outline, scheme.surface, minOutlineRatio)
        assertRatioAtLeast("$label primary/background", scheme.primary, scheme.background, minTextRatio)
        assertRatioAtLeast("$label primary/surface", scheme.primary, scheme.surface, minTextRatio)
        assertRatioAtLeast("$label onPrimary/primary", scheme.onPrimary, scheme.primary, minTextRatio)
        assertRatioAtLeast("$label onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, minTextRatio)
        assertRatioAtLeast("$label secondary/surface", scheme.secondary, scheme.surface, minTextRatio)
        assertRatioAtLeast("$label onSecondary/secondary", scheme.onSecondary, scheme.secondary, minTextRatio)
        assertRatioAtLeast("$label onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, minTextRatio)
        assertRatioAtLeast("$label tertiary/surface", scheme.tertiary, scheme.surface, minTextRatio)
        assertRatioAtLeast("$label onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, minTextRatio)
        assertRatioAtLeast("$label onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, minTextRatio)
        assertRatioAtLeast("$label error/surface", scheme.error, scheme.surface, minTextRatio)
        assertRatioAtLeast("$label onError/error", scheme.onError, scheme.error, minTextRatio)
        assertRatioAtLeast("$label onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer, minTextRatio)
    }

    private fun assertRatioAtLeast(label: String, fg: Color, bg: Color, minRatio: Double) {
        val actual = contrastRatio(fg, bg)
        assertTrue("$label contrast $actual < $minRatio", actual >= minRatio)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }
}

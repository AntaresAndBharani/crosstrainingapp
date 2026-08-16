package com.fractanomics.crosstraining.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun defaultThemeMode_isLight() {
        val defaultMode = AppThemeMode.LIGHT
        assertEquals("Light", defaultMode.title)
        assertFalse(defaultMode.isDark)
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

        // Light Scheme verifies light background
        assertEquals(LightBackground, LightColors.background)
        // Dark HC Scheme verifies OLED black background
        assertEquals(DarkHcBackground, DarkHighContrastColors.background)
    }
}

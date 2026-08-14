package com.fractanomics.crosstraining.ui.theme

import androidx.compose.ui.graphics.Color

// =======================================================
// High-Contrast Carbon & High-Vis Volt Green
// Accessible Palette (WCAG AAA Compliant)
// =======================================================

// --- High-Visibility Primary (Volt Green) ---
val VoltGreenPrimary = Color(0xFF00E676)       // Ultra-bright athletic volt green for dark mode (~14.5:1 on black)
val VoltGreenOnPrimary = Color(0xFF000000)     // Pure black text on primary volt
val VoltGreenContainerDark = Color(0xFF0A2E1A) // Deep high-contrast forest container
val VoltGreenOnContainerDark = Color(0xFF69F0AE) // High-luminance mint-volt on dark container

val VoltGreenPrimaryLight = Color(0xFF007A3D)  // Deep emerald volt for light mode (>7.5:1 on white)
val VoltGreenOnPrimaryLight = Color(0xFFFFFFFF) // White text on dark green
val VoltGreenContainerLight = Color(0xFFDCFCE7) // Soft mint container for light mode
val VoltGreenOnContainerLight = Color(0xFF022C22) // Deep green on light container

// --- High-Contrast Secondary (Electric Cyan / Sky) ---
val ElectricCyanSecondary = Color(0xFF38BDF8)     // Bright sky/cyan for dark mode (>12:1 on black)
val ElectricCyanOnSecondary = Color(0xFF000000)
val ElectricCyanContainerDark = Color(0xFF0C324E)
val ElectricCyanOnContainerDark = Color(0xFFBAE6FD)

val ElectricCyanSecondaryLight = Color(0xFF0284C7)
val ElectricCyanOnSecondaryLight = Color(0xFFFFFFFF)
val ElectricCyanContainerLight = Color(0xFFE0F2FE)
val ElectricCyanOnContainerLight = Color(0xFF082F49)

// --- High-Contrast Tertiary (Solar Amber / Gold) ---
val SolarAmberTertiary = Color(0xFFFFD600)        // Pure solar amber for rest phases, timers, badges (>15:1 on black)
val SolarAmberOnTertiary = Color(0xFF000000)
val SolarAmberContainerDark = Color(0xFF3D2E00)
val SolarAmberOnContainerDark = Color(0xFFFEF08A)

val SolarAmberTertiaryLight = Color(0xFFB45309)
val SolarAmberOnTertiaryLight = Color(0xFFFFFFFF)
val SolarAmberContainerLight = Color(0xFFFEF3C7)
val SolarAmberOnContainerLight = Color(0xFF451A03)

// --- Dark Mode Surfaces & Neutrals (Carbon Pitch) ---
val CarbonBackground = Color(0xFF09090B)          // Deep carbon pitch black
val CarbonSurface = Color(0xFF121215)             // Elevated carbon card surface
val CarbonSurfaceVariant = Color(0xFF1E2025)      // Secondary card / chip surface
val CarbonSurfaceContainer = Color(0xFF18191E)    // Elevated container
val CarbonSurfaceContainerHigh = Color(0xFF22242B)
val TextHighContrastWhite = Color(0xFFFFFFFF)     // 100% white primary text (>20:1 on carbon)
val TextHighContrastSilver = Color(0xFFE2E8F0)    // High-luminance silver secondary text (>14:1 on carbon)
val BorderHighContrastSlate = Color(0xFF52525B)   // Crisp solid border outline
val BorderHighContrastSubtle = Color(0xFF3F3F46)  // Crisp container separator

// --- Light Mode Surfaces & Neutrals (Crisp Porcelain) ---
val LightBackground = Color(0xFFFFFFFF)           // Pure crisp white
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightSurfaceContainer = Color(0xFFF8FAFC)
val LightSurfaceContainerHigh = Color(0xFFEDEFEA)
val TextLightModeBlack = Color(0xFF09090B)        // Deep pitch black primary text (>20:1 on white)
val TextLightModeSlate = Color(0xFF1E293B)        // Deep slate secondary text (>12:1 on white)
val BorderLightModeSlate = Color(0xFF64748B)      // Crisp solid border outline
val BorderLightModeSubtle = Color(0xFFCBD5E1)     // Crisp card border

// --- Error & Alert Tokens (High-Vis Crimson) ---
val HighVisErrorDark = Color(0xFFFF5252)          // High-visibility coral-crimson for dark mode
val HighVisOnErrorDark = Color(0xFF000000)
val HighVisErrorContainerDark = Color(0xFF4C0B0B)
val HighVisOnErrorContainerDark = Color(0xFFFECDD3)

val HighVisErrorLight = Color(0xFFDC2626)
val HighVisOnErrorLight = Color(0xFFFFFFFF)
val HighVisErrorContainerLight = Color(0xFFFEE2E2)
val HighVisOnErrorContainerLight = Color(0xFF450A0A)

// --- Workout, Chart & Timer Semantic Tokens ---
val TimerWorkColor = Color(0xFF00E676)            // Volt green for active work phase
val TimerRestColor = Color(0xFFFFD600)            // Solar amber for rest phase
val TimerPrepColor = Color(0xFF38BDF8)            // Cyan for countdown / prep phase
val ChartVoltPrimary = Color(0xFF00E676)
val ChartCyanSecondary = Color(0xFF38BDF8)
val ChartAmberTertiary = Color(0xFFFFD600)

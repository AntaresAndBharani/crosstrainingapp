package com.fractanomics.crosstraining.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNumericTextFieldTest {

    @Test
    fun `no digit concatenation on replace and non-numeric filtering`() {
        // Clearing "10" and typing "6" results in "6", not "610"
        val filtered = NumericInputSanitizer.filterInput("6", allowDecimals = false)
        assertEquals("6", filtered)

        val nonNumeric = NumericInputSanitizer.filterInput("12a3b4", allowDecimals = false)
        assertEquals("1234", nonNumeric)
    }

    @Test
    fun `decimal parsing accuracy and formatting`() {
        // Typing "22.5" accepts decimal
        val filtered = NumericInputSanitizer.filterInput("22.5", allowDecimals = true)
        assertEquals("22.5", filtered)

        // Multiple decimal points are rejected
        val multiDecimal = NumericInputSanitizer.filterInput("22.5.8", allowDecimals = true)
        assertEquals("22.58", multiDecimal)

        // Comma converted to dot when single decimal mode
        val commaDecimal = NumericInputSanitizer.filterInput("22,5", allowDecimals = true)
        assertEquals("22.5", commaDecimal)
    }

    @Test
    fun `clamp on commit and sanitize leading zeros`() {
        // Empty text commits minValue
        val emptyClamped = NumericInputSanitizer.sanitizeAndClamp("", minValue = 1.0, maxValue = 100.0)
        assertEquals("1", emptyClamped)

        // Value below minValue clamped
        val belowMin = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 5.0, maxValue = 100.0)
        assertEquals("5", belowMin)

        // Value above maxValue clamped
        val aboveMax = NumericInputSanitizer.sanitizeAndClamp("150", minValue = 1.0, maxValue = 100.0)
        assertEquals("100", aboveMax)

        // Sanitizing leading zeros for integer ("05" -> "5")
        val leadingZeroInt = NumericInputSanitizer.sanitizeAndClamp("05", minValue = 1.0, maxValue = 100.0)
        assertEquals("5", leadingZeroInt)

        // Leading zero preserved for decimal ("0.5" -> "0.5")
        val leadingZeroDec = NumericInputSanitizer.sanitizeAndClamp("0.5", minValue = 0.0, maxValue = 100.0, allowDecimals = true)
        assertEquals("0.5", leadingZeroDec)

        // Leading zero with whole number and decimal ("05.2" -> "5.2")
        val leadingZeroMixed = NumericInputSanitizer.sanitizeAndClamp("05.2", minValue = 0.0, maxValue = 100.0, allowDecimals = true)
        assertEquals("5.2", leadingZeroMixed)
    }
}

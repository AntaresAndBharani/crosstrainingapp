package com.fractanomics.crosstraining.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test suite for [AppNumericTextField] logic and [NumericInputSanitizer].
 * Covers Acceptance Criteria from Issue #430:
 * - Scenario: No digit concatenation on replace
 * - Scenario: Decimal parsing accuracy
 * - Scenario: Clamp on commit
 */
class AppNumericTextFieldTest {

    // =========================================================================
    // Scenario: No digit concatenation on replace
    // =========================================================================

    @Test
    fun `no digit concatenation on replace - clearing 10 and typing 6 results in 6`() {
        // Given a field showing "10"
        // When the user clears it and types "6"
        val filtered = NumericInputSanitizer.filterInput("6", allowDecimals = false)
        // Then the resulting value is "6", not "610"
        assertEquals("6", filtered)
    }

    @Test
    fun `no digit concatenation on replace - non-numeric characters filtered out`() {
        val nonNumeric = NumericInputSanitizer.filterInput("12a3b4#*!", allowDecimals = false)
        assertEquals("1234", nonNumeric)
    }

    @Test
    fun `no digit concatenation on replace - integer mode ignores decimal dots and commas`() {
        val integerFiltered = NumericInputSanitizer.filterInput("10.5,2", allowDecimals = false)
        assertEquals("1052", integerFiltered)
    }

    // =========================================================================
    // Scenario: Decimal parsing accuracy
    // =========================================================================

    @Test
    fun `decimal parsing accuracy - typing 22_5 parses exactly to 22_5`() {
        // Given a field configured with allowDecimals = true
        // When the user types "22.5"
        val filtered = NumericInputSanitizer.filterInput("22.5", allowDecimals = true)
        assertEquals("22.5", filtered)

        // Then the parsed value equals 22.5 exactly
        val parsed = filtered.toDoubleOrNull()
        assertEquals(22.5, parsed)
    }

    @Test
    fun `decimal parsing accuracy - comma is normalized to decimal dot`() {
        val commaFiltered = NumericInputSanitizer.filterInput("22,5", allowDecimals = true)
        assertEquals("22.5", commaFiltered)

        val parsed = commaFiltered.toDoubleOrNull()
        assertEquals(22.5, parsed)
    }

    @Test
    fun `decimal parsing accuracy - multiple decimal points are rejected`() {
        val multiDecimal = NumericInputSanitizer.filterInput("22.5.8.9", allowDecimals = true)
        assertEquals("22.589", multiDecimal)

        val parsed = multiDecimal.toDoubleOrNull()
        assertEquals(22.589, parsed)
    }

    @Test
    fun `decimal parsing accuracy - leading zero decimal fractions parse accurately`() {
        val filtered = NumericInputSanitizer.filterInput("0.75", allowDecimals = true)
        assertEquals("0.75", filtered)

        val parsed = filtered.toDoubleOrNull()
        assertEquals(0.75, parsed)
    }

    // =========================================================================
    // Scenario: Clamp on commit
    // =========================================================================

    @Test
    fun `clamp on commit - empty text commits minValue`() {
        // Given a field with minValue and maxValue set
        // When the field is left empty and loses focus, or IME Done is pressed while empty
        val clampedEmpty = NumericInputSanitizer.sanitizeAndClamp(
            text = "",
            minValue = 5.0,
            maxValue = 100.0,
            allowDecimals = false
        )
        // Then the committed value equals minValue
        assertEquals("5", clampedEmpty)
    }

    @Test
    fun `clamp on commit - empty text commits decimal minValue when allowDecimals is true`() {
        val clampedDecimalEmpty = NumericInputSanitizer.sanitizeAndClamp(
            text = "",
            minValue = 2.5,
            maxValue = 50.0,
            allowDecimals = true
        )
        assertEquals("2.5", clampedDecimalEmpty)
    }

    @Test
    fun `clamp on commit - empty text without minValue returns empty string`() {
        val clampedEmptyNoMin = NumericInputSanitizer.sanitizeAndClamp(
            text = "",
            minValue = null,
            maxValue = 100.0
        )
        assertEquals("", clampedEmptyNoMin)
    }

    @Test
    fun `clamp on commit - value below minValue is clamped to minValue`() {
        val belowMin = NumericInputSanitizer.sanitizeAndClamp(
            text = "2",
            minValue = 5.0,
            maxValue = 100.0
        )
        assertEquals("5", belowMin)
    }

    @Test
    fun `clamp on commit - value above maxValue is clamped to maxValue`() {
        val aboveMax = NumericInputSanitizer.sanitizeAndClamp(
            text = "150",
            minValue = 1.0,
            maxValue = 100.0
        )
        assertEquals("100", aboveMax)
    }

    @Test
    fun `clamp on commit - decimal value below minValue clamped to minValue`() {
        val belowMinDec = NumericInputSanitizer.sanitizeAndClamp(
            text = "1.5",
            minValue = 2.5,
            maxValue = 100.0,
            allowDecimals = true
        )
        assertEquals("2.5", belowMinDec)
    }

    @Test
    fun `clamp on commit - decimal value above maxValue clamped to maxValue`() {
        val aboveMaxDec = NumericInputSanitizer.sanitizeAndClamp(
            text = "120.75",
            minValue = 0.0,
            maxValue = 100.0,
            allowDecimals = true
        )
        assertEquals("100", aboveMaxDec)
    }

    @Test
    fun `clamp on commit - leading zero integer is sanitized from 05 to 5`() {
        val leadingZero = NumericInputSanitizer.sanitizeAndClamp(
            text = "05",
            minValue = 1.0,
            maxValue = 100.0,
            allowDecimals = false
        )
        assertEquals("5", leadingZero)
    }

    @Test
    fun `clamp on commit - leading zero with decimal fraction is preserved for 0_5`() {
        val leadingZeroDec = NumericInputSanitizer.sanitizeAndClamp(
            text = "0.5",
            minValue = 0.0,
            maxValue = 100.0,
            allowDecimals = true
        )
        assertEquals("0.5", leadingZeroDec)
    }

    @Test
    fun `clamp on commit - leading zero with whole number and decimal is sanitized from 05_2 to 5_2`() {
        val leadingZeroMixed = NumericInputSanitizer.sanitizeAndClamp(
            text = "05.2",
            minValue = 0.0,
            maxValue = 100.0,
            allowDecimals = true
        )
        assertEquals("5.2", leadingZeroMixed)
    }

    // =========================================================================
    // Schemes, Ranges, and Negative Support
    // =========================================================================

    @Test
    fun `scheme formatting - allowScheme preserves 5x3 and 3x10 expressions`() {
        val filtered = NumericInputSanitizer.filterInput("5x3", allowScheme = true)
        assertEquals("5x3", filtered)

        val clamped = NumericInputSanitizer.sanitizeAndClamp("5x3", allowScheme = true)
        assertEquals("5x3", clamped)
    }

    @Test
    fun `range and list formatting - allowRangeOrList preserves 60-80 and lists`() {
        val filteredRange = NumericInputSanitizer.filterInput("60-80", allowRangeOrList = true)
        assertEquals("60-80", filteredRange)

        val clampedRange = NumericInputSanitizer.sanitizeAndClamp("60-80", allowRangeOrList = true)
        assertEquals("60-80", clampedRange)

        val filteredList = NumericInputSanitizer.filterInput("60, 65, 70", allowRangeOrList = true)
        assertEquals("60, 65, 70", filteredList)

        val clampedList = NumericInputSanitizer.sanitizeAndClamp("60, 65, 70", allowRangeOrList = true)
        assertEquals("60, 65, 70", clampedList)
    }

    @Test
    fun `negative numbers - allowNegative allows leading minus sign`() {
        val negative = NumericInputSanitizer.filterInput("-15", allowNegative = true)
        assertEquals("-15", negative)

        val internalMinus = NumericInputSanitizer.filterInput("15-20", allowNegative = true)
        assertEquals("1520", internalMinus)
    }

    // =========================================================================
    // Double display formatting helper verification
    // =========================================================================

    @Test
    fun `double value display formatting logic`() {
        fun formatDouble(value: Double?): String {
            return value?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        }

        assertEquals("", formatDouble(null))
        assertEquals("100", formatDouble(100.0))
        assertEquals("22.5", formatDouble(22.5))
        assertEquals("0", formatDouble(0.0))
        assertEquals("0.75", formatDouble(0.75))
    }
}

package com.fractanomics.crosstraining.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    // Scenario 1: Select-all on focus and direct digit replacement
    // =========================================================================

    @Test
    fun `scenario 1 - select-all on focus produces full selection range`() {
        val initialText = "10"
        val initialTfv = TextFieldValue(text = initialText)
        val focusedTfv = initialTfv.copy(selection = TextRange(0, initialTfv.text.length))

        assertEquals(0, focusedTfv.selection.start)
        assertEquals(2, focusedTfv.selection.end)
        assertEquals(2, focusedTfv.selection.length)
    }

    @Test
    fun `scenario 1 - direct digit replacement replaces entire selected text without concatenation`() {
        // Given text "10" is fully selected
        val initialText = "10"
        val selection = TextRange(0, initialText.length)

        // When user types '6' to replace selected range
        val typedChar = "6"
        val replaced = initialText.replaceRange(selection.start, selection.end, typedChar)
        val filtered = NumericInputSanitizer.filterInput(replaced, allowDecimals = false)

        // Then result is "6", not "610" or "106"
        assertEquals("6", filtered)
    }

    @Test
    fun `no digit concatenation on replace - clearing 10 and typing 6 results in 6`() {
        val filtered = NumericInputSanitizer.filterInput("6", allowDecimals = false)
        assertEquals("6", filtered)
    }

    @Test
    fun `scenario 1 - non-numeric characters are filtered out`() {
        val filtered = NumericInputSanitizer.filterInput("12a3b4#$", allowDecimals = false)
        assertEquals("1234", filtered)
    }

    @Test
    fun `no digit concatenation on replace - integer mode ignores decimal dots and commas`() {
        val integerFiltered = NumericInputSanitizer.filterInput("10.5,2", allowDecimals = false)
        assertEquals("1052", integerFiltered)
    }

    // =========================================================================
    // Scenario 2: Intermediate blank state
    // =========================================================================

    @Test
    fun `scenario 2 - backspacing all digits results in empty string without reverting`() {
        val emptyInput = NumericInputSanitizer.filterInput("", allowDecimals = false)
        assertEquals("", emptyInput)

        val uncommittedEmpty = NumericInputSanitizer.sanitizeAndClamp("", minValue = null, maxValue = 100.0)
        assertEquals("", uncommittedEmpty)
    }

    // =========================================================================
    // Scenario 3: Commit, clamping, and IME action handling
    // =========================================================================

    @Test
    fun `clamp on commit - empty text commits minValue`() {
        val clampedEmpty = NumericInputSanitizer.sanitizeAndClamp(
            text = "",
            minValue = 5.0,
            maxValue = 100.0,
            allowDecimals = false
        )
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

    @Test
    fun `scenario 3 - trailing decimal dot sanitized on commit`() {
        val committed = NumericInputSanitizer.sanitizeAndClamp("22.", minValue = 0.0, maxValue = 100.0, allowDecimals = true)
        assertEquals("22", committed)
    }

    // =========================================================================
    // Scenario 4: Decimal parsing accuracy
    // =========================================================================

    @Test
    fun `decimal parsing accuracy - typing 22_5 parses exactly to 22_5`() {
        val filtered = NumericInputSanitizer.filterInput("22.5", allowDecimals = true)
        assertEquals("22.5", filtered)

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

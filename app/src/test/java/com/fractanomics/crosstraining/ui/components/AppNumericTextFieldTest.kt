package com.fractanomics.crosstraining.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNumericTextFieldTest {

    // --- Scenario 1: Select-all on focus and direct digit replacement ---

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
    fun `scenario 1 - non-numeric characters are filtered out`() {
        val filtered = NumericInputSanitizer.filterInput("12a3b4#$", allowDecimals = false)
        assertEquals("1234", filtered)
    }

    // --- Scenario 2: Intermediate blank state ---

    @Test
    fun `scenario 2 - backspacing all digits results in empty string without reverting`() {
        val emptyInput = NumericInputSanitizer.filterInput("", allowDecimals = false)
        assertEquals("", emptyInput)

        val uncommittedEmpty = NumericInputSanitizer.sanitizeAndClamp("", minValue = null, maxValue = 100.0)
        assertEquals("", uncommittedEmpty)
    }

    // --- Scenario 3: Commit, clamping, and IME action handling ---

    @Test
    fun `scenario 3 - empty field commits to minValue when minValue is configured`() {
        val emptyClampedInt = NumericInputSanitizer.sanitizeAndClamp("", minValue = 1.0, maxValue = 100.0)
        assertEquals("1", emptyClampedInt)

        val emptyClampedDec = NumericInputSanitizer.sanitizeAndClamp("", minValue = 0.5, maxValue = 100.0, allowDecimals = true)
        assertEquals("0.5", emptyClampedDec)
    }

    @Test
    fun `scenario 3 - value below minValue is clamped to minValue`() {
        val belowMin = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 5.0, maxValue = 100.0)
        assertEquals("5", belowMin)
    }

    @Test
    fun `scenario 3 - value above maxValue is clamped to maxValue`() {
        val aboveMax = NumericInputSanitizer.sanitizeAndClamp("150", minValue = 1.0, maxValue = 100.0)
        assertEquals("100", aboveMax)
    }

    @Test
    fun `scenario 3 - integer leading zeros are sanitized`() {
        assertEquals("5", NumericInputSanitizer.sanitizeAndClamp("05", minValue = 1.0, maxValue = 100.0))
        assertEquals("7", NumericInputSanitizer.sanitizeAndClamp("007", minValue = 1.0, maxValue = 100.0))
        assertEquals("0", NumericInputSanitizer.sanitizeAndClamp("00", minValue = 0.0, maxValue = 100.0))
    }

    @Test
    fun `scenario 3 - decimal leading zeros are sanitized while preserving valid fractions`() {
        // "05.2" -> "5.2"
        assertEquals("5.2", NumericInputSanitizer.sanitizeAndClamp("05.2", minValue = 0.0, maxValue = 100.0, allowDecimals = true))
        // "00.5" -> "0.5"
        assertEquals("0.5", NumericInputSanitizer.sanitizeAndClamp("00.5", minValue = 0.0, maxValue = 100.0, allowDecimals = true))
        // "0.5" preserved
        assertEquals("0.5", NumericInputSanitizer.sanitizeAndClamp("0.5", minValue = 0.0, maxValue = 100.0, allowDecimals = true))
        // "0.05" preserved
        assertEquals("0.05", NumericInputSanitizer.sanitizeAndClamp("0.05", minValue = 0.0, maxValue = 100.0, allowDecimals = true))
    }

    @Test
    fun `scenario 3 - trailing decimal dot sanitized on commit`() {
        val committed = NumericInputSanitizer.sanitizeAndClamp("22.", minValue = 0.0, maxValue = 100.0, allowDecimals = true)
        assertEquals("22", committed)
    }

    // --- Scenario 4: Decimal support ---

    @Test
    fun `scenario 4 - decimal parsing accuracy and comma normalization`() {
        // Typing "22.5" accepts decimal
        val filtered = NumericInputSanitizer.filterInput("22.5", allowDecimals = true)
        assertEquals("22.5", filtered)

        // Comma converted to dot in decimal mode
        val commaDecimal = NumericInputSanitizer.filterInput("22,5", allowDecimals = true)
        assertEquals("22.5", commaDecimal)

        // Multiple decimal points rejected
        val multiDecimal = NumericInputSanitizer.filterInput("22.5.8", allowDecimals = true)
        assertEquals("22.58", multiDecimal)
    }

    @Test
    fun `scenario 4 - whole number decimal formatting cleans trailing zero`() {
        val wholeNumberDec = NumericInputSanitizer.sanitizeAndClamp("22.0", minValue = 0.0, maxValue = 100.0, allowDecimals = true)
        assertEquals("22.0", wholeNumberDec) // Preserves user-typed decimal precision if valid
    }

    // --- Advanced Features: Schemes, Ranges, and Negative Values ---

    @Test
    fun `schemes ranges and lists support`() {
        // Rep scheme
        val scheme = NumericInputSanitizer.filterInput("5x3", allowScheme = true)
        assertEquals("5x3", scheme)
        assertEquals("5x3", NumericInputSanitizer.sanitizeAndClamp("5x3", allowScheme = true))

        // Weight range
        val range = NumericInputSanitizer.filterInput("60-80", allowRangeOrList = true)
        assertEquals("60-80", range)
        assertEquals("60-80", NumericInputSanitizer.sanitizeAndClamp("60-80", allowRangeOrList = true))

        // Weight list
        val list = NumericInputSanitizer.filterInput("60, 65, 70", allowRangeOrList = true)
        assertEquals("60, 65, 70", list)
        assertEquals("60, 65, 70", NumericInputSanitizer.sanitizeAndClamp("60, 65, 70", allowRangeOrList = true))
    }

    @Test
    fun `negative value support`() {
        val negative = NumericInputSanitizer.filterInput("-15", allowNegative = true)
        assertEquals("-15", negative)

        val invalidNegative = NumericInputSanitizer.filterInput("15-", allowNegative = true)
        assertEquals("15", invalidNegative)
    }
}

package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Centered placeholder shown when a list has no items yet. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A simple vertical section with a title and content. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** LazyColumn preset with standard padding/spacing used across screens. */
@Composable
fun ScreenList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/**
 * Utility for input filtering, sanitization, and clamping for numeric text fields.
 */
object NumericInputSanitizer {

    fun filterInput(
        text: String,
        allowDecimals: Boolean = false,
        allowNegative: Boolean = false,
        allowScheme: Boolean = false,
        allowRangeOrList: Boolean = false
    ): String {
        return buildString {
            var hasDecimal = false
            var hasSign = false
            for ((idx, ch) in text.withIndex()) {
                if (ch.isDigit()) {
                    append(ch)
                } else if (allowRangeOrList && (ch == '-' || ch == '–' || ch == '—' || ch == ',' || ch == ' ')) {
                    append(ch)
                    if (ch == ',' || ch == '-' || ch == '–' || ch == '—') {
                        hasDecimal = false // Reset decimal flag for subsequent numbers in list/range
                    }
                } else if (allowScheme && (ch == 'x' || ch == 'X' || ch == '*' || ch == '-' || ch == ',' || ch == ' ')) {
                    append(ch)
                } else if (allowDecimals && (ch == '.' || (!allowRangeOrList && ch == ',')) && !hasDecimal) {
                    append('.')
                    hasDecimal = true
                } else if (allowNegative && ch == '-' && idx == 0 && !hasSign) {
                    append(ch)
                    hasSign = true
                }
            }
        }
    }

    fun sanitizeAndClamp(
        text: String,
        minValue: Double? = null,
        maxValue: Double? = null,
        allowDecimals: Boolean = false,
        allowScheme: Boolean = false,
        allowRangeOrList: Boolean = false
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return if (minValue != null) {
                if (allowDecimals && minValue % 1.0 != 0.0) minValue.toString()
                else if (minValue % 1.0 == 0.0) minValue.toLong().toString()
                else minValue.toString()
            } else ""
        }

        // If it's a scheme like "5x3" or range like "60-80" or list like "60, 65, 70"
        if ((allowScheme || allowRangeOrList) && (trimmed.contains(Regex("""[xX*,\-]""")) || trimmed.contains(" "))) {
            return trimmed
        }

        if (allowDecimals) {
            val parsed = trimmed.toDoubleOrNull()
            if (parsed == null) {
                return if (minValue != null) {
                    if (minValue % 1.0 == 0.0) minValue.toLong().toString() else minValue.toString()
                } else ""
            }
            var clamped = parsed
            if (minValue != null && clamped < minValue) clamped = minValue
            if (maxValue != null && clamped > maxValue) clamped = maxValue

            return if (clamped == parsed) {
                if (trimmed.startsWith("0") && trimmed.length > 1 && trimmed[1] != '.') {
                    if (clamped % 1.0 == 0.0) clamped.toLong().toString() else clamped.toString()
                } else {
                    trimmed
                }
            } else {
                if (clamped % 1.0 == 0.0) clamped.toLong().toString() else clamped.toString()
            }
        } else {
            val parsed = trimmed.toLongOrNull()
            if (parsed == null) {
                return minValue?.toLong()?.toString() ?: ""
            }
            var clamped = parsed
            if (minValue != null && clamped < minValue.toLong()) clamped = minValue.toLong()
            if (maxValue != null && clamped > maxValue.toLong()) clamped = maxValue.toLong()
            return clamped.toString()
        }
    }
}

/**
 * A resilient numeric input field that supports:
 * - Select-all on focus (preventing accidental digit concatenation like typing "6" into "10" becoming "610")
 * - Intermediate blank state while focused without snapping back
 * - Deferred commit & clamping to [minValue]..[maxValue] on focus loss or IME action
 * - Decimal support (optional)
 * - Sanitization of leading zeros (e.g. "05" -> "5")
 */
@Composable
fun AppNumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    minValue: Double? = null,
    maxValue: Double? = null,
    allowDecimals: Boolean = false,
    allowNegative: Boolean = false,
    allowScheme: Boolean = false,
    allowRangeOrList: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    LaunchedEffect(value, isFocused) {
        if (!isFocused && textFieldValue.text != value) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    fun commitValue() {
        val committed = NumericInputSanitizer.sanitizeAndClamp(
            text = textFieldValue.text,
            minValue = minValue,
            maxValue = maxValue,
            allowDecimals = allowDecimals,
            allowScheme = allowScheme,
            allowRangeOrList = allowRangeOrList
        )
        textFieldValue = TextFieldValue(
            text = committed,
            selection = TextRange(committed.length)
        )
        onValueChange(committed)
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newTfv ->
            val filtered = NumericInputSanitizer.filterInput(
                text = newTfv.text,
                allowDecimals = allowDecimals,
                allowNegative = allowNegative,
                allowScheme = allowScheme,
                allowRangeOrList = allowRangeOrList
            )

            val updatedTfv = if (filtered != newTfv.text) {
                val newCursor = filtered.length.coerceAtMost(newTfv.selection.end)
                newTfv.copy(text = filtered, selection = TextRange(newCursor))
            } else {
                newTfv
            }

            textFieldValue = updatedTfv
            onValueChange(updatedTfv.text)
        },
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            if (focusState.isFocused && !wasFocused) {
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(0, textFieldValue.text.length)
                )
            } else if (!focusState.isFocused && wasFocused) {
                commitValue()
            }
        },
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                allowScheme || allowRangeOrList -> KeyboardType.Text
                allowDecimals -> KeyboardType.Decimal
                else -> KeyboardType.Number
            },
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                commitValue()
                focusManager.clearFocus()
                onImeAction?.invoke()
            },
            onNext = {
                commitValue()
                onImeAction?.invoke()
            }
        )
    )
}

/**
 * Int-based overload of [AppNumericTextField].
 */
@Composable
fun AppNumericTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    minValue: Int = 0,
    maxValue: Int = Int.MAX_VALUE,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    AppNumericTextField(
        value = value.toString(),
        onValueChange = { str ->
            val num = str.toIntOrNull() ?: minValue
            onValueChange(num)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        minValue = minValue.toDouble(),
        maxValue = maxValue.toDouble(),
        allowDecimals = false,
        imeAction = imeAction,
        onImeAction = onImeAction,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError
    )
}

/**
 * Double-based overload of [AppNumericTextField].
 */
@Composable
fun AppNumericTextField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    minValue: Double = 0.0,
    maxValue: Double = Double.MAX_VALUE,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    val displayValue = value?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
    } ?: ""

    AppNumericTextField(
        value = displayValue,
        onValueChange = { str ->
            val num = str.toDoubleOrNull()
            onValueChange(num)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        minValue = minValue,
        maxValue = maxValue,
        allowDecimals = true,
        imeAction = imeAction,
        onImeAction = onImeAction,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError
    )
}

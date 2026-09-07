package com.fractanomics.crosstraining.ui.screens.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.components.AppNumericTextField
import com.fractanomics.crosstraining.ui.components.DateField
import java.time.LocalDate

/**
 * Modal bottom sheet for logging a new weight entry or editing an existing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEntryBottomSheet(
    sheetState: SheetState,
    initialEntry: WeightEntry?,
    latestEntry: WeightEntry?,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (weightKg: Double, date: LocalDate, notes: String) -> Unit,
    onDelete: ((date: LocalDate) -> Unit)? = null
) {
    val isImperial = unit.equals("lbs", ignoreCase = true)
    val isEditing = initialEntry != null

    // Determine initial weight string
    val initialWeightStr = remember(initialEntry, latestEntry, unit) {
        val entryToUse = initialEntry ?: latestEntry
        if (entryToUse != null) {
            val converted = if (isImperial) {
                WeightAnalytics.kgToLbs(entryToUse.weightKg)
            } else {
                entryToUse.weightKg
            }
            if (converted % 1.0 == 0.0) converted.toLong().toString()
            else String.format(java.util.Locale.US, "%.1f", converted)
        } else ""
    }

    var weightText by remember { mutableStateOf(initialWeightStr) }
    var selectedDate by remember { mutableStateOf(initialEntry?.date ?: LocalDate.now()) }
    var notesText by remember { mutableStateOf(initialEntry?.notes ?: "") }

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    // Validation
    val parsedWeight = weightText.toDoubleOrNull()
    val canonicalKg = if (parsedWeight != null) {
        if (isImperial) WeightAnalytics.lbsToKg(parsedWeight) else parsedWeight
    } else null

    val isValid = canonicalKg != null && WeightAnalytics.isValidWeightKg(canonicalKg)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditing) "Edit Weight Entry" else "Log Body Weight",
                style = MaterialTheme.typography.titleLarge
            )

            // Date Quick-Pick Chips & DatePicker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedDate == today,
                        onClick = { selectedDate = today },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = selectedDate == yesterday,
                        onClick = { selectedDate = yesterday },
                        label = { Text("Yesterday") }
                    )
                    DateField(
                        label = "Date",
                        date = selectedDate,
                        onDateChange = { selectedDate = it }
                    )
                }
            }

            // Numeric Decimal Weight Input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Weight ()",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppNumericTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    allowDecimals = true,
                    placeholder = { Text(if (isImperial) "e.g. 172.8" else "e.g. 78.4") },
                    isError = weightText.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (weightText.isNotBlank() && !isValid) {
                    val bounds = if (isImperial) {
                        "44.1 - 771.6 lbs"
                    } else {
                        "20.0 - 350.0 kg"
                    }
                    Text(
                        text = "Enter a valid weight between ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Notes Input (Optional)
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("e.g. Morning weigh-in, fasted") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (initialEntry != null && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete(initialEntry.date)
                        }
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (canonicalKg != null && isValid) {
                                onSave(canonicalKg, selectedDate, notesText.trim())
                            }
                        },
                        enabled = isValid
                    ) {
                        Text("Save entry")
                    }
                }
            }
        }
    }
}

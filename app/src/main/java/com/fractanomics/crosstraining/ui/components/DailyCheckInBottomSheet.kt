package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.ui.formatLong
import java.time.LocalDate

/**
 * Modal bottom sheet for logging athlete daily fasting status (Done / Ate something),
 * rest day flag, and macronutrient targets (Calories, Protein, Carbs, Fat).
 *
 * Supports nullable integer parsing for macronutrient goals and tombstone-durable
 * synchronization via [com.fractanomics.crosstraining.data.model.DailyLog].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyCheckInBottomSheet(
    date: LocalDate,
    dailyLog: DailyLog?,
    isScheduledFastDay: Boolean = false,
    isScheduledRestDay: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (DailyLog) -> Unit,
    onDelete: ((LocalDate) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var fastCompleted by remember(dailyLog) { mutableStateOf(dailyLog?.fastCompleted) }
    var isRestDay by remember(dailyLog) { mutableStateOf(dailyLog?.isRestDay ?: isScheduledRestDay) }
    var caloriesText by remember(dailyLog) {
        mutableStateOf(dailyLog?.caloriesKcal?.toString() ?: "")
    }
    var proteinText by remember(dailyLog) {
        mutableStateOf(dailyLog?.proteinGrams?.toString() ?: "")
    }
    var carbsText by remember(dailyLog) {
        mutableStateOf(dailyLog?.carbsGrams?.toString() ?: "")
    }
    var fatText by remember(dailyLog) {
        mutableStateOf(dailyLog?.fatGrams?.toString() ?: "")
    }
    var notes by remember(dailyLog) { mutableStateOf(dailyLog?.notes ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Check-In",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = date.formatLong(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isScheduledFastDay) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "Fast Day",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Fasting Adherence Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Fasting Adherence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = fastCompleted == true,
                        onClick = {
                            fastCompleted = if (fastCompleted == true) null else true
                        },
                        label = { Text("Done") },
                        leadingIcon = if (fastCompleted == true) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    FilterChip(
                        selected = fastCompleted == false,
                        onClick = {
                            fastCompleted = if (fastCompleted == false) null else false
                        },
                        label = { Text("Ate something") },
                        leadingIcon = if (fastCompleted == false) {
                            { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )

                    FilterChip(
                        selected = fastCompleted == null,
                        onClick = { fastCompleted = null },
                        label = { Text("Not logged") }
                    )
                }
            }

            // Rest Day Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isRestDay,
                    onCheckedChange = { isRestDay = it }
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Rest Day (No planned training session)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Nutrition & Macros Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nutrition & Macros",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppNumericTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it },
                        label = { Text("Calories (kcal)") },
                        singleLine = true,
                        allowDecimals = false,
                        minValue = 0.0,
                        maxValue = 15000.0,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )

                    AppNumericTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it },
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        allowDecimals = false,
                        minValue = 0.0,
                        maxValue = 1000.0,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppNumericTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text("Carbs (g)") },
                        singleLine = true,
                        allowDecimals = false,
                        minValue = 0.0,
                        maxValue = 1500.0,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )

                    AppNumericTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
                        label = { Text("Fat (g)") },
                        singleLine = true,
                        allowDecimals = false,
                        minValue = 0.0,
                        maxValue = 1000.0,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (e.g. fasting window, refeed details)") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dailyLog != null && onDelete != null) {
                    IconButton(
                        onClick = { onDelete(date) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Check-In",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val cal = caloriesText.toIntOrNull()
                        val pro = proteinText.toIntOrNull()
                        val carb = carbsText.toIntOrNull()
                        val fat = fatText.toIntOrNull()

                        val updated = (dailyLog ?: DailyLog(date = date, updatedAtMillis = System.currentTimeMillis())).copy(
                            fastCompleted = fastCompleted,
                            isRestDay = isRestDay,
                            caloriesKcal = cal,
                            proteinGrams = pro,
                            carbsGrams = carb,
                            fatGrams = fat,
                            notes = notes.trim(),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}

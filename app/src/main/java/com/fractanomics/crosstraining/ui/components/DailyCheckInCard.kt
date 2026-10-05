package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.ui.formatLong
import java.time.LocalDate

/**
 * Athlete daily check-in summary card displaying fasting status (Done / Ate something),
 * rest day indicator, and macro nutrition metrics.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyCheckInCard(
    date: LocalDate,
    dailyLog: DailyLog?,
    isScheduledFastDay: Boolean,
    isScheduledRestDay: Boolean,
    onSaveDailyLog: (DailyLog) -> Unit,
    onOpenFullCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = dailyLog?.fastCompleted == true
    val isAte = dailyLog?.fastCompleted == false
    val isRest = dailyLog?.isRestDay ?: isScheduledRestDay

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title, Date, Badges & Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Daily Check-In",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isScheduledRestDay) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Rest Day",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenFullCheckIn,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit Daily Check-In",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Fasting Actions & Rest Day Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fasting:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilterChip(
                    selected = isDone,
                    onClick = {
                        val next = if (isDone) null else true
                        val updated = (dailyLog ?: DailyLog(date = date, updatedAtMillis = System.currentTimeMillis())).copy(
                            fastCompleted = next,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        onSaveDailyLog(updated)
                    },
                    label = { Text("Done") },
                    leadingIcon = if (isDone) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                FilterChip(
                    selected = isAte,
                    onClick = {
                        val next = if (isAte) null else false
                        val updated = (dailyLog ?: DailyLog(date = date, updatedAtMillis = System.currentTimeMillis())).copy(
                            fastCompleted = next,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        onSaveDailyLog(updated)
                    },
                    label = { Text("Ate something") },
                    leadingIcon = if (isAte) {
                        { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )

                Spacer(Modifier.weight(1f))

                FilterChip(
                    selected = isRest,
                    onClick = {
                        val nextRest = !isRest
                        val updated = (dailyLog ?: DailyLog(date = date, updatedAtMillis = System.currentTimeMillis())).copy(
                            isRestDay = nextRest,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        onSaveDailyLog(updated)
                    },
                    label = { Text("Rest") }
                )
            }

            // Macros Summary Row
            val hasMacros = dailyLog != null && (
                dailyLog.caloriesKcal != null || dailyLog.proteinGrams != null ||
                dailyLog.carbsGrams != null || dailyLog.fatGrams != null
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            if (dailyLog != null && hasMacros) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFullCheckIn() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val parts = buildList {
                        dailyLog.caloriesKcal?.let { add("$it kcal") }
                        dailyLog.proteinGrams?.let { add("P: ${it}g") }
                        dailyLog.carbsGrams?.let { add("C: ${it}g") }
                        dailyLog.fatGrams?.let { add("F: ${it}g") }
                    }
                    Text(
                        text = parts.joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFullCheckIn() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Macros: Not logged for today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Log Macros",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

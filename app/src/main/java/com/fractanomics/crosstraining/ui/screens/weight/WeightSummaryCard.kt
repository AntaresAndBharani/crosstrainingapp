package com.fractanomics.crosstraining.ui.screens.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.trimmed
import java.util.Locale

/**
 * KPI summary card rendering the athlete's latest weight, delta percentage,
 * 7-day rolling average, and a one-tap unit toggle between "kg" and "lbs".
 */
@Composable
fun WeightSummaryCard(
    entries: List<WeightEntry>,
    unit: String,
    onToggleUnit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSorted = entries
        .filter { it.deletedAtMillis == null }
        .sortedBy { it.date }

    val latestEntry = activeSorted.lastOrNull()
    val isImperial = unit.equals("lbs", ignoreCase = true)

    // Current weight display value
    val currentDisplayWeight = latestEntry?.let { entry ->
        if (isImperial) WeightAnalytics.kgToLbs(entry.weightKg) else entry.weightKg
    }

    // 7-day moving average computation over [latestDate - 6, latestDate]
    val sma7Display = latestEntry?.let { latest ->
        val windowStart = latest.date.minusDays(6)
        val inWindow = activeSorted.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(latest.date) }
        if (inWindow.size >= 3) {
            val avgKg = inWindow.map { it.weightKg }.average()
            if (isImperial) WeightAnalytics.kgToLbs(avgKg) else avgKg
        } else null
    }

    // Delta percentage relative to earliest entry in the dataset or baseline
    val firstEntry = activeSorted.firstOrNull()
    val deltaPercent: Double? = if (latestEntry != null && firstEntry != null && firstEntry != latestEntry && firstEntry.weightKg > 0.0) {
        ((latestEntry.weightKg - firstEntry.weightKg) / firstEntry.weightKg) * 100.0
    } else null

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Weight",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Compact Unit Toggle Switch (kg / lbs)
                UnitTogglePill(
                    selectedUnit = unit,
                    onToggleUnit = onToggleUnit
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Large Weight Display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentDisplayWeight?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = unit.lowercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Delta percentage badge if available
                if (deltaPercent != null) {
                    val isGain = deltaPercent > 0.0
                    val isNeutral = Math.abs(deltaPercent) < 0.05
                    val badgeColor = when {
                        isNeutral -> MaterialTheme.colorScheme.surfaceVariant
                        isGain -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val textColor = when {
                        isNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
                        isGain -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    val sign = if (isGain) "+" else ""

                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 7-Day Moving Average Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "7-Day Moving Average",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (sma7Display != null) {
                        " "
                    } else {
                        "Need ≥ 3 entries"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun UnitTogglePill(
    selectedUnit: String,
    onToggleUnit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isKg = !selectedUnit.equals("lbs", ignoreCase = true)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (isKg) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { onToggleUnit("kg") }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "kg",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isKg) FontWeight.Bold else FontWeight.Normal,
                color = if (isKg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (!isKg) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { onToggleUnit("lbs") }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "lbs",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (!isKg) FontWeight.Bold else FontWeight.Normal,
                color = if (!isKg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

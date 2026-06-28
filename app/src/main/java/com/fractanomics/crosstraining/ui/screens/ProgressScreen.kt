package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.ChartPoint
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.LineChart
import com.fractanomics.crosstraining.ui.components.SectionCard
import com.fractanomics.crosstraining.ui.formatShort
import com.fractanomics.crosstraining.ui.trimmed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(viewModel: AppViewModel, outerPadding: PaddingValues) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val repMaxes by viewModel.repMaxes.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Exercise?>(null) }
    val current = selected ?: exercises.firstOrNull()

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Progress") }) }
    ) { pad ->
        if (exercises.isEmpty()) {
            EmptyState("Add exercises and log sessions to see progress here.", Modifier.padding(pad))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Dropdown(
                label = "Exercise",
                options = exercises,
                selected = current,
                labelOf = { it.name },
                onSelect = { selected = it }
            )

            if (current == null) return@Column

            // --- Rep-max progression chart ---------------------------------
            if (current.tracksRepMax) {
                val rmSeries = repMaxes.filter { it.exerciseId == current.id }
                val repOptions = rmSeries.map { it.reps }.distinct().sorted()
                if (repOptions.isNotEmpty()) {
                    var selectedReps by remember(current.id) { mutableStateOf<Int?>(null) }
                    val reps = selectedReps ?: if (repOptions.contains(1)) 1 else repOptions.first()
                    SectionCard(title = "Rep-max progression") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repOptions.forEach { r ->
                                FilterChip(
                                    selected = r == reps,
                                    onClick = { selectedReps = r },
                                    label = { Text("${r}RM") }
                                )
                            }
                        }
                        val series = rmSeries.filter { it.reps == reps }
                            .sortedBy { it.date }
                            .map { ChartPoint(it.date.formatShort(), it.weight.toFloat()) }
                        LineChart(points = series)
                    }
                }
            }

            // --- Working-weight progression chart --------------------------
            run {
                val series = sessions.filter { it.session.mainExerciseId == current.id }
                    .sortedBy { it.session.date }
                    .mapNotNull { item ->
                        val top = item.sets.mapNotNull { it.weight ?: it.metricValue }.maxOrNull()
                        top?.let { ChartPoint(item.session.date.formatShort(), it.toFloat()) }
                    }
                if (series.isNotEmpty()) {
                    SectionCard(title = "Working-weight progression (chart)") {
                        LineChart(points = series, lineColor = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            // --- Rep-max bests ---------------------------------------------
            if (current.tracksRepMax) {
                val rms = repMaxes.filter { it.exerciseId == current.id }
                SectionCard(title = "Rep-max bests") {
                    if (rms.isEmpty()) {
                        Text("No rep-maxes recorded yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val bestByReps = rms.groupBy { it.reps }
                            .mapValues { (_, list) -> list.maxBy { it.weight } }
                            .toSortedMap()
                        bestByReps.forEach { (reps, rm) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${reps}RM", fontWeight = FontWeight.SemiBold)
                                Text("${rm.weight.trimmed()} kg")
                                Text(rm.date.formatShort(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (rms.isNotEmpty()) {
                    SectionCard(title = "Rep-max history") {
                        rms.sortedByDescending { it.date }.forEach { rm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${rm.reps}RM · ${rm.weight.trimmed()} kg")
                                Text(rm.date.formatShort(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // --- Session / complex progression -----------------------------
            val exerciseSessions = sessions.filter { it.session.mainExerciseId == current.id }
            SectionCard(title = "Working-weight (per session)") {
                if (exerciseSessions.isEmpty()) {
                    Text("No sessions logged for this exercise yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    exerciseSessions
                        .sortedByDescending { it.session.date }
                        .forEach { item ->
                            val top = item.sets.mapNotNull { it.weight ?: it.metricValue }.maxOrNull()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(item.session.date.formatShort(), fontWeight = FontWeight.SemiBold)
                                    val sub = listOfNotNull(
                                        item.session.format.ifBlank { null },
                                        item.session.repScheme.ifBlank { null }
                                    ).joinToString(" · ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    top?.let { "top ${it.trimmed()} ${current.unit}" } ?: "—",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                }
            }
        }
    }
}

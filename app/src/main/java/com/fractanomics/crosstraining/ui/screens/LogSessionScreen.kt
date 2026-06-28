package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.SessionSet
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS
import com.fractanomics.crosstraining.ui.components.Dropdown
import kotlinx.coroutines.launch
import java.time.LocalDate

private class SetRow(reps: String = "", value: String = "") {
    var reps by mutableStateOf(reps)
    var value by mutableStateOf(value)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSessionScreen(viewModel: AppViewModel, outerPadding: PaddingValues) {
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Form state
    var selectedCycleId by remember { mutableStateOf<Long?>(null) }
    val effectiveCycleId = selectedCycleId ?: activeCycle?.id
    var selectedRoutine by remember { mutableStateOf<Routine?>(null) }
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var newExerciseName by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var format by remember { mutableStateOf("") }
    var repScheme by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val sets = remember { listOf(SetRow()).toMutableStateList() }

    var recordRm by remember { mutableStateOf(false) }
    var rmReps by remember { mutableStateOf("1") }
    var rmWeight by remember { mutableStateOf("") }

    // The metric we collect per set depends on the chosen main exercise.
    val metric = selectedExercise?.metricType ?: MetricType.WEIGHT
    val valueLabel = if (metric == MetricType.WEIGHT) "kg" else metric.defaultUnit
    val canRecordRm = (selectedExercise?.tracksRepMax ?: (newExerciseName.isNotBlank()))

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Log Session") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cycle selector
            val cycleOptions = cycles
            Dropdown(
                label = "Cycle",
                options = cycleOptions,
                selected = cycleOptions.firstOrNull { it.id == effectiveCycleId },
                labelOf = { it.name + if (it.isActive) " (active)" else "" },
                onSelect = { selectedCycleId = it.id },
                placeholder = "No cycle — create one in Cycles tab"
            )

            // Routine (complex) selector — autofills format + target exercise.
            Dropdown(
                label = "Routine / complex (optional)",
                options = routines,
                selected = selectedRoutine,
                labelOf = { it.name },
                onSelect = { routine ->
                    selectedRoutine = routine
                    if (format.isBlank()) format = routine.defaultFormat
                    exercises.firstOrNull { it.id == routine.mainExerciseId }?.let {
                        selectedExercise = it
                    }
                }
            )

            // Main exercise selector + free-text create.
            Dropdown(
                label = "Main exercise",
                options = exercises,
                selected = selectedExercise,
                labelOf = { it.name },
                onSelect = { selectedExercise = it; newExerciseName = "" }
            )
            OutlinedTextField(
                value = newExerciseName,
                onValueChange = { newExerciseName = it },
                label = { Text("…or type a new exercise to add it") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            com.fractanomics.crosstraining.ui.components.DateField(
                "Date", date, { date = it }, Modifier.fillMaxWidth()
            )

            // Format with quick chips.
            OutlinedTextField(
                value = format,
                onValueChange = { format = it },
                label = { Text("Format") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WORKOUT_FORMATS.forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = { format = f },
                        label = { Text(f) }
                    )
                }
            }

            OutlinedTextField(
                value = repScheme,
                onValueChange = { repScheme = it },
                label = { Text("Rep scheme (e.g. 3-2-1-3-2-1-3-2-1-1-1-1)") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Sets — weight/${valueLabel} per round", style = MaterialTheme.typography.titleMedium)
            sets.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = row.reps,
                        onValueChange = { row.reps = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                    OutlinedTextField(
                        value = row.value,
                        onValueChange = { row.value = it },
                        label = { Text(valueLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp)
                    )
                    IconButton(
                        onClick = { if (sets.size > 1) sets.removeAt(index) },
                        enabled = sets.size > 1
                    ) { Icon(Icons.Filled.Close, contentDescription = "Remove set") }
                }
            }
            OutlinedButton(onClick = { sets.add(SetRow()) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add set")
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = recordRm && canRecordRm,
                    onCheckedChange = { recordRm = it },
                    enabled = canRecordRm
                )
                Text(
                    if (canRecordRm) "I hit a new rep-max on the main exercise"
                    else "Rep-max tracking applies to weighted exercises"
                )
            }
            if (recordRm && canRecordRm) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rmReps,
                        onValueChange = { rmReps = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps (RM)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                    OutlinedTextField(
                        value = rmWeight,
                        onValueChange = { rmWeight = it },
                        label = { Text("Weight (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp)
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val cycleId = effectiveCycleId
                    if (cycleId == null) {
                        scope.launch { snackbar.showSnackbar("Create and select a cycle first (Cycles tab).") }
                        return@Button
                    }
                    if (selectedExercise == null && newExerciseName.isBlank()) {
                        scope.launch { snackbar.showSnackbar("Pick or type a main exercise.") }
                        return@Button
                    }
                    val builtSets = sets.mapNotNull { row ->
                        val reps = row.reps.toIntOrNull() ?: return@mapNotNull null
                        val v = row.value.replace(',', '.').toDoubleOrNull()
                        SessionSet(
                            sessionId = 0,
                            position = 0,
                            reps = reps,
                            weight = if (metric == MetricType.WEIGHT) v else null,
                            metricValue = if (metric != MetricType.WEIGHT) v else null
                        )
                    }
                    val rmRepsVal = rmReps.toIntOrNull()
                    val rmWeightVal = rmWeight.replace(',', '.').toDoubleOrNull()
                    viewModel.saveSession(
                        cycleId = cycleId,
                        routineId = selectedRoutine?.id,
                        existingExerciseId = selectedExercise?.id,
                        newExerciseName = newExerciseName.ifBlank { null },
                        date = date,
                        format = format.trim(),
                        repScheme = repScheme.trim(),
                        notes = notes.trim(),
                        sets = builtSets,
                        newRepMaxReps = if (recordRm && canRecordRm) rmRepsVal else null,
                        newRepMaxWeight = if (recordRm && canRecordRm) rmWeightVal else null
                    )
                    // Reset the volatile parts of the form.
                    selectedRoutine = null
                    newExerciseName = ""
                    repScheme = ""
                    notes = ""
                    sets.clear(); sets.add(SetRow())
                    recordRm = false; rmWeight = ""
                    scope.launch { snackbar.showSnackbar("Session saved.") }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save session") }
        }
    }
}

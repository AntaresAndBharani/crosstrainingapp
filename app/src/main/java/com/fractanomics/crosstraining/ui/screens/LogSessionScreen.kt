package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.BlockDraft
import com.fractanomics.crosstraining.ui.SessionDraft
import com.fractanomics.crosstraining.ui.SetDraft
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS
import com.fractanomics.crosstraining.ui.components.DateField
import com.fractanomics.crosstraining.ui.components.Dropdown
import kotlinx.coroutines.launch
import java.time.LocalDate

private class SetState {
    var reps by mutableStateOf("")
    var value by mutableStateOf("")
    var group by mutableStateOf("")
    var isWarmup by mutableStateOf(false)
    var isFailed by mutableStateOf(false)
}

private class BlockState {
    var name by mutableStateOf("")
    var kind by mutableStateOf(BlockKind.STRENGTH)
    var format by mutableStateOf("")
    var scheme by mutableStateOf("")
    var exercise by mutableStateOf<Exercise?>(null)
    var newExerciseName by mutableStateOf("")
    var routine by mutableStateOf<Routine?>(null)
    var description by mutableStateOf("")
    var resultText by mutableStateOf("")
    var resultValue by mutableStateOf("")
    var recordRm by mutableStateOf(false)
    var rmReps by mutableStateOf("1")
    var rmWeight by mutableStateOf("")
    val sets: SnapshotStateList<SetState> = mutableStateListOf(SetState())
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

    var selectedCycleId by remember { mutableStateOf<Long?>(null) }
    val effectiveCycleId = selectedCycleId ?: activeCycle?.id
    var date by remember { mutableStateOf(LocalDate.now()) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val blocks = remember { mutableStateListOf(BlockState()) }

    fun resetForm() {
        title = ""
        notes = ""
        blocks.clear()
        blocks.add(BlockState())
    }

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
            Dropdown(
                label = "Cycle",
                options = cycles,
                selected = cycles.firstOrNull { it.id == effectiveCycleId },
                labelOf = { it.name + if (it.isActive) " (active)" else "" },
                onSelect = { selectedCycleId = it.id },
                placeholder = "No cycle — create one in Cycles tab"
            )
            DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Session title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            blocks.forEachIndexed { index, block ->
                BlockEditor(
                    index = index,
                    block = block,
                    exercises = exercises,
                    routines = routines,
                    canRemove = blocks.size > 1,
                    onRemove = { blocks.removeAt(index) }
                )
            }

            OutlinedButton(
                onClick = { blocks.add(BlockState()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add block")
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Session notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val cycleId = effectiveCycleId
                    if (cycleId == null) {
                        scope.launch { snackbar.showSnackbar("Create and select a cycle first (Cycles tab).") }
                        return@Button
                    }
                    val blockDrafts = blocks.mapNotNull { it.toDraftOrNull() }
                    if (blockDrafts.isEmpty()) {
                        scope.launch { snackbar.showSnackbar("Add at least one block with content.") }
                        return@Button
                    }
                    viewModel.saveSession(
                        SessionDraft(
                            cycleId = cycleId,
                            date = date,
                            title = title.trim(),
                            notes = notes.trim(),
                            blocks = blockDrafts
                        )
                    )
                    resetForm()
                    scope.launch { snackbar.showSnackbar("Session saved.") }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save session") }
        }
    }
}

/** Build a [BlockDraft] from a [BlockState], or null if the block is empty. */
private fun BlockState.toDraftOrNull(): BlockDraft? {
    val metric = exercise?.metricType ?: MetricType.WEIGHT
    val setDrafts = sets.mapNotNull { ss ->
        val reps = ss.reps.toIntOrNull() ?: return@mapNotNull null
        val v = ss.value.replace(',', '.').toDoubleOrNull()
        SetDraft(
            reps = reps,
            weight = if (metric == MetricType.WEIGHT) v else null,
            metricValue = if (metric != MetricType.WEIGHT) v else null,
            groupIndex = ss.group.toIntOrNull(),
            isWarmup = ss.isWarmup,
            isFailed = ss.isFailed
        )
    }
    val hasContent = name.isNotBlank() || exercise != null || newExerciseName.isNotBlank() ||
        setDrafts.isNotEmpty() || description.isNotBlank() || resultText.isNotBlank()
    if (!hasContent) return null
    return BlockDraft(
        name = name.trim(),
        kind = kind,
        format = format.trim(),
        scheme = scheme.trim(),
        existingExerciseId = exercise?.id,
        newExerciseName = newExerciseName.ifBlank { null },
        routineId = routine?.id,
        description = description.trim(),
        resultText = resultText.trim(),
        resultValue = resultValue.replace(',', '.').toDoubleOrNull(),
        sets = setDrafts,
        newRepMaxReps = if (recordRm) rmReps.toIntOrNull() else null,
        newRepMaxWeight = if (recordRm) rmWeight.replace(',', '.').toDoubleOrNull() else null
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlockEditor(
    index: Int,
    block: BlockState,
    exercises: List<Exercise>,
    routines: List<Routine>,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val metric = block.exercise?.metricType ?: MetricType.WEIGHT
    val valueLabel = if (metric == MetricType.WEIGHT) "kg" else metric.defaultUnit
    val canRecordRm = block.exercise?.tracksRepMax ?: block.newExerciseName.isNotBlank()
    val isMetcon = block.kind == BlockKind.METCON

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Block ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRemove, enabled = canRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove block")
                }
            }

            OutlinedTextField(
                value = block.name,
                onValueChange = { block.name = it },
                label = { Text("Block name (e.g. Snatch Waves)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Dropdown(
                label = "Type",
                options = BlockKind.entries,
                selected = block.kind,
                labelOf = { it.label },
                onSelect = { block.kind = it }
            )

            Dropdown(
                label = "Routine / complex (optional)",
                options = routines,
                selected = block.routine,
                labelOf = { it.name },
                onSelect = { routine ->
                    block.routine = routine
                    if (block.name.isBlank()) block.name = routine.name
                    if (block.format.isBlank()) block.format = routine.defaultFormat
                    exercises.firstOrNull { it.id == routine.mainExerciseId }?.let {
                        block.exercise = it
                        block.newExerciseName = ""
                    }
                }
            )

            Dropdown(
                label = "Main exercise (optional)",
                options = exercises,
                selected = block.exercise,
                labelOf = { it.name },
                onSelect = { block.exercise = it; block.newExerciseName = "" }
            )
            OutlinedTextField(
                value = block.newExerciseName,
                onValueChange = { block.newExerciseName = it },
                label = { Text("…or type a new exercise to add it") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = block.format,
                onValueChange = { block.format = it },
                label = { Text("Format (e.g. E3MOM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WORKOUT_FORMATS.forEach { f ->
                    FilterChip(
                        selected = block.format == f,
                        onClick = { block.format = f },
                        label = { Text(f) }
                    )
                }
            }

            OutlinedTextField(
                value = block.scheme,
                onValueChange = { block.scheme = it },
                label = { Text("Rep scheme (e.g. 3-2-1-3-2-1-1-1-1)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (isMetcon) {
                OutlinedTextField(
                    value = block.description,
                    onValueChange = { block.description = it },
                    label = { Text("Movements / intervals") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = block.resultText,
                        onValueChange = { block.resultText = it },
                        label = { Text("Score (e.g. 2 rounds + 15 reps)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider()
            Text("Sets — weight/$valueLabel per round", style = MaterialTheme.typography.titleSmall)
            block.sets.forEachIndexed { i, setState ->
                SetEditorRow(
                    set = setState,
                    valueLabel = valueLabel,
                    canRemove = block.sets.size > 1,
                    onRemove = { block.sets.removeAt(i) }
                )
            }
            OutlinedButton(onClick = { block.sets.add(SetState()) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add set")
            }

            if (canRecordRm) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = block.recordRm, onCheckedChange = { block.recordRm = it })
                    Text("New rep-max on this lift")
                }
                if (block.recordRm) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = block.rmReps,
                            onValueChange = { block.rmReps = it.filter { c -> c.isDigit() } },
                            label = { Text("Reps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp)
                        )
                        OutlinedTextField(
                            value = block.rmWeight,
                            onValueChange = { block.rmWeight = it },
                            label = { Text("Weight (kg)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetEditorRow(
    set: SetState,
    valueLabel: String,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = set.reps,
                onValueChange = { set.reps = it.filter { c -> c.isDigit() } },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(84.dp)
            )
            OutlinedTextField(
                value = set.value,
                onValueChange = { set.value = it },
                label = { Text(valueLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(104.dp)
            )
            OutlinedTextField(
                value = set.group,
                onValueChange = { set.group = it.filter { c -> c.isDigit() } },
                label = { Text("Wave") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp)
            )
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove set")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = set.isWarmup,
                onClick = { set.isWarmup = !set.isWarmup },
                label = { Text("Warm-up") }
            )
            FilterChip(
                selected = set.isFailed,
                onClick = { set.isFailed = !set.isFailed },
                label = { Text("Failed") }
            )
        }
    }
}

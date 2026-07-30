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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.util.RepScheme
import com.fractanomics.crosstraining.ui.BlockDraft
import com.fractanomics.crosstraining.ui.SessionDraft
import com.fractanomics.crosstraining.ui.SetDraft
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS
import androidx.compose.material.icons.filled.AutoAwesome
import com.fractanomics.crosstraining.ui.components.DateField
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.QuickAddWorkoutDialog
import com.fractanomics.crosstraining.util.ParsedWorkout
import com.fractanomics.crosstraining.ui.trimmed
import kotlinx.coroutines.launch
import java.time.LocalDate

// --- Plain seeds used to (re)initialise the editor form ----------------------

data class SetSeed(
    val reps: String = "",
    val value: String = "",
    val group: String = "",
    val warm: Boolean = false,
    val failed: Boolean = false
)

data class BlockSeed(
    val name: String = "",
    val kind: BlockKind = BlockKind.STRENGTH,
    val format: String = "",
    val scheme: String = "",
    val exerciseId: Long? = null,
    val routineId: Long? = null,
    val description: String = "",
    val resultText: String = "",
    val resultValue: String = "",
    val sets: List<SetSeed> = listOf(SetSeed())
)

data class SessionSeed(
    val cycleId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val notes: String = "",
    val blocks: List<BlockSeed> = listOf(BlockSeed())
)

/** A fresh, empty session form (used by the Log tab). */
fun emptySessionSeed(): SessionSeed = SessionSeed()

/** Build a seed from an existing session (for Edit, or Copy with today's date). */
fun sessionSeed(s: SessionWithBlocks, dateOverride: LocalDate? = null): SessionSeed =
    SessionSeed(
        cycleId = s.session.cycleId,
        date = dateOverride ?: s.session.date,
        title = s.session.title,
        notes = s.session.notes,
        blocks = s.blocks.sortedBy { it.block.position }.map { bws ->
            BlockSeed(
                name = bws.block.name,
                kind = bws.block.kind,
                format = bws.block.format,
                scheme = bws.block.scheme,
                exerciseId = bws.block.mainExerciseId,
                routineId = bws.block.routineId,
                description = bws.block.description,
                resultText = bws.block.resultText,
                resultValue = bws.block.resultValue?.trimmed() ?: "",
                sets = bws.sets.sortedBy { it.position }.map { st ->
                    SetSeed(
                        reps = st.reps.toString(),
                        value = (st.weight ?: st.metricValue)?.trimmed() ?: "",
                        group = st.groupIndex?.toString() ?: "",
                        warm = st.isWarmup,
                        failed = st.isFailed
                    )
                }.ifEmpty { listOf(SetSeed()) }
            )
        }.ifEmpty { listOf(BlockSeed()) }
    )

// --- Mutable form state holders ----------------------------------------------

private class SetState(
    reps: String = "", value: String = "", group: String = "",
    warm: Boolean = false, failed: Boolean = false
) {
    var reps by mutableStateOf(reps)
    var value by mutableStateOf(value)
    var group by mutableStateOf(group)
    var isWarmup by mutableStateOf(warm)
    var isFailed by mutableStateOf(failed)
}

private class BlockState(
    name: String = "", kind: BlockKind = BlockKind.STRENGTH, format: String = "", scheme: String = "",
    exercise: Exercise? = null, newExerciseName: String = "", routine: Routine? = null,
    description: String = "", resultText: String = "", resultValue: String = "",
    sets: List<SetState> = listOf(SetState())
) {
    var name by mutableStateOf(name)
    var kind by mutableStateOf(kind)
    var format by mutableStateOf(format)
    var scheme by mutableStateOf(scheme)
    var exercise by mutableStateOf(exercise)
    var newExerciseName by mutableStateOf(newExerciseName)
    var routine by mutableStateOf(routine)
    var description by mutableStateOf(description)
    var resultText by mutableStateOf(resultText)
    var resultValue by mutableStateOf(resultValue)
    var recordRm by mutableStateOf(false)
    var rmReps by mutableStateOf("1")
    var rmWeight by mutableStateOf("")
    val sets: SnapshotStateList<SetState> = sets.toMutableStateList()
}

private fun buildBlockState(seed: BlockSeed, exercises: List<Exercise>, routines: List<Routine>) =
    BlockState(
        name = seed.name,
        kind = seed.kind,
        format = seed.format,
        scheme = seed.scheme,
        exercise = exercises.firstOrNull { it.id == seed.exerciseId },
        routine = routines.firstOrNull { it.id == seed.routineId },
        description = seed.description,
        resultText = seed.resultText,
        resultValue = seed.resultValue,
        sets = seed.sets.map { SetState(it.reps, it.value, it.group, it.warm, it.failed) }
    )

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

// --- Shared editor body ------------------------------------------------------

/**
 * The session form, reused for logging (clears after save), editing and copying.
 * [seed] provides the initial values; [seedKey] is used as a remember key so the
 * state resets when a different session is opened. [onSubmit] persists the
 * resulting draft; in non-clearing mode [onBack] is invoked afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorBody(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    screenTitle: String,
    seed: SessionSeed,
    seedKey: Any,
    saveLabel: String,
    clearAfterSave: Boolean,
    onBack: (() -> Unit)?,
    onSubmit: (SessionDraft) -> Unit
) {
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var formKey by remember(seedKey) { mutableIntStateOf(0) }
    val key = seedKey to formKey

    var selectedCycleId by remember(key) { mutableStateOf(seed.cycleId) }
    var date by remember(key) { mutableStateOf(seed.date) }
    var title by remember(key) { mutableStateOf(seed.title) }
    var notes by remember(key) { mutableStateOf(seed.notes) }
    val blocks = remember(key) {
        seed.blocks.map { buildBlockState(it, exercises, routines) }.toMutableStateList()
    }
    val effectiveCycleId = selectedCycleId ?: activeCycle?.id
    var showQuickAddDialog by remember { mutableStateOf(false) }

    if (showQuickAddDialog) {
        QuickAddWorkoutDialog(
            exercises = exercises,
            routines = routines,
            onDismiss = { showQuickAddDialog = false },
            onConfirm = { parsed ->
                val newBlock = BlockState(
                    name = parsed.name,
                    kind = parsed.kind,
                    format = parsed.format,
                    scheme = parsed.scheme,
                    exercise = exercises.firstOrNull { it.id == parsed.existingExerciseId },
                    newExerciseName = parsed.newExerciseName ?: "",
                    routine = routines.firstOrNull { it.id == parsed.routineId },
                    sets = parsed.sets.map { s ->
                        SetState(
                            reps = s.reps.toString(),
                            value = s.weight?.trimmed() ?: "",
                            warm = s.isWarmup
                        )
                    }.ifEmpty { listOf(SetState()) }
                )
                blocks.add(newBlock)
            }
        )
    }

    val routinesWithBlocks by viewModel.routinesWithBlocks.collectAsStateWithLifecycle()
    var selectedRoutineWithBlocks by remember(key) { mutableStateOf<RoutineWithBlocks?>(null) }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
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

            Dropdown(
                label = "Load Daily Routine",
                options = routinesWithBlocks,
                selected = selectedRoutineWithBlocks,
                labelOf = { it.routine.name },
                onSelect = { rwb ->
                    selectedRoutineWithBlocks = rwb
                    if (title.isBlank()) title = rwb.routine.name
                    blocks.clear()
                    rwb.blocks.sortedBy { it.position }.forEach { blk ->
                        val targetEx = blk.exerciseIdsCsv.split(",")
                            .mapNotNull { idStr -> idStr.trim().toLongOrNull() }
                            .mapNotNull { id -> exercises.firstOrNull { it.id == id } }
                            .firstOrNull()

                        val parsedRepsList = RepScheme.parse(blk.targetRepsScheme, blk.setsCount)

                        val newBlockState = BlockState(
                            name = blk.name.ifBlank { blk.kind.label },
                            kind = blk.kind,
                            format = blk.format,
                            scheme = blk.targetRepsScheme,
                            exercise = targetEx,
                            description = blk.notes,
                            sets = parsedRepsList.map { reps ->
                                SetState(reps = reps.toString(), value = "")
                            }.toMutableStateList()
                        )
                        blocks.add(newBlockState)
                    }
                },
                placeholder = "Select a Daily Routine to load..."
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showQuickAddDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Quick Add / Parse")
                }

                OutlinedButton(
                    onClick = { blocks.add(BlockState()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add block")
                }
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
                    onSubmit(
                        SessionDraft(
                            cycleId = cycleId,
                            date = date,
                            title = title.trim(),
                            notes = notes.trim(),
                            blocks = blockDrafts
                        )
                    )
                    if (clearAfterSave) {
                        formKey += 1
                        scope.launch { snackbar.showSnackbar("Session saved.") }
                    } else {
                        onBack?.invoke()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(saveLabel) }
        }
    }
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
                OutlinedTextField(
                    value = block.resultText,
                    onValueChange = { block.resultText = it },
                    label = { Text("Score (e.g. 2 rounds + 15 reps)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sets — weight/$valueLabel per round", style = MaterialTheme.typography.titleSmall)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val firstWeight = block.sets.firstOrNull()?.value ?: ""
                        if (firstWeight.isNotBlank()) {
                            block.sets.forEach { it.value = firstWeight }
                        }
                    },
                    label = { Text("Copy 1st Weight to All", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        var current = block.sets.firstOrNull()?.value?.toDoubleOrNull() ?: 0.0
                        block.sets.forEach { set ->
                            if (set.value.isBlank()) {
                                set.value = current.trimmed()
                            }
                            current += 2.5
                        }
                    },
                    label = { Text("+2.5 kg / set", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        var current = block.sets.firstOrNull()?.value?.toDoubleOrNull() ?: 0.0
                        block.sets.forEach { set ->
                            if (set.value.isBlank()) {
                                set.value = current.trimmed()
                            }
                            current += 5.0
                        }
                    },
                    label = { Text("+5 kg / set", style = MaterialTheme.typography.labelSmall) }
                )
            }

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

// --- Edit / Copy entry screen ------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    sessionId: Long,
    copy: Boolean,
    onBack: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val target = sessions.firstOrNull { it.session.id == sessionId }

    if (target == null) {
        Scaffold(
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
            topBar = {
                TopAppBar(
                    title = { Text(if (copy) "Copy Session" else "Edit Session") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            EmptyState("Session not available.", Modifier.padding(pad))
        }
        return
    }

    val seed = remember(sessionId, copy) {
        sessionSeed(target, dateOverride = if (copy) LocalDate.now() else null)
    }
    SessionEditorBody(
        viewModel = viewModel,
        outerPadding = outerPadding,
        screenTitle = if (copy) "Copy Session" else "Edit Session",
        seed = seed,
        seedKey = "${if (copy) "copy" else "edit"}-$sessionId",
        saveLabel = if (copy) "Save copy" else "Update session",
        clearAfterSave = false,
        onBack = onBack,
        onSubmit = { draft ->
            if (copy) viewModel.saveSession(draft) else viewModel.updateSession(sessionId, draft)
        }
    )
}

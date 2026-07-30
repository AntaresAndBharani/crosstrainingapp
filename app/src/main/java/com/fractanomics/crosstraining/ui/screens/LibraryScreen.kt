package com.fractanomics.crosstraining.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AppViewModel, outerPadding: PaddingValues) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routinesWithBlocks by viewModel.routinesWithBlocks.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var showExerciseEditor by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<RoutineWithBlocks?>(null) }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(context.contentResolver, it) { ok ->
                scope.launch { snackbar.showSnackbar(if (ok) "Backup exported" else "Export failed") }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export backup (CSV)") },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch("crosstraining-backup-${LocalDate.now()}.csv")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup (CSV)") },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (demoMode) "Switch to my data" else "Try demo data") },
                            onClick = {
                                showMenu = false
                                val enable = !demoMode
                                viewModel.setDemoMode(enable)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (enable) "Demo data active — your real data is untouched"
                                        else "Back to your data"
                                    )
                                }
                            }
                        )
                        if (demoMode) {
                            DropdownMenuItem(
                                text = { Text("Reset demo data") },
                                onClick = {
                                    showMenu = false
                                    viewModel.resetDemoData()
                                    scope.launch { snackbar.showSnackbar("Demo data reset") }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (tab == 0) {
                    editingExercise = null; showExerciseEditor = true
                } else {
                    editingRoutine = null; showRoutineEditor = true
                }
            }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Exercises") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Daily Routines") })
            }
            if (tab == 0) {
                if (exercises.isEmpty()) {
                    EmptyState("No exercises yet. Tap + to add one.")
                } else {
                    ScreenList {
                        items(exercises, key = { it.id }) { ex ->
                            ExerciseCard(
                                exercise = ex,
                                onEdit = { editingExercise = ex; showExerciseEditor = true },
                                onDelete = { viewModel.deleteExercise(ex) }
                            )
                        }
                    }
                }
            } else {
                if (routinesWithBlocks.isEmpty()) {
                    EmptyState("No daily routines created yet. Tap + to define one.")
                } else {
                    ScreenList {
                        items(routinesWithBlocks, key = { it.routine.id }) { rwb ->
                            RoutineCard(
                                routineWithBlocks = rwb,
                                exercises = exercises,
                                onEdit = { editingRoutine = rwb; showRoutineEditor = true },
                                onDelete = { viewModel.deleteRoutine(rwb.routine) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExerciseEditor) {
        ExerciseEditorDialog(
            original = editingExercise,
            onDismiss = { showExerciseEditor = false },
            onSave = { ex -> viewModel.saveExercise(ex); showExerciseEditor = false }
        )
    }
    if (showRoutineEditor) {
        RoutineEditorDialog(
            original = editingRoutine,
            exercises = exercises,
            onDismiss = { showRoutineEditor = false },
            onSave = { routine, blocks ->
                viewModel.saveRoutineWithBlocks(routine, blocks)
                showRoutineEditor = false
            }
        )
    }
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false; pendingImportUri = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Importing this backup will replace all current cycles, exercises, " +
                        "routines and sessions on this device. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    showImportConfirm = false
                    pendingImportUri = null
                    if (uri != null) {
                        viewModel.importBackup(context.contentResolver, uri) { ok ->
                            scope.launch {
                                snackbar.showSnackbar(if (ok) "Backup imported" else "Import failed — check the file")
                            }
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false; pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${exercise.category.label} · ${exercise.metricType.label} (${exercise.unit})" +
                    if (exercise.tracksRepMax) " · tracks RM" else "",
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(
    routineWithBlocks: RoutineWithBlocks,
    exercises: List<Exercise>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val routine = routineWithBlocks.routine
    val blocks = routineWithBlocks.blocks.sortedBy { it.position }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (routine.description.isNotBlank()) {
                Text(routine.description, style = MaterialTheme.typography.bodyMedium)
            }

            if (blocks.isNotEmpty()) {
                HorizontalDivider()
                Text("Blocks (${blocks.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                blocks.forEachIndexed { idx, blk ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. ${blk.name.ifBlank { blk.kind.label }}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    blk.kind.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        val detailLine = listOfNotNull(
                            blk.format.takeIf { it.isNotBlank() }?.let { "Format: $it" },
                            if (blk.setsCount > 1) "${blk.setsCount} sets" else null
                        ).joinToString(" · ")

                        if (detailLine.isNotBlank()) {
                            Text(detailLine, style = MaterialTheme.typography.bodySmall)
                        }

                        val targetExNames = blk.exerciseIdsCsv.split(",")
                            .mapNotNull { idStr -> idStr.trim().toLongOrNull() }
                            .mapNotNull { id -> exercises.firstOrNull { it.id == id }?.name }

                        if (targetExNames.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                targetExNames.forEach { exName ->
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(exName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (routine.defaultFormat.isNotBlank()) {
                Text("Format: ${routine.defaultFormat}", style = MaterialTheme.typography.bodySmall)
            }

            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ExerciseEditorDialog(
    original: Exercise?,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var category by remember { mutableStateOf(original?.category ?: ExerciseCategory.BARBELL) }
    var metric by remember { mutableStateOf(original?.metricType ?: MetricType.WEIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Dropdown(
                    label = "Category",
                    options = ExerciseCategory.entries,
                    selected = category,
                    labelOf = { it.label },
                    onSelect = { category = it }
                )
                Dropdown(
                    label = "Tracked metric",
                    options = MetricType.entries,
                    selected = metric,
                    labelOf = { "${it.label} (${it.defaultUnit})" + if (it.tracksRepMax) " · RM" else "" },
                    onSelect = { metric = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (original ?: Exercise(
                            name = "",
                            category = category,
                            metricType = metric,
                            unit = metric.defaultUnit,
                            tracksRepMax = metric.tracksRepMax
                        )).copy(
                            name = name.trim(),
                            category = category,
                            metricType = metric,
                            unit = metric.defaultUnit,
                            tracksRepMax = metric.tracksRepMax
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private class RoutineBlockFormState(
    name: String = "",
    kind: BlockKind = BlockKind.WEIGHTLIFTING,
    format: String = "EMOM",
    setsCount: String = "4",
    selectedExerciseIds: List<Long> = emptyList(),
    notes: String = ""
) {
    var name by mutableStateOf(name)
    var kind by mutableStateOf(kind)
    var format by mutableStateOf(format)
    var setsCount by mutableStateOf(setsCount)
    var selectedExerciseIds by mutableStateOf(selectedExerciseIds)
    var notes by mutableStateOf(notes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditorDialog(
    original: RoutineWithBlocks?,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSave: (Routine, List<RoutineBlock>) -> Unit
) {
    var name by remember { mutableStateOf(original?.routine?.name ?: "") }
    var description by remember { mutableStateOf(original?.routine?.description ?: "") }

    val blockStates = remember(original) {
        val initialBlocks = original?.blocks?.sortedBy { it.position }?.map { blk ->
            RoutineBlockFormState(
                name = blk.name,
                kind = blk.kind,
                format = blk.format,
                setsCount = blk.setsCount.toString(),
                selectedExerciseIds = blk.exerciseIdsCsv.split(",")
                    .mapNotNull { it.trim().toLongOrNull() },
                notes = blk.notes
            )
        } ?: listOf(RoutineBlockFormState(name = "Block 1", kind = BlockKind.WEIGHTLIFTING))

        initialBlocks.toMutableStateList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New Daily Routine" else "Edit Daily Routine") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine / Workout Name (e.g. Monday Snatch & Metcon)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Workout Goal") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text("Routine Blocks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                blockStates.forEachIndexed { index, block ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Block ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { blockStates.removeAt(index) },
                                    enabled = blockStates.size > 1
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove block")
                                }
                            }

                            OutlinedTextField(
                                value = block.name,
                                onValueChange = { block.name = it },
                                label = { Text("Block Name (e.g. Snatch Complex)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Dropdown(
                                label = "Block Type",
                                options = listOf(
                                    BlockKind.WEIGHTLIFTING,
                                    BlockKind.HYPERTROPHY,
                                    BlockKind.ACCESSORY,
                                    BlockKind.METABOLIC,
                                    BlockKind.CARDIO,
                                    BlockKind.WARMUP,
                                    BlockKind.OTHER
                                ),
                                selected = block.kind,
                                labelOf = { it.label },
                                onSelect = { block.kind = it }
                            )

                            OutlinedTextField(
                                value = block.format,
                                onValueChange = { block.format = it },
                                label = { Text("Execution Format / Structure") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                WORKOUT_FORMATS.forEach { fmt ->
                                    FilterChip(
                                        selected = block.format == fmt,
                                        onClick = { block.format = fmt },
                                        label = { Text(fmt, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = block.setsCount,
                                onValueChange = { block.setsCount = it.filter { c -> c.isDigit() } },
                                label = { Text("Number of Sets / Rounds") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Exercises in this Block:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                exercises.forEach { ex ->
                                    val isSelected = block.selectedExerciseIds.contains(ex.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            block.selectedExerciseIds = if (isSelected) {
                                                block.selectedExerciseIds - ex.id
                                            } else {
                                                block.selectedExerciseIds + ex.id
                                            }
                                        },
                                        label = { Text(ex.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        blockStates.add(
                            RoutineBlockFormState(
                                name = "Block ${blockStates.size + 1}",
                                kind = BlockKind.WEIGHTLIFTING
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Block to Routine")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val routine = (original?.routine ?: Routine(name = "")).copy(
                        name = name.trim(),
                        description = description.trim(),
                        defaultFormat = blockStates.firstOrNull()?.format?.trim() ?: ""
                    )

                    val blocks = blockStates.mapIndexed { idx, bs ->
                        RoutineBlock(
                            id = 0,
                            routineId = routine.id,
                            position = idx,
                            name = bs.name.trim().ifBlank { bs.kind.label },
                            kind = bs.kind,
                            format = bs.format.trim(),
                            setsCount = bs.setsCount.toIntOrNull() ?: 1,
                            exerciseIdsCsv = bs.selectedExerciseIds.joinToString(","),
                            notes = bs.notes.trim()
                        )
                    }

                    onSave(routine, blocks)
                }
            ) { Text("Save Routine") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

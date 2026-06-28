package com.fractanomics.crosstraining.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AppViewModel, outerPadding: PaddingValues) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var showExerciseEditor by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }

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
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Routines") })
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
                if (routines.isEmpty()) {
                    EmptyState("No routines/complexes yet. Tap + to add one.")
                } else {
                    ScreenList {
                        items(routines, key = { it.id }) { routine ->
                            RoutineCard(
                                routine = routine,
                                exercises = exercises,
                                onEdit = { editingRoutine = routine; showRoutineEditor = true },
                                onDelete = { viewModel.deleteRoutine(routine) }
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
            onSave = { routine -> viewModel.saveRoutine(routine); showRoutineEditor = false }
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

@Composable
private fun RoutineCard(
    routine: Routine,
    exercises: List<Exercise>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val target = exercises.firstOrNull { it.id == routine.mainExerciseId }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (target != null) Text("Improves: ${target.name}", style = MaterialTheme.typography.bodySmall)
            if (routine.description.isNotBlank()) Text(routine.description, style = MaterialTheme.typography.bodyMedium)
            if (routine.defaultFormat.isNotBlank()) Text("Format: ${routine.defaultFormat}", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun RoutineEditorDialog(
    original: Routine?,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var description by remember { mutableStateOf(original?.description ?: "") }
    var format by remember { mutableStateOf(original?.defaultFormat ?: "") }
    var target by remember {
        mutableStateOf(exercises.firstOrNull { it.id == original?.mainExerciseId })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New routine / complex" else "Edit routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Clean + Hang Clean + Push Jerk)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Dropdown(
                    label = "Improves (main exercise)",
                    options = exercises,
                    selected = target,
                    labelOf = { it.name },
                    onSelect = { target = it }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / movement breakdown") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = format,
                    onValueChange = { format = it },
                    label = { Text("Default format (e.g. E3MOM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (original ?: Routine(name = "")).copy(
                            name = name.trim(),
                            mainExerciseId = target?.id,
                            description = description.trim(),
                            defaultFormat = format.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

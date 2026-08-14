package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.DateField
import com.fractanomics.crosstraining.ui.components.Dropdown
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.trimmed
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyclesScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val allGoals by viewModel.cycleGoals.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Cycle?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("Training Cycles") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTimer) {
                        Icon(Icons.Filled.Timer, contentDescription = "Quick Timer")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Filled.Add, contentDescription = "New cycle") }
        }
    ) { pad ->
        if (cycles.isEmpty()) {
            EmptyState(
                "No training cycles yet.\nCreate one to group your sessions into a block.",
                Modifier.padding(pad)
            )
        } else {
            ScreenList(modifier = Modifier.padding(pad)) {
                items(cycles, key = { it.id }) { cycle ->
                    val cycleGoals = allGoals.filter { it.cycleId == cycle.id }
                    CycleCard(
                        cycle = cycle,
                        goals = cycleGoals,
                        exercises = exercises,
                        onActivate = { viewModel.activateCycle(cycle.id) },
                        onEdit = { editing = cycle; showEditor = true },
                        onDelete = { viewModel.deleteCycle(cycle) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        val currentGoals = remember(editing) {
            allGoals.filter { it.cycleId == editing?.id }
        }
        CycleEditorDialog(
            original = editing,
            existingGoals = currentGoals,
            exercises = exercises,
            onDismiss = { showEditor = false },
            onSave = { cycle, goals, makeActive ->
                viewModel.saveCycleWithGoals(cycle, goals, makeActive)
                showEditor = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleCard(
    cycle: Cycle,
    goals: List<CycleGoal>,
    exercises: List<Exercise>,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cycle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (cycle.isActive) {
                    AssistChip(onClick = {}, label = { Text("Active") })
                }
            }
            val range = buildString {
                append(cycle.startDate.formatLong())
                append("  →  ")
                append(cycle.endDate?.formatLong() ?: "open (extendable)")
            }
            Text(range, style = MaterialTheme.typography.bodyMedium)
            if (cycle.goal.isNotBlank()) {
                Text("Cycle focus: ${cycle.goal}", style = MaterialTheme.typography.bodySmall)
            }

            if (goals.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text("Basic Movement Goals:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    goals.forEach { g ->
                        val ex = exercises.firstOrNull { it.id == g.exerciseId }
                        val unit = ex?.unit ?: "kg"
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${ex?.name ?: "Lift"} (${g.targetReps}RM Goal): ${g.targetWeight.trimmed()} $unit",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!cycle.isActive) {
                    TextButton(onClick = onActivate) { Text("Set active") }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private data class GoalDraftState(
    val exercise: Exercise?,
    val reps: String,
    val targetWeight: String
)

@Composable
private fun CycleEditorDialog(
    original: Cycle?,
    existingGoals: List<CycleGoal>,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSave: (Cycle, List<CycleGoal>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var goal by remember { mutableStateOf(original?.goal ?: "") }
    var startDate by remember { mutableStateOf(original?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(original?.endDate) }
    var makeActive by remember { mutableStateOf(original?.isActive ?: (original == null)) }

    val goalDrafts = remember(existingGoals, exercises) {
        mutableStateListOf<GoalDraftState>().apply {
            existingGoals.forEach { g ->
                val ex = exercises.firstOrNull { it.id == g.exerciseId }
                add(
                    GoalDraftState(
                        exercise = ex,
                        reps = g.targetReps.toString(),
                        targetWeight = if (g.targetWeight > 0.0) g.targetWeight.trimmed() else ""
                    )
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New cycle" else "Edit cycle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. 8-Wk Peak & Snatch Cycle)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DateField("Start", startDate, { startDate = it }, Modifier.fillMaxWidth())
                DateField("End", endDate, { endDate = it }, Modifier.fillMaxWidth())
                if (endDate != null) {
                    TextButton(onClick = { endDate = null }) { Text("Clear end date (keep open)") }
                }
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Cycle Focus / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeActive, onCheckedChange = { makeActive = it })
                    Text("Make this the active cycle")
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Basic Movement Goals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        goalDrafts.add(GoalDraftState(exercises.firstOrNull(), "1", ""))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add goal")
                        Text("Add Movement")
                    }
                }

                if (goalDrafts.isEmpty()) {
                    Text("No target movement goals added yet.", style = MaterialTheme.typography.bodySmall)
                }

                goalDrafts.forEachIndexed { index, draft ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Goal ${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { goalDrafts.removeAt(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove goal")
                                }
                            }

                            Dropdown(
                                label = "Movement (Basic)",
                                options = exercises,
                                selected = draft.exercise,
                                labelOf = { it.name },
                                onSelect = { selectedEx ->
                                    goalDrafts[index] = draft.copy(exercise = selectedEx)
                                }
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = draft.reps,
                                    onValueChange = { newReps ->
                                        goalDrafts[index] = draft.copy(reps = newReps)
                                    },
                                    label = { Text("Reps (RM)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = draft.targetWeight,
                                    onValueChange = { newTarget ->
                                        goalDrafts[index] = draft.copy(targetWeight = newTarget)
                                    },
                                    label = { Text("Target Goal (${draft.exercise?.unit ?: "kg"})") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val finalCycle = (original ?: Cycle(name = "", startDate = startDate)).copy(
                        name = name.trim(),
                        startDate = startDate,
                        endDate = endDate,
                        goal = goal.trim()
                    )
                    val finalGoals = goalDrafts.mapNotNull { d ->
                        val ex = d.exercise ?: return@mapNotNull null
                        val reps = d.reps.toIntOrNull() ?: 1
                        val targetVal = d.targetWeight.toDoubleOrNull() ?: 0.0
                        if (targetVal <= 0.0) return@mapNotNull null
                        CycleGoal(
                            cycleId = finalCycle.id,
                            exerciseId = ex.id,
                            targetReps = reps,
                            startWeight = 0.0,
                            targetWeight = targetVal
                        )
                    }
                    onSave(finalCycle, finalGoals, makeActive)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

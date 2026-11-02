package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.DateField
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.trimmed
import kotlinx.coroutines.launch
import java.time.DayOfWeek
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
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Cycle?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Training Cycles")
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (userRole.isCoach) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                if (userRole.isCoach) "COACH" else "ATHLETE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
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
            if (userRole.isCoach) {
                FloatingActionButton(onClick = {
                    editing = null
                    showEditor = true
                }) { Icon(Icons.Filled.Add, contentDescription = "New cycle") }
            }
        }
    ) { pad ->
        if (cycles.isEmpty()) {
            EmptyState(
                if (userRole.isCoach) "No training cycles yet.\nTap '+' below to program your first cycle."
                else "No training cycles yet.\nSwitch to Coach Mode to create training blocks and periodization goals.",
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
                        userRole = userRole,
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
            },
            onFetchBestRm = { exerciseId, reps -> viewModel.getBestRepMaxWeight(exerciseId, reps) },
            onFetchLatestWeight = { date, minDate -> viewModel.getLatestWeightOnOrBefore(date, minDate) },
            onFetchBestMetricValue = { exerciseId, metric, reps ->
                viewModel.getBestMetricValueForExercise(exerciseId, metric, reps)
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
    userRole: com.fractanomics.crosstraining.data.model.UserRole,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        cycle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (cycle.isActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Active Cycle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            val range = buildString {
                append(cycle.startDate.formatLong())
                append("  →  ")
                append(cycle.endDate?.formatLong() ?: "open (extendable)")
            }
            Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (cycle.type == CycleType.FAT_LOSS_BODYBUILDING) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "Fat Loss & Bodybuilding",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (cycle.fastDaysOfWeek != 0) {
                        val fastDaysStr = FatLossAnalytics.maskToDayOfWeekSet(cycle.fastDaysOfWeek)
                            .sortedBy { it.value }
                            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
                        Text(
                            "Fast: $fastDaysStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (cycle.restDaysOfWeek != 0) {
                        val restDaysStr = FatLossAnalytics.maskToDayOfWeekSet(cycle.restDaysOfWeek)
                            .sortedBy { it.value }
                            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
                        Text(
                            "Rest: $restDaysStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (cycle.startingWeightKg != null || cycle.targetWeightKg != null) {
                    val weightSummary = buildString {
                        append("Weight: ")
                        if (cycle.startingWeightKg != null) append("${cycle.startingWeightKg.trimmed()} kg") else append("—")
                        append(" → ")
                        if (cycle.targetWeightKg != null) append("${cycle.targetWeightKg.trimmed()} kg") else append("—")
                        if (cycle.isBaselineAutoDerived) append(" (Auto)")
                    }
                    Text(
                        weightSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (cycle.goal.isNotBlank()) {
                Text(
                    "Focus: ${cycle.goal}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (goals.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    "Target Goals (${goals.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    goals.forEach { g ->
                        val ex = exercises.firstOrNull { it.id == g.exerciseId }
                        val unit = ex?.unit ?: "kg"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            val goalLabel = g.formatSummaryChip(ex?.name ?: "Lift", unit)
                            Text(
                                goalLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!cycle.isActive) {
                    TextButton(onClick = onActivate) { Text("Set Active") }
                }
                if (userRole.isCoach) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

object FatLossValidation {
    /**
     * Rejects non-positive or negative input, returning an empty string.
     * Otherwise returns the input.
     */
    fun sanitizeInlineWeightInput(input: String): String {
        if (input.startsWith("-")) return ""
        val parsed = input.replace(',', '.').toDoubleOrNull()
        if (parsed != null && parsed <= 0.0) return ""
        return input
    }

    /**
     * Checks if target weight is greater than or equal to start weight (both positive).
     */
    fun isTargetWeightInvalid(startWeight: Double?, targetWeight: Double?): Boolean {
        if (startWeight == null || targetWeight == null) return false
        return targetWeight >= startWeight
    }
}

data class GoalDraftState(
    val id: Long = 0,
    val exercise: Exercise? = null,
    val reps: String = "1",
    val startWeight: String = "",
    val targetWeight: String = "",
    val selectedModifier: Double? = null,
    val isManuallyEdited: Boolean = false,
    val goalMetric: MetricType = CycleGoal.defaultGoalMetricFor(exercise),
    val timeWindowMinutes: Int? = null,
    val notes: String = ""
) {
    val hasStartWeight: Boolean
        get() = startWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true

    val unitLabel: String
        get() = CycleGoal.unitLabelFor(goalMetric, exercise?.unit)

    val scaleOptions: List<Pair<Double, String>>
        get() = when (goalMetric) {
            MetricType.WEIGHT -> listOf(
                2.5 to "+2.5%",
                5.0 to "+5%",
                10.0 to "+10%",
                0.0 to "0%"
            )
            MetricType.CALORIES -> listOf(
                5.0 to "+5 cal",
                10.0 to "+10 cal",
                20.0 to "+20 cal",
                0.0 to "0 cal"
            )
            MetricType.REPS -> listOf(
                2.0 to "+2 reps",
                5.0 to "+5 reps",
                10.0 to "+10 reps",
                0.0 to "0 reps"
            )
            else -> emptyList()
        }

    fun onExerciseChanged(newExercise: Exercise?, bestWeight: Double?): GoalDraftState {
        val inferredMetric = CycleGoal.defaultGoalMetricFor(newExercise)
        val preservedWindow = if (inferredMetric == MetricType.WEIGHT) null else timeWindowMinutes
        return if (bestWeight != null && bestWeight > 0.0) {
            val weightStr = bestWeight.trimmed().replace(',', '.')
            copy(
                exercise = newExercise,
                goalMetric = inferredMetric,
                timeWindowMinutes = preservedWindow,
                startWeight = weightStr,
                targetWeight = weightStr,
                selectedModifier = 0.0,
                isManuallyEdited = false
            )
        } else {
            copy(
                exercise = newExercise,
                goalMetric = inferredMetric,
                timeWindowMinutes = preservedWindow,
                startWeight = "",
                targetWeight = "",
                selectedModifier = null,
                isManuallyEdited = false
            )
        }
    }

    fun onGoalMetricChanged(newMetric: MetricType, bestValue: Double? = null): GoalDraftState {
        // Re-selecting the active metric is a no-op so manually entered Start/Target
        // values and the selected Scale chip are not wiped by an accidental tap.
        if (newMetric == goalMetric) return this
        val normalizedReps = if ((reps.toIntOrNull() ?: 0) <= 0) "1" else reps
        val window = if (newMetric == MetricType.WEIGHT) null else timeWindowMinutes
        return if (bestValue != null && bestValue > 0.0) {
            val valStr = bestValue.trimmed().replace(',', '.')
            copy(
                goalMetric = newMetric,
                reps = normalizedReps,
                timeWindowMinutes = window,
                startWeight = valStr,
                targetWeight = valStr,
                selectedModifier = 0.0,
                isManuallyEdited = false
            )
        } else {
            copy(
                goalMetric = newMetric,
                reps = normalizedReps,
                timeWindowMinutes = window,
                startWeight = "",
                targetWeight = "",
                selectedModifier = null,
                isManuallyEdited = false
            )
        }
    }

    fun nextSteppedReps(delta: Int): Int {
        val current = (reps.toIntOrNull() ?: 1).coerceAtLeast(1)
        return (current + delta).coerceAtLeast(1)
    }

    fun stepReps(delta: Int, bestWeight: Double? = null): GoalDraftState {
        val current = (reps.toIntOrNull() ?: 1).coerceAtLeast(1)
        val next = nextSteppedReps(delta)
        if (next == current && bestWeight == null) {
            return copy(reps = next.toString())
        }
        return onRepsChanged(next.toString(), bestWeight)
    }

    fun onTimeWindowChanged(minutes: Int?): GoalDraftState {
        return copy(timeWindowMinutes = minutes)
    }

    fun onRepsChanged(newReps: String, bestWeight: Double?): GoalDraftState {
        val parsedReps = newReps.toIntOrNull()
        return if (parsedReps != null && parsedReps > 0 && bestWeight != null && bestWeight > 0.0) {
            val weightStr = bestWeight.trimmed().replace(',', '.')
            copy(
                reps = newReps,
                startWeight = weightStr,
                targetWeight = weightStr,
                selectedModifier = 0.0,
                isManuallyEdited = false
            )
        } else {
            copy(
                reps = newReps,
                startWeight = "",
                targetWeight = "",
                selectedModifier = null,
                isManuallyEdited = false
            )
        }
    }

    fun onStartWeightChanged(newStart: String): GoalDraftState {
        val parsed = newStart.replace(',', '.').toDoubleOrNull()
        val newTarget = if (selectedModifier != null && parsed != null && parsed > 0.0) {
            computeScaledTarget(parsed, selectedModifier, goalMetric).trimmed().replace(',', '.')
        } else {
            targetWeight
        }
        return copy(
            startWeight = newStart,
            targetWeight = newTarget,
            isManuallyEdited = true
        )
    }

    fun onTargetWeightChanged(newTarget: String): GoalDraftState {
        return copy(
            targetWeight = newTarget,
            selectedModifier = null,
            isManuallyEdited = true
        )
    }

    fun applyModifier(percentage: Double): GoalDraftState {
        val base = startWeight.replace(',', '.').toDoubleOrNull() ?: return this
        if (base <= 0.0) return this
        val scaled = computeScaledTarget(base, percentage, goalMetric)
        return copy(
            targetWeight = scaled.trimmed().replace(',', '.'),
            selectedModifier = percentage
        )
    }

    fun resetModifier(): GoalDraftState {
        val base = startWeight.replace(',', '.').toDoubleOrNull() ?: return this
        if (base <= 0.0) return this
        val scaled = computeScaledTarget(base, 0.0, goalMetric)
        return copy(
            targetWeight = scaled.trimmed().replace(',', '.'),
            selectedModifier = 0.0
        )
    }

    fun toCycleGoal(cycleId: Long): CycleGoal? {
        val ex = exercise ?: return null
        val targetReps = (reps.toIntOrNull() ?: 1).coerceAtLeast(1)
        val startVal = startWeight.replace(',', '.').toDoubleOrNull() ?: 0.0
        val targetVal = targetWeight.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (targetVal <= 0.0) return null
        return CycleGoal(
            id = id,
            cycleId = cycleId,
            exerciseId = ex.id,
            targetReps = targetReps,
            startWeight = startVal,
            targetWeight = targetVal,
            notes = notes,
            goalMetric = goalMetric,
            timeWindowMinutes = if (goalMetric == MetricType.WEIGHT) null else timeWindowMinutes
        )
    }

    companion object {
        fun computeScaledTarget(base: Double, modifier: Double, goalMetric: MetricType): Double {
            return when (goalMetric) {
                MetricType.WEIGHT -> FatLossAnalytics.scaleRepMax(base, modifier)
                else -> if (modifier == 0.0) base else base + modifier
            }
        }

        fun fromCycleGoal(goal: CycleGoal, exercise: Exercise?): GoalDraftState {
            val metric = goal.goalMetric
            val repsStr = if (metric == MetricType.WEIGHT) {
                goal.targetReps.coerceAtLeast(1).toString()
            } else {
                "1"
            }
            return GoalDraftState(
                id = goal.id,
                exercise = exercise,
                reps = repsStr,
                startWeight = if (goal.startWeight > 0.0) goal.startWeight.trimmed().replace(',', '.') else "",
                targetWeight = if (goal.targetWeight > 0.0) goal.targetWeight.trimmed().replace(',', '.') else "",
                selectedModifier = null,
                isManuallyEdited = true,
                goalMetric = metric,
                timeWindowMinutes = goal.timeWindowMinutes,
                notes = goal.cleanNotes
            )
        }
    }
}

internal fun filterMovementExercises(
    exercises: List<Exercise>,
    searchQuery: String
): List<Exercise> {
    if (searchQuery.isBlank()) return exercises
    val q = searchQuery.trim()
    return exercises.filter { it.name.contains(q, ignoreCase = true) }
}

internal data class MovementSelectorUiState(
    val expanded: Boolean = false,
    val searchQuery: String = ""
) {
    val hasSearchQuery: Boolean
        get() = searchQuery.isNotEmpty()

    fun toggleExpanded(): MovementSelectorUiState =
        if (expanded) {
            copy(expanded = false, searchQuery = "")
        } else {
            copy(expanded = true, searchQuery = "")
        }

    fun open(): MovementSelectorUiState = copy(expanded = true)

    fun dismiss(): MovementSelectorUiState = copy(expanded = false, searchQuery = "")

    fun onSearchQueryChanged(query: String): MovementSelectorUiState =
        copy(expanded = true, searchQuery = query)

    fun clearSearchQuery(): MovementSelectorUiState = copy(searchQuery = "")

    fun onExerciseSelected(): MovementSelectorUiState =
        copy(expanded = false, searchQuery = "")

    fun filterExercises(exercises: List<Exercise>): List<Exercise> =
        filterMovementExercises(exercises, searchQuery)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementGoalSelector(
    exercises: List<Exercise>,
    selected: Exercise?,
    onSelect: (Exercise) -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(MovementSelectorUiState()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredExercises = remember(exercises, state.searchQuery) {
        state.filterExercises(exercises)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Movement (Basic)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = state.toggleExpanded()
                    state = next
                    if (!next.expanded) {
                        keyboardController?.hide()
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search movement",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = selected?.name ?: "Select movement…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (state.expanded) "Change ▴" else "Change ▾",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.expanded) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { state = state.onSearchQueryChanged(it) },
                        placeholder = { Text("Search movement…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                filteredExercises.firstOrNull()?.let { option ->
                                    onSelect(option)
                                    state = state.onExerciseSelected()
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (state.hasSearchQuery) {
                            {
                                IconButton(
                                    onClick = {
                                        state = state.clearSearchQuery()
                                        runCatching { focusRequester.requestFocus() }
                                        keyboardController?.show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .focusRequester(focusRequester)
                    )

                    LaunchedEffect(Unit) {
                        runCatching { focusRequester.requestFocus() }
                        keyboardController?.show()
                    }

                    if (filteredExercises.isEmpty()) {
                        Text(
                            text = "No matching movements found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            filteredExercises.forEach { option ->
                                val isSelected = option.id == selected?.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    onClick = {
                                        onSelect(option)
                                        state = state.onExerciseSelected()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CycleEditorDialog(
    original: Cycle?,
    existingGoals: List<CycleGoal>,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSave: (Cycle, List<CycleGoal>, Boolean) -> Unit,
    onFetchBestRm: (suspend (exerciseId: Long, reps: Int) -> Double?)? = null,
    onFetchLatestWeight: (suspend (date: LocalDate, minDate: LocalDate) -> WeightEntry?)? = null,
    onFetchBestMetricValue: (suspend (exerciseId: Long, metric: MetricType, reps: Int) -> Double?)? = null
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var goal by remember { mutableStateOf(original?.goal ?: "") }
    var startDate by remember { mutableStateOf(original?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(original?.endDate) }
    var makeActive by remember { mutableStateOf(original?.isActive ?: (original == null)) }
    var cycleType by remember { mutableStateOf(original?.type ?: CycleType.STRENGTH_WEIGHTLIFTING) }
    var fastDaysOfWeek by remember { mutableStateOf(original?.fastDaysOfWeek ?: 0) }
    var restDaysOfWeek by remember { mutableStateOf(original?.restDaysOfWeek ?: 0) }

    var startingWeightStr by remember {
        mutableStateOf(original?.startingWeightKg?.trimmed()?.replace(',', '.') ?: "")
    }
    var targetWeightStr by remember {
        mutableStateOf(original?.targetWeightKg?.trimmed()?.replace(',', '.') ?: "")
    }
    var isBaselineAutoDerived by remember {
        mutableStateOf(original?.isBaselineAutoDerived ?: false)
    }
    var isStartManuallyEdited by remember {
        mutableStateOf(original?.startingWeightKg != null)
    }

    val coroutineScope = rememberCoroutineScope()

    suspend fun resolveBestValue(exerciseId: Long, metric: MetricType, reps: Int): Double? {
        return when {
            onFetchBestMetricValue != null -> onFetchBestMetricValue(exerciseId, metric, reps)
            metric == MetricType.WEIGHT && onFetchBestRm != null -> onFetchBestRm(exerciseId, reps)
            else -> null
        }
    }

    LaunchedEffect(startDate, cycleType) {
        if (cycleType == CycleType.FAT_LOSS_BODYBUILDING && !isStartManuallyEdited && (original == null || original.startingWeightKg == null)) {
            if (onFetchLatestWeight != null && !startDate.isAfter(LocalDate.now())) {
                val minDate = startDate.minusDays(7)
                val entry = onFetchLatestWeight(startDate, minDate)
                if (entry != null) {
                    startingWeightStr = entry.weightKg.trimmed().replace(',', '.')
                    isBaselineAutoDerived = true
                } else {
                    startingWeightStr = ""
                    isBaselineAutoDerived = false
                }
            }
        }
    }

    val goalDrafts = remember(existingGoals, exercises) {
        mutableStateListOf<GoalDraftState>().apply {
            existingGoals.forEach { g ->
                val ex = exercises.firstOrNull { it.id == g.exerciseId }
                add(GoalDraftState.fromCycleGoal(g, ex))
            }
        }
    }

    val startVal = startingWeightStr.replace(',', '.').toDoubleOrNull()
    val targetVal = targetWeightStr.replace(',', '.').toDoubleOrNull()
    val isTargetInvalid = cycleType == CycleType.FAT_LOSS_BODYBUILDING &&
        FatLossValidation.isTargetWeightInvalid(startVal, targetVal)

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

                // Cycle Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cycle Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = cycleType == CycleType.STRENGTH_WEIGHTLIFTING,
                            onClick = { cycleType = CycleType.STRENGTH_WEIGHTLIFTING },
                            label = { Text("Strength & Lifts") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = cycleType == CycleType.FAT_LOSS_BODYBUILDING,
                            onClick = { cycleType = CycleType.FAT_LOSS_BODYBUILDING },
                            label = { Text("Fat Loss & BB") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // If FAT_LOSS_BODYBUILDING: Render Body Weight Goals and Fast/Rest Day Selectors
                if (cycleType == CycleType.FAT_LOSS_BODYBUILDING) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
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
                                Text(
                                    "Body Weight Goals (Fat Loss)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                if (isBaselineAutoDerived && startVal != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            "Auto-Derived Baseline",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = startingWeightStr,
                                    onValueChange = { input ->
                                        val sanitized = FatLossValidation.sanitizeInlineWeightInput(input)
                                        startingWeightStr = sanitized
                                        isStartManuallyEdited = true
                                        isBaselineAutoDerived = false
                                    },
                                    label = { Text("Starting (kg)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = targetWeightStr,
                                    onValueChange = { input ->
                                        val sanitized = FatLossValidation.sanitizeInlineWeightInput(input)
                                        targetWeightStr = sanitized
                                    },
                                    label = { Text("Target (kg)") },
                                    singleLine = true,
                                    isError = isTargetInvalid,
                                    supportingText = if (isTargetInvalid) {
                                        { Text("Target weight must be less than starting weight (${startVal?.trimmed()} kg)") }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    val daysOfWeek = listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Scheduled Fast Days",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            daysOfWeek.forEach { day ->
                                val isSelected = FatLossAnalytics.isDayInMask(day, fastDaysOfWeek)
                                val label = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val bit = FatLossAnalytics.dayOfWeekToBit(day)
                                        fastDaysOfWeek = fastDaysOfWeek xor bit
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Scheduled Rest Days",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            daysOfWeek.forEach { day ->
                                val isSelected = FatLossAnalytics.isDayInMask(day, restDaysOfWeek)
                                val label = day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val bit = FatLossAnalytics.dayOfWeekToBit(day)
                                        restDaysOfWeek = restDaysOfWeek xor bit
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

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
                        val defaultEx = exercises.firstOrNull()
                        val defaultMetric = CycleGoal.defaultGoalMetricFor(defaultEx)
                        val newIndex = goalDrafts.size
                        val newDraft = GoalDraftState(
                            exercise = defaultEx,
                            reps = "1",
                            goalMetric = defaultMetric
                        )
                        goalDrafts.add(newDraft)
                        if (defaultEx != null && (onFetchBestMetricValue != null || onFetchBestRm != null)) {
                            coroutineScope.launch {
                                val best = resolveBestValue(defaultEx.id, defaultMetric, 1)
                                val cur = goalDrafts.getOrNull(newIndex) ?: return@launch
                                goalDrafts[newIndex] = cur.onExerciseChanged(defaultEx, best)
                            }
                        }
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

                            MovementGoalSelector(
                                exercises = exercises,
                                selected = draft.exercise,
                                onSelect = { selectedEx ->
                                    val inferredMetric = CycleGoal.defaultGoalMetricFor(selectedEx)
                                    val repsInt = (draft.reps.toIntOrNull() ?: 1).coerceAtLeast(1)
                                    if (onFetchBestMetricValue != null || onFetchBestRm != null) {
                                        coroutineScope.launch {
                                            val best = resolveBestValue(selectedEx.id, inferredMetric, repsInt)
                                            val cur = goalDrafts.getOrNull(index) ?: return@launch
                                            goalDrafts[index] = cur.onExerciseChanged(selectedEx, best)
                                        }
                                    } else {
                                        goalDrafts[index] = draft.onExerciseChanged(selectedEx, null)
                                    }
                                }
                            )

                            // Goal Metric Segmented Pill Row
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Goal Metric:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val weightUnit = CycleGoal.unitLabelFor(MetricType.WEIGHT, draft.exercise?.unit)
                                val metricPills = listOf(
                                    MetricType.WEIGHT to "Weight ($weightUnit)",
                                    MetricType.CALORIES to "Calories (cal)",
                                    MetricType.REPS to "Reps"
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    metricPills.forEach { (metric, pillLabel) ->
                                        FilterChip(
                                            selected = draft.goalMetric == metric,
                                            onClick = {
                                                val ex = draft.exercise
                                                val repsInt = (draft.reps.toIntOrNull() ?: 1).coerceAtLeast(1)
                                                // Re-tapping the active metric keeps manual Start/Target edits intact.
                                                if (draft.goalMetric != metric) {
                                                    if (ex != null && (onFetchBestMetricValue != null || onFetchBestRm != null)) {
                                                        coroutineScope.launch {
                                                            val best = resolveBestValue(ex.id, metric, repsInt)
                                                            val cur = goalDrafts.getOrNull(index) ?: return@launch
                                                            goalDrafts[index] = cur.onGoalMetricChanged(metric, best)
                                                        }
                                                    } else {
                                                        goalDrafts[index] = draft.onGoalMetricChanged(metric, null)
                                                    }
                                                }
                                            },
                                            label = { Text(pillLabel, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            // Contextual Row: Reps (RM) stepper + presets vs Time Window pills
                            if (draft.goalMetric == MetricType.WEIGHT) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Reps (RM)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val currentRepsInt = (draft.reps.toIntOrNull() ?: 1).coerceAtLeast(1)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedIconButton(
                                            onClick = {
                                                val nextReps = draft.nextSteppedReps(-1)
                                                val ex = draft.exercise
                                                if (ex != null && (onFetchBestMetricValue != null || onFetchBestRm != null)) {
                                                    coroutineScope.launch {
                                                        val best = resolveBestValue(ex.id, MetricType.WEIGHT, nextReps)
                                                        val cur = goalDrafts.getOrNull(index) ?: return@launch
                                                        // Apply the exact reps the lookup was made for so rapid taps
                                                        // can't pair an RM count with another RM's best weight.
                                                        goalDrafts[index] = cur.onRepsChanged(nextReps.toString(), best)
                                                    }
                                                } else {
                                                    goalDrafts[index] = draft.stepReps(-1, null)
                                                }
                                            },
                                            enabled = currentRepsInt > 1,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Remove,
                                                contentDescription = "Decrease RM",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = "$currentRepsInt RM",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                        OutlinedIconButton(
                                            onClick = {
                                                val nextReps = draft.nextSteppedReps(1)
                                                val ex = draft.exercise
                                                if (ex != null && (onFetchBestMetricValue != null || onFetchBestRm != null)) {
                                                    coroutineScope.launch {
                                                        val best = resolveBestValue(ex.id, MetricType.WEIGHT, nextReps)
                                                        val cur = goalDrafts.getOrNull(index) ?: return@launch
                                                        // Apply the exact reps the lookup was made for so rapid taps
                                                        // can't pair an RM count with another RM's best weight.
                                                        goalDrafts[index] = cur.onRepsChanged(nextReps.toString(), best)
                                                    }
                                                } else {
                                                    goalDrafts[index] = draft.stepReps(1, null)
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Increase RM",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CycleGoal.RM_PRESETS.forEach { presetReps ->
                                            val presetStr = presetReps.toString()
                                            FilterChip(
                                                selected = draft.reps == presetStr,
                                                onClick = {
                                                    val ex = draft.exercise
                                                    if (ex != null && (onFetchBestMetricValue != null || onFetchBestRm != null)) {
                                                        coroutineScope.launch {
                                                            val best = resolveBestValue(ex.id, MetricType.WEIGHT, presetReps)
                                                            val cur = goalDrafts.getOrNull(index) ?: return@launch
                                                            goalDrafts[index] = cur.onRepsChanged(presetStr, best)
                                                        }
                                                    } else {
                                                        goalDrafts[index] = draft.onRepsChanged(presetStr, null)
                                                    }
                                                },
                                                label = { Text(presetStr, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Time Window",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CycleGoal.TIME_WINDOW_OPTIONS.forEach { windowMin ->
                                            val label = CycleGoal.formatTimeWindowLabel(windowMin)
                                            FilterChip(
                                                selected = draft.timeWindowMinutes == windowMin,
                                                onClick = {
                                                    goalDrafts[index] = draft.onTimeWindowChanged(windowMin)
                                                },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Start & Target Fields Row
                            val unit = draft.unitLabel
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = draft.startWeight,
                                    onValueChange = { newStart ->
                                        goalDrafts[index] = draft.onStartWeightChanged(newStart)
                                    },
                                    label = { Text("Start ($unit)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "+ →",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = draft.targetWeight,
                                    onValueChange = { newTarget ->
                                        goalDrafts[index] = draft.onTargetWeightChanged(newTarget)
                                    },
                                    label = { Text("Target ($unit)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Adaptive Scale Modifier Chips Row
                            val hasStart = draft.hasStartWeight
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Scale:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasStart) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp)
                                )
                                draft.scaleOptions.forEach { (modVal, label) ->
                                    FilterChip(
                                        selected = draft.selectedModifier == modVal,
                                        enabled = hasStart,
                                        onClick = {
                                            goalDrafts[index] = if (modVal == 0.0) draft.resetModifier() else draft.applyModifier(modVal)
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !isTargetInvalid,
                onClick = {
                    val finalStartWeight = if (cycleType == CycleType.FAT_LOSS_BODYBUILDING) {
                        startingWeightStr.replace(',', '.').toDoubleOrNull()
                    } else null

                    val finalTargetWeight = if (cycleType == CycleType.FAT_LOSS_BODYBUILDING) {
                        targetWeightStr.replace(',', '.').toDoubleOrNull()
                    } else null

                    val finalIsAutoDerived = if (cycleType == CycleType.FAT_LOSS_BODYBUILDING) {
                        isBaselineAutoDerived && finalStartWeight != null
                    } else false

                    val finalCycle = (original ?: Cycle(name = "", startDate = startDate)).copy(
                        name = name.trim(),
                        startDate = startDate,
                        endDate = endDate,
                        goal = goal.trim(),
                        type = cycleType,
                        fastDaysOfWeek = fastDaysOfWeek,
                        restDaysOfWeek = restDaysOfWeek,
                        startingWeightKg = finalStartWeight,
                        targetWeightKg = finalTargetWeight,
                        isBaselineAutoDerived = finalIsAutoDerived
                    )
                    val finalGoals = goalDrafts.mapNotNull { it.toCycleGoal(finalCycle.id) }
                    onSave(finalCycle, finalGoals, makeActive)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

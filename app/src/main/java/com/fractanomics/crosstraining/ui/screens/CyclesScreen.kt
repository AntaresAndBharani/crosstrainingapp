package com.fractanomics.crosstraining.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.fractanomics.crosstraining.data.model.WeightSubcycleType
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.DateField
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.trimmed
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "Subcycle: ${cycle.subcycleType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

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

            if (cycle.cleanGoal.isNotBlank()) {
                Text(
                    "Focus: ${cycle.cleanGoal}",
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

    /**
     * Validates optional starting & target body weight against the selected [WeightSubcycleType].
     * - [WeightSubcycleType.WEIGHT_LOSS]: target must be strictly less than start.
     * - [WeightSubcycleType.WEIGHT_GAIN]: target must be strictly greater than start.
     * - [WeightSubcycleType.RECOMPOSITION]: any positive target weight is valid.
     */
    fun isTargetWeightInvalidForSubcycle(
        startWeight: Double?,
        targetWeight: Double?,
        subcycleType: WeightSubcycleType
    ): Boolean {
        if (startWeight == null || targetWeight == null) return false
        return when (subcycleType) {
            WeightSubcycleType.WEIGHT_LOSS -> targetWeight >= startWeight
            WeightSubcycleType.WEIGHT_GAIN -> targetWeight <= startWeight
            WeightSubcycleType.RECOMPOSITION -> false
        }
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
    val notes: String = "",
    val isCollapsed: Boolean = false,
    val showTargetError: Boolean = false,
    val lastValidTargetWeight: String = if (targetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true) targetWeight else ""
) {
    val hasStartWeight: Boolean
        get() = startWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true

    val hasValidTarget: Boolean
        get() = targetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true

    val canRestoreValidTarget: Boolean
        get() = lastValidTargetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true

    val isDiscardableEmptyDraft: Boolean
        get() = id == 0L && !hasValidTarget && !canRestoreValidTarget

    val unitLabel: String
        get() = CycleGoal.unitLabelFor(goalMetric, exercise?.unit)

    val contextBadgeText: String
        get() = when (goalMetric) {
            MetricType.WEIGHT -> "${(reps.toIntOrNull() ?: 1).coerceAtLeast(1)}RM"
            else -> CycleGoal.formatTimeWindowLabel(timeWindowMinutes)
        }

    val targetBadgeText: String
        get() {
            val parsed = targetWeight.replace(',', '.').toDoubleOrNull()
            val valueStr = if (parsed != null && parsed > 0.0) parsed.trimmed() else targetWeight.ifBlank { "0" }
            return "Target: $valueStr $unitLabel"
        }

    fun formatCollapsedSummaryTitle(index: Int): String {
        val movementName = exercise?.name ?: "Select movement…"
        return "Goal ${index + 1}: $movementName"
    }

    fun collapsedAccessibilityDescription(index: Int): String {
        val movementName = exercise?.name ?: "Select movement"
        val parsed = targetWeight.replace(',', '.').toDoubleOrNull()
        val valueStr = if (parsed != null && parsed > 0.0) parsed.trimmed() else targetWeight.ifBlank { "0" }
        return "Goal ${index + 1}: $movementName, $contextBadgeText, Target $valueStr $unitLabel. Collapsed. Double-tap to edit goal."
    }

    val targetStepperOptions: List<Pair<Double, String>>
        get() = when (goalMetric) {
            MetricType.WEIGHT -> {
                val u = unitLabel
                listOf(
                    -2.5 to "-2.5 $u",
                    2.5 to "+2.5 $u",
                    5.0 to "+5 $u"
                )
            }
            MetricType.CALORIES -> listOf(
                -5.0 to "-5 cal",
                5.0 to "+5 cal",
                10.0 to "+10 cal"
            )
            MetricType.REPS -> listOf(
                -1.0 to "-1 rep",
                1.0 to "+1 rep",
                5.0 to "+5 reps"
            )
            else -> emptyList()
        }

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

    fun stepTarget(delta: Double): GoalDraftState {
        val current = targetWeight.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val nextBd = BigDecimal.valueOf(current)
            .add(BigDecimal.valueOf(delta))
            .setScale(2, RoundingMode.HALF_UP)
        val clamped = nextBd.toDouble().coerceAtLeast(0.0)
        val formatted = clamped.trimmed().replace(',', '.')
        return copy(
            targetWeight = formatted,
            selectedModifier = delta,
            isManuallyEdited = true,
            showTargetError = if (clamped > 0.0) false else showTargetError,
            lastValidTargetWeight = if (clamped > 0.0) formatted else lastValidTargetWeight
        )
    }

    fun tryCollapse(): GoalDraftState =
        if (hasValidTarget) {
            copy(isCollapsed = true, showTargetError = false)
        } else {
            copy(isCollapsed = false, showTargetError = true)
        }

    fun expand(): GoalDraftState = copy(isCollapsed = false, showTargetError = false)

    // SPEC-CYCLE-GOAL-04: the Start field is no longer shown, so re-querying a movement,
    // metric, or RM only pre-fills Target from the historical best and clears startWeight.
    // Persisting startWeight = 0.0 lets evaluateCycleGoalProgress derive the baseline from
    // records on/before the cycle start instead of a hidden, uneditable value captured at
    // edit time (which could already include in-cycle PRs when a goal is edited mid-cycle).
    fun onExerciseChanged(newExercise: Exercise?, bestWeight: Double?): GoalDraftState {
        val inferredMetric = CycleGoal.defaultGoalMetricFor(newExercise)
        val preservedWindow = if (inferredMetric == MetricType.WEIGHT) null else timeWindowMinutes
        return if (bestWeight != null && bestWeight > 0.0) {
            val weightStr = bestWeight.trimmed().replace(',', '.')
            copy(
                exercise = newExercise,
                goalMetric = inferredMetric,
                timeWindowMinutes = preservedWindow,
                startWeight = "",
                targetWeight = weightStr,
                selectedModifier = 0.0,
                isManuallyEdited = false,
                showTargetError = false,
                lastValidTargetWeight = weightStr
            )
        } else {
            // A previous movement's target is meaningless (and possibly a different unit)
            // for the new movement, so it must not be restored later by expandGoalDraftAt.
            copy(
                exercise = newExercise,
                goalMetric = inferredMetric,
                timeWindowMinutes = preservedWindow,
                startWeight = "",
                targetWeight = "",
                selectedModifier = null,
                isManuallyEdited = false,
                lastValidTargetWeight = ""
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
                startWeight = "",
                targetWeight = valStr,
                selectedModifier = 0.0,
                isManuallyEdited = false,
                showTargetError = false,
                lastValidTargetWeight = valStr
            )
        } else {
            // The previous metric's target is in a different unit (kg vs cal vs reps),
            // so it must not be restored later by expandGoalDraftAt.
            copy(
                goalMetric = newMetric,
                reps = normalizedReps,
                timeWindowMinutes = window,
                startWeight = "",
                targetWeight = "",
                selectedModifier = null,
                isManuallyEdited = false,
                lastValidTargetWeight = ""
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
                startWeight = "",
                targetWeight = weightStr,
                selectedModifier = 0.0,
                isManuallyEdited = false,
                showTargetError = false,
                lastValidTargetWeight = weightStr
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
        val newTargetValid = newTarget.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true
        return copy(
            startWeight = newStart,
            targetWeight = newTarget,
            isManuallyEdited = true,
            lastValidTargetWeight = if (newTargetValid) newTarget else lastValidTargetWeight
        )
    }

    fun onTargetWeightChanged(newTarget: String): GoalDraftState {
        val parsed = newTarget.replace(',', '.').toDoubleOrNull()
        val isValid = parsed != null && parsed > 0.0
        return copy(
            targetWeight = newTarget,
            selectedModifier = null,
            isManuallyEdited = true,
            showTargetError = if (isValid) false else showTargetError,
            lastValidTargetWeight = if (isValid) newTarget else lastValidTargetWeight
        )
    }

    fun applyModifier(percentage: Double): GoalDraftState {
        val base = startWeight.replace(',', '.').toDoubleOrNull() ?: return this
        if (base <= 0.0) return this
        val scaled = computeScaledTarget(base, percentage, goalMetric)
        val formatted = scaled.trimmed().replace(',', '.')
        return copy(
            targetWeight = formatted,
            selectedModifier = percentage,
            showTargetError = false,
            lastValidTargetWeight = if (scaled > 0.0) formatted else lastValidTargetWeight
        )
    }

    fun resetModifier(): GoalDraftState {
        val base = startWeight.replace(',', '.').toDoubleOrNull() ?: return this
        if (base <= 0.0) return this
        val scaled = computeScaledTarget(base, 0.0, goalMetric)
        val formatted = scaled.trimmed().replace(',', '.')
        return copy(
            targetWeight = formatted,
            selectedModifier = 0.0,
            showTargetError = false,
            lastValidTargetWeight = if (scaled > 0.0) formatted else lastValidTargetWeight
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
            val targetStr = if (goal.targetWeight > 0.0) goal.targetWeight.trimmed().replace(',', '.') else ""
            return GoalDraftState(
                id = goal.id,
                exercise = exercise,
                reps = repsStr,
                startWeight = if (goal.startWeight > 0.0) goal.startWeight.trimmed().replace(',', '.') else "",
                targetWeight = targetStr,
                selectedModifier = null,
                isManuallyEdited = true,
                goalMetric = metric,
                timeWindowMinutes = goal.timeWindowMinutes,
                notes = goal.cleanNotes,
                isCollapsed = goal.targetWeight > 0.0,
                showTargetError = false,
                lastValidTargetWeight = targetStr
            )
        }
    }
}

internal data class CardLayoutBounds(
    val topPx: Int,
    val heightPx: Int
) {
    val bottomPx: Int
        get() = topPx + heightPx
}

/**
 * Pure scroll-offset solver for bringing a newly added, newly expanded, or validation-error
 * card into the visible vertical scroll viewport inside a modal dialog.
 */
internal fun resolveGoalCardScrollOffset(
    cardTopPx: Int,
    cardHeightPx: Int,
    viewportHeightPx: Int,
    currentScrollPx: Int,
    maxScrollPx: Int,
    topPaddingPx: Int = 24
): Int {
    if (viewportHeightPx <= 0 || maxScrollPx <= 0 || cardHeightPx <= 0) {
        return currentScrollPx.coerceIn(0, maxScrollPx.coerceAtLeast(0))
    }
    val cardBottomPx = cardTopPx + cardHeightPx
    val visibleTopPx = currentScrollPx
    val visibleBottomPx = currentScrollPx + viewportHeightPx

    val desiredOffset = when {
        // Card is taller than viewport or clipped above the top edge: align top edge
        cardHeightPx >= viewportHeightPx || cardTopPx < visibleTopPx -> {
            (cardTopPx - topPaddingPx).coerceAtLeast(0)
        }
        // Card extends below the visible bottom edge: scroll down so bottom (or top if needed) is visible
        cardBottomPx > visibleBottomPx -> {
            val alignBottomOffset = cardBottomPx - viewportHeightPx + topPaddingPx
            val maxTopAlignedOffset = (cardTopPx - topPaddingPx).coerceAtLeast(0)
            alignBottomOffset.coerceAtMost(maxTopAlignedOffset)
        }
        // Card is already completely within the visible viewport
        else -> currentScrollPx
    }
    return desiredOffset.coerceIn(0, maxScrollPx)
}

internal fun collapseGoalDraftAt(
    drafts: List<GoalDraftState>,
    index: Int
): List<GoalDraftState> {
    if (index !in drafts.indices) return drafts
    return drafts.mapIndexed { idx, draft ->
        if (idx == index) draft.tryCollapse() else draft
    }
}

internal fun expandGoalDraftAt(
    drafts: List<GoalDraftState>,
    targetIndex: Int
): List<GoalDraftState> {
    if (targetIndex !in drafts.indices) return drafts
    val result = mutableListOf<GoalDraftState>()
    drafts.forEachIndexed { idx, draft ->
        when {
            idx == targetIndex -> {
                result.add(draft.expand())
            }
            !draft.isCollapsed && !draft.hasValidTarget -> {
                when {
                    draft.canRestoreValidTarget -> result.add(
                        draft.copy(
                            targetWeight = draft.lastValidTargetWeight,
                            isCollapsed = true,
                            showTargetError = false
                        )
                    )
                    // Unsaved empty draft that never had a valid target:
                    // omit from result to auto-discard cleanly without blocking expansion.
                    draft.isDiscardableEmptyDraft -> Unit
                    // Persisted goal (id != 0) without a restorable target: never silently
                    // remove it from the list; collapse it so the user can still edit or delete it.
                    else -> result.add(draft.copy(isCollapsed = true, showTargetError = false))
                }
            }
            !draft.isCollapsed -> {
                result.add(draft.copy(isCollapsed = true, showTargetError = false))
            }
            else -> {
                result.add(draft)
            }
        }
    }
    return result
}

internal fun appendMovementGoalDraft(
    drafts: List<GoalDraftState>,
    newDraft: GoalDraftState
): Pair<List<GoalDraftState>, Boolean> {
    val invalidOpenIndex = drafts.indexOfFirst { !it.isCollapsed && !it.hasValidTarget }
    if (invalidOpenIndex != -1) {
        val flagged = drafts.mapIndexed { idx, draft ->
            if (idx == invalidOpenIndex) draft.tryCollapse() else draft
        }
        return flagged to false
    }
    val collapsedExisting = drafts.map { draft ->
        if (!draft.isCollapsed) draft.copy(isCollapsed = true, showTargetError = false) else draft
    }
    val appended = collapsedExisting + newDraft.copy(isCollapsed = false, showTargetError = false)
    return appended to true
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
        copy(expanded = !expanded, searchQuery = "")

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

    // The inline search panel lives inside the CycleEditorDialog window, so without this
    // handler a system back press (after the IME is hidden) would dismiss the entire dialog
    // and discard unsaved cycle edits. Collapse the selector first, matching the previous
    // ExposedDropdownMenu popup's back-press behavior.
    BackHandler(enabled = state.expanded) {
        state = state.dismiss()
        keyboardController?.hide()
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
                .clickable(
                    onClickLabel = if (state.expanded) "Close movement search" else "Change movement",
                    role = Role.Button
                ) {
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

@Composable
private fun CollapsedGoalSummaryCard(
    index: Int,
    draft: GoalDraftState,
    onExpand: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a11yDescription = draft.collapsedAccessibilityDescription(index)
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = a11yDescription
            }
            .clickable(
                onClickLabel = "Edit goal",
                role = Role.Button,
                onClick = onExpand
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = draft.formatCollapsedSummaryTitle(index),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = draft.contextBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = draft.targetBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove goal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onExpand,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand goal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
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
    var goal by remember { mutableStateOf(original?.cleanGoal ?: "") }
    var subcycleType by remember {
        mutableStateOf(
            original?.subcycleType
                ?: Cycle.inferDefaultSubcycleType(CycleType.STRENGTH_WEIGHTLIFTING, null, null)
        )
    }
    // New cycles follow the cycle-type default until the athlete explicitly picks a subcycle regime.
    var isSubcycleManuallySelected by remember { mutableStateOf(original != null) }
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
    val hapticFeedback = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    suspend fun resolveBestValue(exerciseId: Long, metric: MetricType, reps: Int): Double? {
        return when {
            onFetchBestMetricValue != null -> onFetchBestMetricValue(exerciseId, metric, reps)
            metric == MetricType.WEIGHT && onFetchBestRm != null -> onFetchBestRm(exerciseId, reps)
            else -> null
        }
    }

    LaunchedEffect(cycleType) {
        if (!isSubcycleManuallySelected) {
            subcycleType = Cycle.inferDefaultSubcycleType(cycleType, null, null)
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

    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val cardBoundsMap = remember { mutableMapOf<Int, CardLayoutBounds>() }
    var pendingScrollCardIndex by remember { mutableIntStateOf(-1) }
    var scrollTriggerToken by remember { mutableIntStateOf(0) }

    fun requestScrollToGoalCard(cardIndex: Int) {
        if (cardIndex >= 0) {
            pendingScrollCardIndex = cardIndex
            scrollTriggerToken++
        }
    }

    LaunchedEffect(scrollTriggerToken) {
        if (scrollTriggerToken > 0 && pendingScrollCardIndex >= 0) {
            repeat(2) { withFrameNanos { } }
            val bounds = cardBoundsMap[pendingScrollCardIndex]
            if (bounds != null) {
                val targetOffset = resolveGoalCardScrollOffset(
                    cardTopPx = bounds.topPx,
                    cardHeightPx = bounds.heightPx,
                    viewportHeightPx = viewportHeightPx,
                    currentScrollPx = scrollState.value,
                    maxScrollPx = scrollState.maxValue
                )
                if (targetOffset != scrollState.value) {
                    scrollState.animateScrollTo(targetOffset)
                }
            }
        }
    }

    fun applyGoalDrafts(updated: List<GoalDraftState>) {
        goalDrafts.clear()
        goalDrafts.addAll(updated)
        cardBoundsMap.keys.retainAll(0 until goalDrafts.size)
    }

    val startVal = startingWeightStr.replace(',', '.').toDoubleOrNull()
    val targetVal = targetWeightStr.replace(',', '.').toDoubleOrNull()
    val isTargetInvalid =
        FatLossValidation.isTargetWeightInvalidForSubcycle(startVal, targetVal, subcycleType)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New cycle" else "Edit cycle") },
        text = {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        viewportHeightPx = coords.size.height
                    }
                    .verticalScroll(scrollState),
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

                // All cycle types: Weight Subcycle Regime selector and Body Weight Goals
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Weight Subcycle Regime", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeightSubcycleType.entries.forEach { option ->
                            FilterChip(
                                selected = subcycleType == option,
                                onClick = {
                                    subcycleType = option
                                    isSubcycleManuallySelected = true
                                },
                                label = {
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
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
                                "Body Weight Goals",
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
                                    {
                                        val relation = if (subcycleType == WeightSubcycleType.WEIGHT_GAIN) "greater" else "less"
                                        Text("Target weight must be $relation than starting weight (${startVal?.trimmed()} kg) for ${subcycleType.label}")
                                    }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // If FAT_LOSS_BODYBUILDING: Render Fast/Rest Day Selectors
                if (cycleType == CycleType.FAT_LOSS_BODYBUILDING) {
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
                        val newDraft = GoalDraftState(
                            exercise = defaultEx,
                            reps = "1",
                            goalMetric = defaultMetric,
                            isCollapsed = false
                        )
                        val (updated, wasAppended) = appendMovementGoalDraft(goalDrafts.toList(), newDraft)
                        applyGoalDrafts(updated)
                        if (wasAppended) {
                            val newIndex = goalDrafts.lastIndex
                            requestScrollToGoalCard(newIndex)
                            if (defaultEx != null && (onFetchBestMetricValue != null || onFetchBestRm != null) && newIndex >= 0) {
                                coroutineScope.launch {
                                    val best = resolveBestValue(defaultEx.id, defaultMetric, 1)
                                    val cur = goalDrafts.getOrNull(newIndex) ?: return@launch
                                    goalDrafts[newIndex] = cur.onExerciseChanged(defaultEx, best)
                                }
                            }
                        } else {
                            val errorIndex = updated.indexOfFirst { !it.isCollapsed && it.showTargetError }
                            if (errorIndex >= 0) {
                                requestScrollToGoalCard(errorIndex)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add goal")
                        Text("Add Movement")
                    }
                }

                if (goalDrafts.isEmpty()) {
                    Text(
                        text = "No movement goals in this cycle yet. Tap + Movement to add a target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                goalDrafts.forEachIndexed { index, draft ->
                    val cardPositionModifier = Modifier.onGloballyPositioned { coords ->
                        cardBoundsMap[index] = CardLayoutBounds(
                            topPx = coords.positionInParent().y.toInt(),
                            heightPx = coords.size.height
                        )
                    }
                    if (draft.isCollapsed) {
                        CollapsedGoalSummaryCard(
                            index = index,
                            draft = draft,
                            onExpand = {
                                val updated = expandGoalDraftAt(goalDrafts.toList(), index)
                                applyGoalDrafts(updated)
                                val expandedIndex = updated.indexOfFirst { !it.isCollapsed }
                                if (expandedIndex >= 0) {
                                    requestScrollToGoalCard(expandedIndex)
                                }
                            },
                            onDelete = {
                                goalDrafts.removeAt(index)
                                cardBoundsMap.keys.retainAll(0 until goalDrafts.size)
                            },
                            modifier = cardPositionModifier
                        )
                    } else {
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = cardPositionModifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Goal ${index + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = "Done, collapse goal ${index + 1}"
                                                }
                                                .clickable(
                                                    onClickLabel = "Collapse goal",
                                                    role = Role.Button,
                                                    onClick = {
                                                        val updated = collapseGoalDraftAt(goalDrafts.toList(), index)
                                                        applyGoalDrafts(updated)
                                                        if (updated.getOrNull(index)?.isCollapsed == true) {
                                                            keyboardController?.hide()
                                                        } else if (updated.getOrNull(index)?.showTargetError == true) {
                                                            requestScrollToGoalCard(index)
                                                        }
                                                    }
                                                )
                                        ) {
                                            Text(
                                                text = "Done ▲",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                goalDrafts.removeAt(index)
                                                cardBoundsMap.keys.retainAll(0 until goalDrafts.size)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Remove goal",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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

                                // Single-Row Goal Metric Selector
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        metricPills.forEach { (metric, pillLabel) ->
                                            FilterChip(
                                                selected = draft.goalMetric == metric,
                                                onClick = {
                                                    val ex = draft.exercise
                                                    val repsInt = (draft.reps.toIntOrNull() ?: 1).coerceAtLeast(1)
                                                    // Re-tapping the active metric keeps manual Target edits intact.
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
                                                label = {
                                                    Text(
                                                        text = pillLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                modifier = Modifier.weight(1f)
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

                                // Single Full-Width Target Input Field
                                val unit = draft.unitLabel
                                OutlinedTextField(
                                    value = draft.targetWeight,
                                    onValueChange = { newTarget ->
                                        goalDrafts[index] = draft.onTargetWeightChanged(newTarget)
                                    },
                                    label = { Text("Target ($unit)") },
                                    singleLine = true,
                                    isError = draft.showTargetError,
                                    supportingText = if (draft.showTargetError) {
                                        { Text("Please enter a target value greater than 0") }
                                    } else null,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            val updated = collapseGoalDraftAt(goalDrafts.toList(), index)
                                            applyGoalDrafts(updated)
                                            if (updated.getOrNull(index)?.isCollapsed == true) {
                                                keyboardController?.hide()
                                            } else if (updated.getOrNull(index)?.showTargetError == true) {
                                                requestScrollToGoalCard(index)
                                            }
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Quick Target Stepper Pills Row (1A)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    draft.targetStepperOptions.forEach { (stepDelta, pillLabel) ->
                                        FilterChip(
                                            selected = draft.selectedModifier == stepDelta,
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                goalDrafts[index] = draft.stepTarget(stepDelta)
                                            },
                                            label = {
                                                Text(
                                                    text = pillLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
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
                    val finalStartWeight = startingWeightStr.replace(',', '.').toDoubleOrNull()
                    val finalTargetWeight = targetWeightStr.replace(',', '.').toDoubleOrNull()
                    val finalIsAutoDerived = isBaselineAutoDerived && finalStartWeight != null

                    val finalCycle = (original ?: Cycle(name = "", startDate = startDate)).copy(
                        name = name.trim(),
                        startDate = startDate,
                        endDate = endDate,
                        goal = Cycle.encodeSubcycleMetadata(goal.trim(), subcycleType),
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

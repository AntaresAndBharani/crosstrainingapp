package com.fractanomics.crosstraining.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.WorkoutJourneyDraft
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 4-step Material 3 modal bottom sheet guiding the user through:
 * 1. Raw workout notes pasting & AI document parsing.
 * 2. Routine and Session template options & Cycle selection.
 * 3. In-flight exercise grounding and category/metric verification.
 * 4. Macro-block preview and atomic Room persistence.
 *
 * Includes a SheetState dismissal guard active during Steps 2-4 to prevent accidental data loss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutJourneyAssistantSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Routine?, Session?) -> Unit = { _, _ -> }
) {
    val draft by viewModel.workoutJourneyDraft.collectAsStateWithLifecycle()
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val activeCycle by viewModel.activeCycle.collectAsStateWithLifecycle()

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Sheet dismissal guard: block swipe-down or scrim dismiss during steps 2 to 4
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            val currentStep = draft?.currentStep ?: 1
            if (currentStep in 2..4 && targetValue == SheetValue.Hidden) {
                showExitConfirmDialog = true
                false // Prevent dismissal until confirmed
            } else {
                true
            }
        }
    )

    // Local input state for Step 1
    var rawInputText by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = {
            val currentStep = draft?.currentStep ?: 1
            if (currentStep in 2..4) {
                showExitConfirmDialog = true
            } else {
                viewModel.clearWorkoutJourneyDraft()
                onDismiss()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar with Step Indicator and Close Button
            val currentStep = draft?.currentStep ?: 1
            AssistantHeader(
                currentStep = currentStep,
                onClose = {
                    if (currentStep in 2..4) {
                        showExitConfirmDialog = true
                    } else {
                        viewModel.clearWorkoutJourneyDraft()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // Step Content Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentStep) {
                    1 -> Step1RawInput(
                        rawText = rawInputText,
                        onTextChange = { rawInputText = it; parseError = null },
                        onPaste = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                rawInputText = clip
                                parseError = null
                            }
                        },
                        isParsing = isParsing,
                        errorMessage = parseError,
                        onParse = {
                            if (rawInputText.isBlank()) {
                                parseError = "Please paste or enter workout notes to continue."
                            } else {
                                isParsing = true
                                parseError = null
                                scope.launch {
                                    val ok = viewModel.processWorkoutText(rawInputText)
                                    isParsing = false
                                    if (ok) {
                                        viewModel.setJourneyStep(2)
                                    } else {
                                        parseError = "Could not parse workout movements or blocks. Please check the text format."
                                    }
                                }
                            }
                        }
                    )

                    2 -> draft?.let { currentDraft ->
                        Step2TemplatesAndCycle(
                            draft = currentDraft,
                            cycles = cycles,
                            activeCycleId = activeCycle?.id,
                            onUpdateOptions = { routineTitle, saveRoutine, sessionTitle, logSession, cycleId, date ->
                                viewModel.updateJourneyTemplateOptions(
                                    routineTitle = routineTitle,
                                    saveAsRoutine = saveRoutine,
                                    sessionTitle = sessionTitle,
                                    logAsSession = logSession,
                                    cycleId = cycleId,
                                    sessionDate = date
                                )
                            },
                            onNext = {
                                if (currentDraft.missingExercises.isNotEmpty()) {
                                    viewModel.setJourneyStep(3)
                                } else {
                                    viewModel.setJourneyStep(4)
                                }
                            },
                            onBack = { viewModel.setJourneyStep(1) }
                        )
                    }

                    3 -> draft?.let { currentDraft ->
                        Step3ReviewExercises(
                            draft = currentDraft,
                            onUpdateExercise = { name, cat, metric ->
                                viewModel.updateMissingExerciseConfig(name, cat, metric)
                            },
                            onDeleteExercise = { name ->
                                viewModel.removeMissingExercise(name)
                            },
                            onNext = { viewModel.setJourneyStep(4) },
                            onBack = { viewModel.setJourneyStep(2) }
                        )
                    }

                    4 -> draft?.let { currentDraft ->
                        Step4PreviewAndConfirm(
                            draft = currentDraft,
                            isSaving = isSaving,
                            onDeleteBlock = { index ->
                                viewModel.removeJourneyBlock(index)
                            },
                            onConfirm = {
                                isSaving = true
                                viewModel.confirmWorkoutJourney { routine, session ->
                                    isSaving = false
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        onSuccess(routine, session)
                                        onDismiss()
                                    }
                                }
                            },
                            onBack = {
                                if (currentDraft.missingExercises.isNotEmpty()) {
                                    viewModel.setJourneyStep(3)
                                } else {
                                    viewModel.setJourneyStep(2)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dismissal Guard Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Discard Workout Journey?") },
            text = {
                Text("You have unconfirmed workout steps in progress. Discarding will lose parsed blocks and exercise reviews.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        viewModel.clearWorkoutJourneyDraft()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Button(onClick = { showExitConfirmDialog = false }) {
                    Text("Continue Setup")
                }
            }
        )
    }
}

@Composable
private fun AssistantHeader(
    currentStep: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    "Workout Journey Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Step $currentStep of 4: ${stepTitle(currentStep)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

private fun stepTitle(step: Int): String = when (step) {
    1 -> "Paste Notes"
    2 -> "Routine & Session"
    3 -> "Review Movements"
    4 -> "Preview & Confirm"
    else -> ""
}

// ----------------------------------------------------------------------------
// STEP 1: Raw Input & Parser Invocation
// ----------------------------------------------------------------------------
@Composable
private fun Step1RawInput(
    rawText: String,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    isParsing: Boolean,
    errorMessage: String?,
    onParse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Paste your workout notes, daily routine, or coach instructions. The assistant will detect blocks, sets, reps, weight, and movements automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onPaste) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste from Clipboard")
            }
        }

        OutlinedTextField(
            value = rawText,
            onValueChange = onTextChange,
            placeholder = {
                Text("e.g.\nStrength & Power block:\nClean + Hang Clean + Front Squat + Push to OH 4x1 (E3MOM) @ 60, 70, 80, 85\n\nAccessories block:\nTRISET_1 3 rounds:\n- DB Incline Press x 10\n- Pull-ups x 8\n- Ab Rollers x 15")
            },
            label = { Text("Workout Notes") },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(12.dp)
        )

        if (errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        Button(
            onClick = onParse,
            enabled = !isParsing && rawText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Analyzing workout...")
            } else {
                Text("Parse & Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ----------------------------------------------------------------------------
// STEP 2: Templates & Cycle Configuration
// ----------------------------------------------------------------------------
@Composable
private fun Step2TemplatesAndCycle(
    draft: WorkoutJourneyDraft,
    cycles: List<Cycle>,
    activeCycleId: Long?,
    onUpdateOptions: (
        routineTitle: String?,
        saveRoutine: Boolean?,
        sessionTitle: String?,
        logSession: Boolean?,
        cycleId: Long?,
        date: LocalDate?
    ) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var routineTitle by remember { mutableStateOf(draft.routineTitle) }
    var saveAsRoutine by remember { mutableStateOf(draft.saveAsRoutine) }
    var sessionTitle by remember { mutableStateOf(draft.sessionTitle) }
    var logAsSession by remember { mutableStateOf(draft.logAsSession) }
    var selectedCycleId by remember { mutableStateOf(draft.cycleId ?: activeCycleId) }
    var sessionDate by remember { mutableStateOf(draft.sessionDate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Configure how this workout should be saved. You can save it as a reusable routine template, log it as an active workout session, or both.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Save as Routine Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save as Library Routine", fontWeight = FontWeight.Bold)
                        Text(
                            "Create a reusable daily template for future training",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = saveAsRoutine,
                        onCheckedChange = {
                            saveAsRoutine = it
                            onUpdateOptions(routineTitle, it, sessionTitle, logAsSession, selectedCycleId, sessionDate)
                        }
                    )
                }

                if (saveAsRoutine) {
                    OutlinedTextField(
                        value = routineTitle,
                        onValueChange = {
                            routineTitle = it
                            onUpdateOptions(it, saveAsRoutine, sessionTitle, logAsSession, selectedCycleId, sessionDate)
                        },
                        label = { Text("Routine Template Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Log as Session Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Log as Active Session", fontWeight = FontWeight.Bold)
                        Text(
                            "Record this session under your training history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = logAsSession,
                        onCheckedChange = {
                            logAsSession = it
                            onUpdateOptions(routineTitle, saveAsRoutine, sessionTitle, it, selectedCycleId, sessionDate)
                        }
                    )
                }

                if (logAsSession) {
                    OutlinedTextField(
                        value = sessionTitle,
                        onValueChange = {
                            sessionTitle = it
                            onUpdateOptions(routineTitle, saveAsRoutine, sessionTitle, logAsSession, selectedCycleId, sessionDate)
                        },
                        label = { Text("Session Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DateField(
                            label = "Date",
                            date = sessionDate,
                            onDateChange = {
                                sessionDate = it
                                onUpdateOptions(routineTitle, saveAsRoutine, sessionTitle, logAsSession, selectedCycleId, it)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Dropdown(
                            label = "Cycle",
                            options = cycles,
                            selected = cycles.firstOrNull { it.id == selectedCycleId },
                            labelOf = { it.name + if (it.isActive) " (active)" else "" },
                            onSelect = {
                                selectedCycleId = it.id
                                onUpdateOptions(routineTitle, saveAsRoutine, sessionTitle, logAsSession, it.id, sessionDate)
                            },
                            placeholder = "No cycle",
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }

            Button(
                onClick = onNext,
                enabled = saveAsRoutine || logAsSession,
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
            ) {
                Text("Next: Review Exercises", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ----------------------------------------------------------------------------
// STEP 3: Review Grounded & Inferred Exercises
// ----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step3ReviewExercises(
    draft: WorkoutJourneyDraft,
    onUpdateExercise: (name: String, category: ExerciseCategory, metric: MetricType) -> Unit,
    onDeleteExercise: (name: String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val existing = draft.resolutionResult.matchedExisting.values.distinctBy { it.id }
    val missing = draft.missingExercises
    val hasBlocks = draft.resolutionResult.blockResolutions.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Review detected exercises. Existing library exercises are linked automatically. New exercises can have their category and tracking metric customized before cataloging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Banner when all missing movements have been cataloged/resolved or deleted
        if (missing.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "All movements in library",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (existing.isNotEmpty()) {
            Text(
                "Matched in Library (${existing.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                existing.forEach { ex ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(ex.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "(${ex.category.label})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (missing.isNotEmpty()) {
            Text(
                "New Exercises to Catalog (${missing.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            missing.forEach { ex ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "NEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteExercise(ex.name) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete ${ex.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Dropdown(
                                label = "Category",
                                options = ExerciseCategory.entries,
                                selected = ex.category,
                                labelOf = { it.label },
                                onSelect = { onUpdateExercise(ex.name, it, ex.metricType) },
                                modifier = Modifier.weight(1f)
                            )

                            Dropdown(
                                label = "Metric",
                                options = MetricType.entries,
                                selected = ex.metricType,
                                labelOf = { it.label },
                                onSelect = { onUpdateExercise(ex.name, ex.category, it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }

            Button(
                onClick = onNext,
                enabled = hasBlocks,
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
            ) {
                Text("Next: Preview", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ----------------------------------------------------------------------------
// STEP 4: Preview Macro-Blocks & Persist Journey
// ----------------------------------------------------------------------------
@Composable
private fun Step4PreviewAndConfirm(
    draft: WorkoutJourneyDraft,
    isSaving: Boolean,
    onDeleteBlock: (index: Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val blockResolutions = draft.resolutionResult.blockResolutions
    val hasBlocks = blockResolutions.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Review your structured workout blocks. All macro-block sections and movement groupings will be saved with high fidelity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Summary Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (draft.saveAsRoutine) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Routine: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(draft.routineTitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (draft.logAsSession) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Session: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("${draft.sessionTitle} (${draft.sessionDate})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(
            "Blocks (${blockResolutions.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (blockResolutions.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "No blocks remaining in workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "All blocks were removed. At least one block is required to save a routine or session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Block Previews grouped by section, derived from authoritative blockResolutions
        blockResolutions.forEachIndexed { index, res ->
            val block = res.block
            val prevSection = if (index > 0) blockResolutions[index - 1].block.section.trim() else null
            val currentSection = block.section.trim()

            if (currentSection.isNotBlank() && currentSection != prevSection) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentSection,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            block.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    block.kind.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteBlock(index) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete block ${index + 1}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (block.format.isNotBlank() || block.scheme.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (block.format.isNotBlank()) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(block.format, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            if (block.scheme.isNotBlank()) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(block.scheme, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    if (block.sets.isNotEmpty()) {
                        val setsSummary = block.sets.joinToString(" | ") { s ->
                            val load = s.weight?.let { "${it.cleanString()}kg" } ?: ""
                            "${s.reps} reps" + if (load.isNotBlank()) " @ $load" else ""
                        }
                        Text(
                            setsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }

            Button(
                onClick = onConfirm,
                enabled = !isSaving && hasBlocks,
                modifier = Modifier
                    .weight(1.6f)
                    .height(48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save & Persist", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

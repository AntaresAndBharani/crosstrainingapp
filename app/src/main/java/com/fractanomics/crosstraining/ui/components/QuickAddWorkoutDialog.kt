package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS
import com.fractanomics.crosstraining.util.ParsedWorkout
import com.fractanomics.crosstraining.util.WorkoutParser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddWorkoutDialog(
    exercises: List<Exercise>,
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onConfirm: (ParsedWorkout) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Freeform Tab State
    var freeformText by remember { mutableStateOf("Snatch 5x3 @ 60, 65, 70, 75, 80 kg E2MOM") }

    // Structured Tab State
    var selectedExercise by remember { mutableStateOf<Exercise?>(exercises.firstOrNull()) }
    var selectedRoutine by remember { mutableStateOf<Routine?>(null) }
    var newExerciseName by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("E2MOM") }
    var setsScheme by remember { mutableStateOf("5x3") }
    var weightInput by remember { mutableStateOf("60, 65, 70, 75, 80") }

    val parsedWorkout by remember(selectedTab, freeformText, selectedExercise, selectedRoutine, newExerciseName, format, setsScheme, weightInput) {
        derivedStateOf {
            if (selectedTab == 0) {
                WorkoutParser.parseStructured(
                    exercise = selectedExercise,
                    newExerciseName = newExerciseName,
                    routine = selectedRoutine,
                    format = format,
                    setsInput = setsScheme,
                    weightInput = weightInput
                )
            } else {
                WorkoutParser.parseFreeform(freeformText, exercises, routines)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Quick Add / Parse Workout", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Structured Fields") },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Smart Text Box") },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) }
                    )
                }

                if (selectedTab == 0) {
                    // Structured Fields Mode
                    Dropdown(
                        label = "Base Routine / Complex (optional)",
                        options = routines,
                        selected = selectedRoutine,
                        labelOf = { it.name },
                        onSelect = { routine ->
                            selectedRoutine = routine
                            format = routine.defaultFormat
                            exercises.firstOrNull { it.id == routine.mainExerciseId }?.let {
                                selectedExercise = it
                                newExerciseName = ""
                            }
                        }
                    )

                    Dropdown(
                        label = "Base Exercise (Combo Box)",
                        options = exercises,
                        selected = selectedExercise,
                        labelOf = { it.name },
                        onSelect = { exercise ->
                            selectedExercise = exercise
                            newExerciseName = ""
                        }
                    )

                    OutlinedTextField(
                        value = newExerciseName,
                        onValueChange = {
                            newExerciseName = it
                            if (it.isNotBlank()) selectedExercise = null
                        },
                        label = { Text("…or type new exercise name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = format,
                        onValueChange = { format = it },
                        label = { Text("Format (EMOM, AMRAP, Rest 90s...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WORKOUT_FORMATS.forEach { fmt ->
                            FilterChip(
                                selected = format == fmt,
                                onClick = { format = fmt },
                                label = { Text(fmt) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = setsScheme,
                        onValueChange = { setsScheme = it },
                        label = { Text("Number of sets / scheme (e.g. 5x3 or 3-2-1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight for sets (e.g. 60, 65, 70 or 60-80)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                } else {
                    // Freeform Mode
                    Text(
                        "Type or paste your workout details in a single text box:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = freeformText,
                        onValueChange = { freeformText = it },
                        label = { Text("Workout Shorthand") },
                        placeholder = { Text("e.g. Snatch 5x3 @ 60, 65, 70, 75, 80kg E2MOM") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )

                    Text(
                        "Examples:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val examples = listOf(
                            "Snatch 5x3 @ 60, 65, 70, 75, 80kg E2MOM",
                            "Clean & Jerk 3-2-1 @ 80-100kg",
                            "Back Squat 4x5 @ 120kg",
                            "3-Position Snatch 3x2 @ 70kg E3MOM"
                        )
                        examples.forEach { ex ->
                            FilterChip(
                                selected = freeformText == ex,
                                onClick = { freeformText = ex },
                                label = { Text(ex, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Live Preview Card
                Text(
                    "Parsed Preview:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = parsedWorkout.name.ifBlank { "Workout Block" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (parsedWorkout.format.isNotBlank()) {
                            Text("Format: ${parsedWorkout.format}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (parsedWorkout.scheme.isNotBlank()) {
                            Text("Scheme: ${parsedWorkout.scheme}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Sets (${parsedWorkout.sets.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            parsedWorkout.sets.forEachIndexed { idx, s ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val wStr = if (s.weight != null) " @ ${s.weight}kg" else ""
                                    Text(
                                        text = "#${idx + 1}: ${s.reps} reps$wStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(parsedWorkout)
                    onDismiss()
                },
                enabled = parsedWorkout.sets.isNotEmpty()
            ) {
                Text("Add Block to Session")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

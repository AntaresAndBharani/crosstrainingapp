package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.data.model.SessionWithSets
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.trimmed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: AppViewModel, outerPadding: PaddingValues) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()

    val exerciseNames = exercises.associate { it.id to it.name }
    val cycleNames = cycles.associate { it.id to it.name }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Session History") }) }
    ) { pad ->
        if (sessions.isEmpty()) {
            EmptyState("No sessions logged yet.\nUse the Log tab to add your first one.", Modifier.padding(pad))
        } else {
            ScreenList(modifier = Modifier.padding(pad)) {
                items(sessions, key = { it.session.id }) { item ->
                    SessionCard(
                        item = item,
                        exerciseName = item.session.mainExerciseId?.let { exerciseNames[it] },
                        cycleName = cycleNames[item.session.cycleId],
                        onDelete = { viewModel.deleteSession(item.session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    item: SessionWithSets,
    exerciseName: String?,
    cycleName: String?,
    onDelete: () -> Unit
) {
    val s = item.session
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(exerciseName ?: "Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(s.date.formatLong(), style = MaterialTheme.typography.bodySmall)
            }
            val meta = listOfNotNull(
                s.format.ifBlank { null },
                s.repScheme.ifBlank { null },
                cycleName?.let { "Cycle: $it" }
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)

            val setsText = item.sets
                .sortedBy { it.position }
                .joinToString("   ") { set ->
                    val v = set.weight ?: set.metricValue
                    if (v != null) "${v.trimmed()}×${set.reps}" else "${set.reps} reps"
                }
            if (setsText.isNotBlank()) Text(setsText, style = MaterialTheme.typography.bodyMedium)

            if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodySmall)

            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

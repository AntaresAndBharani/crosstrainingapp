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
import androidx.compose.material3.HorizontalDivider
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
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.components.EmptyState
import com.fractanomics.crosstraining.ui.components.ScreenList
import com.fractanomics.crosstraining.ui.formatLong
import com.fractanomics.crosstraining.ui.trimmed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenEditor: (sessionId: Long, copy: Boolean) -> Unit
) {
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
                        exerciseNames = exerciseNames,
                        cycleName = cycleNames[item.session.cycleId],
                        onEdit = { onOpenEditor(item.session.id, false) },
                        onCopy = { onOpenEditor(item.session.id, true) },
                        onDelete = { viewModel.deleteSession(item.session) }
                    )
                }
            }
        }
    }
}

/** "60×3" with optional warm-up prefix and failed mark. */
private fun setToken(set: BlockSet): String {
    val v = set.weight ?: set.metricValue
    val core = if (v != null) "${v.trimmed()}×${set.reps}" else "${set.reps}"
    val prefix = if (set.isWarmup) "wu " else ""
    val suffix = if (set.isFailed) " ✗" else ""
    return "$prefix$core$suffix"
}

@Composable
private fun BlockSets(block: BlockWithSets) {
    val sets = block.sets.sortedBy { it.position }
    if (sets.isEmpty()) return
    val anyWave = sets.any { it.groupIndex != null }
    if (anyWave) {
        sets.groupBy { it.groupIndex }
            .entries
            .sortedBy { it.key ?: Int.MAX_VALUE }
            .forEach { (group, list) ->
                val label = group?.let { "W$it" } ?: "—"
                Text(
                    "$label:  " + list.sortedBy { it.position }.joinToString("   ") { setToken(it) },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
    } else {
        Text(sets.joinToString("   ") { setToken(it) }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SessionCard(
    item: SessionWithBlocks,
    exerciseNames: Map<Long, String>,
    cycleName: String?,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val s = item.session
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    s.title.ifBlank { "Session" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(s.date.formatLong(), style = MaterialTheme.typography.bodySmall)
            }
            cycleName?.let { Text("Cycle: $it", style = MaterialTheme.typography.bodySmall) }

            item.blocks.sortedBy { it.block.position }.forEach { bws ->
                val b = bws.block
                HorizontalDivider()
                val title = b.name.ifBlank { b.mainExerciseId?.let { exerciseNames[it] } ?: "Block" }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                val meta = listOfNotNull(
                    b.kind.takeIf { it != BlockKind.STRENGTH }?.label,
                    b.format.ifBlank { null },
                    b.scheme.ifBlank { null },
                    b.mainExerciseId?.let { exerciseNames[it] }?.takeIf { it != b.name }
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)

                if (b.description.isNotBlank()) Text(b.description, style = MaterialTheme.typography.bodyMedium)

                BlockSets(bws)

                val result = listOfNotNull(
                    b.resultText.ifBlank { null },
                    b.resultValue?.let { "= ${it.trimmed()}" }
                ).joinToString(" ")
                if (result.isNotBlank()) Text("Score: $result", style = MaterialTheme.typography.bodyMedium)

                if (b.notes.isNotBlank()) Text(b.notes, style = MaterialTheme.typography.bodySmall)
            }

            if (s.notes.isNotBlank()) {
                HorizontalDivider()
                Text(s.notes, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

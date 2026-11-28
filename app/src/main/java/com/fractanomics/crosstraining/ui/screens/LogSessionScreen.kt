package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.AppViewModel

/** The Log tab: a persistent active workout draft that clears itself after save or explicit discard. */
@Composable
fun LogSessionScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    val activeWorkoutDraft by viewModel.activeWorkoutDraft.collectAsStateWithLifecycle()
    val draftRevision by viewModel.activeWorkoutDraftRevision.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()

    SessionEditorBody(
        viewModel = viewModel,
        outerPadding = outerPadding,
        screenTitle = "Log Session",
        seed = remember { emptySessionSeed() },
        seedKey = "log-$demoMode-$draftRevision",
        saveLabel = "Save Session",
        clearAfterSave = true,
        onBack = null,
        onOpenDrawer = onOpenDrawer,
        onOpenTimer = onOpenTimer,
        isActiveLogTab = true,
        persistedDraft = activeWorkoutDraft,
        onDraftChanged = { viewModel.updateActiveWorkoutDraft(it) },
        onFlushDraft = { viewModel.flushActiveWorkoutDraftNow(it) },
        onDiscardDraft = { viewModel.clearActiveWorkoutDraft() },
        onSubmit = { viewModel.saveSession(it) }
    )
}

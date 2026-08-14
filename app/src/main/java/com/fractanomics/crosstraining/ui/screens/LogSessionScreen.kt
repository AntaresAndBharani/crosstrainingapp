package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fractanomics.crosstraining.ui.AppViewModel

/** The Log tab: a fresh session form that clears itself after each save. */
@Composable
fun LogSessionScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    SessionEditorBody(
        viewModel = viewModel,
        outerPadding = outerPadding,
        screenTitle = "Log Session",
        seed = remember { emptySessionSeed() },
        seedKey = "log",
        saveLabel = "Save session",
        clearAfterSave = true,
        onBack = null,
        onOpenDrawer = onOpenDrawer,
        onOpenTimer = onOpenTimer,
        onSubmit = { viewModel.saveSession(it) }
    )
}

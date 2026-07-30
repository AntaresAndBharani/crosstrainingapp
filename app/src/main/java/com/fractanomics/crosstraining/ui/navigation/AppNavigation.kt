package com.fractanomics.crosstraining.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.screens.CyclesScreen
import com.fractanomics.crosstraining.ui.screens.HistoryScreen
import com.fractanomics.crosstraining.ui.screens.LibraryScreen
import com.fractanomics.crosstraining.ui.screens.LogSessionScreen
import com.fractanomics.crosstraining.ui.screens.ProgressScreen
import com.fractanomics.crosstraining.ui.screens.SessionEditorScreen

import androidx.compose.material.icons.filled.Timer
import com.fractanomics.crosstraining.ui.screens.TimerScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    LOG("log", "Log", Icons.Filled.FitnessCenter),
    HISTORY("history", "History", Icons.Filled.History),
    PROGRESS("progress", "Progress", Icons.Filled.BarChart),
    CYCLES("cycles", "Cycles", Icons.Filled.CalendarMonth),
    LIBRARY("library", "Library", Icons.Filled.MenuBook),
    TIMER("timer", "Timer", Icons.Filled.Timer)
}

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val destinations = Destination.entries
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                if (demoMode) DemoBanner()
                NavigationBar {
                    val backStackEntry = navController.currentBackStackEntryAsState().value
                    val currentDestination = backStackEntry?.destination
                    destinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.LOG.route
        ) {
            composable(Destination.LOG.route) {
                LogSessionScreen(viewModel, innerPadding)
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    outerPadding = innerPadding,
                    onOpenEditor = { sessionId, copy ->
                        navController.navigate("sessionEditor/$sessionId/$copy")
                    }
                )
            }
            composable(
                route = "sessionEditor/{sessionId}/{copy}",
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType },
                    navArgument("copy") { type = NavType.BoolType }
                )
            ) { entry ->
                SessionEditorScreen(
                    viewModel = viewModel,
                    outerPadding = innerPadding,
                    sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                    copy = entry.arguments?.getBoolean("copy") ?: false,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destination.PROGRESS.route) {
                ProgressScreen(viewModel, innerPadding)
            }
            composable(Destination.CYCLES.route) {
                CyclesScreen(viewModel, innerPadding)
            }
            composable(Destination.LIBRARY.route) {
                LibraryScreen(viewModel, innerPadding)
            }
            composable(Destination.TIMER.route) {
                TimerScreen(innerPadding)
            }
        }
    }
}

/** Persistent strip shown above the nav bar while demo data is active. */
@Composable
private fun DemoBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Demo data — your real data is untouched",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            textAlign = TextAlign.Center
        )
    }
}

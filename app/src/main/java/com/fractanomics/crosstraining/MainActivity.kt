package com.fractanomics.crosstraining

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.navigation.AppNavigation
import com.fractanomics.crosstraining.ui.navigation.NavigationIntentHandler
import com.fractanomics.crosstraining.ui.theme.CrossTrainingTheme

class MainActivity : ComponentActivity() {

    internal val navigationHandler = NavigationIntentHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        navigationHandler.handleIntent(intent)
        val dataModes = (application as CrossTrainingApp).dataModes
        setContent {
            val viewModel: AppViewModel =
                viewModel(factory = AppViewModel.factory(dataModes))
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val pendingRoute by navigationHandler.pendingDestination.collectAsStateWithLifecycle()
            CrossTrainingTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        viewModel = viewModel,
                        pendingRoute = pendingRoute,
                        onRouteHandled = { navigationHandler.onDestinationHandled() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationHandler.handleIntent(intent)
    }
}

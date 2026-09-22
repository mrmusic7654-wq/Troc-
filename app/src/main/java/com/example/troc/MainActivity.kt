package com.example.troc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.troc.ui.navigation.Routes
import com.example.troc.ui.navigation.TrocNavGraph
import com.example.troc.ui.screens.MainViewModel
import com.example.troc.ui.theme.TrocTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            TrocTheme(settings = settings) {
                // Decide the start destination once, from the persisted onboarding flag.
                val startDestination = rememberStartDestination(settings.onboardingDone)
                TrocNavGraph(startDestination = startDestination)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun rememberStartDestination(onboardingDone: Boolean): String =
        androidx.compose.runtime.saveable.rememberSaveable { if (onboardingDone) Routes.HOME else Routes.ONBOARDING }
}

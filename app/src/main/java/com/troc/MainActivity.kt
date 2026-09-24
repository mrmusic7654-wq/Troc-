package com.troc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.troc.data.prefs.SettingsDataStore
import com.troc.navigation.NavGraph
import com.troc.ui.theme.TrocTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var themeSetting by remember { mutableStateOf("system") }
            var onboardingDone by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                themeSetting = settingsDataStore.themeFlow.first()
                onboardingDone = settingsDataStore.onboardingDoneFlow.first()
            }

            // Observe theme changes
            LaunchedEffect(Unit) {
                settingsDataStore.themeFlow.collect { themeSetting = it }
            }

            TrocTheme(themeSetting = themeSetting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Listen for selected chat id from history
                    val currentBackStackEntry = navController.currentBackStackEntry
                    LaunchedEffect(currentBackStackEntry) {
                        navController.currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("selected_chat_id", null)?.collect { chatId ->
                            if (chatId != null) {
                                // Handle chat selection - would need to pass to HomeViewModel
                                // For simplicity, we navigate to home and viewModel will observe
                                navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_chat_id")
                            }
                        }
                    }

                    NavGraph(
                        navController = navController,
                        onboardingDone = onboardingDone
                    )
                }
            }
        }
    }
}

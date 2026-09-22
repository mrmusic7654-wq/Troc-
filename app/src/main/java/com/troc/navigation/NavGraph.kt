package com.troc.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.troc.ui.screens.*

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Sandbox : Screen("sandbox")
    object VoiceMode : Screen("voice_mode")
    object History : Screen("history")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    onboardingDone: Boolean = true
) {
    NavHost(
        navController = navController,
        startDestination = if (!onboardingDone) Screen.Onboarding.route else startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSandbox = { navController.navigate(Screen.Sandbox.route) },
                onNavigateToVoice = { navController.navigate(Screen.VoiceMode.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Sandbox.route) {
            SandboxScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.VoiceMode.route) {
            VoiceModeScreen(
                onClose = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onChatSelected = { chatId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selected_chat_id", chatId)
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

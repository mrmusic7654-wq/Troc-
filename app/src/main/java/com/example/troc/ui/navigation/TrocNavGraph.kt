package com.example.troc.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.troc.ui.sandbox.SandboxScreen
import com.example.troc.ui.screens.history.HistoryScreen
import com.example.troc.ui.screens.home.HomeScreen
import com.example.troc.ui.screens.onboarding.OnboardingScreen
import com.example.troc.ui.screens.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home?chatId={chatId}"
    const val SANDBOX = "sandbox?chatId={chatId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun home(chatId: String? = null): String =
        if (chatId.isNullOrBlank()) "home" else "home?chatId=$chatId"

    fun sandbox(chatId: String? = null): String =
        if (chatId.isNullOrBlank()) "sandbox" else "sandbox?chatId=$chatId"
}

@Composable
fun TrocNavGraph(
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(280)) }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.home()) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.HOME,
            arguments = listOf(navArgument("chatId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            HomeScreen(
                navController = navController,
                chatId = entry.arguments?.getString("chatId")
            )
        }
        composable(
            route = Routes.SANDBOX,
            arguments = listOf(navArgument("chatId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            SandboxScreen(
                onBack = { navController.popBackStack() },
                chatId = entry.arguments?.getString("chatId")
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate(Routes.home(chatId)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

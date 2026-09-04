package com.privateagent.private_agent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privateagent.private_agent.data.SettingsRepository
import com.privateagent.private_agent.service.TaskExecutor
import com.privateagent.private_agent.ui.screens.HistoryScreen
import com.privateagent.private_agent.ui.screens.HomeScreen
import com.privateagent.private_agent.ui.screens.OnboardingScreen
import com.privateagent.private_agent.ui.screens.SessionDetailScreen
import com.privateagent.private_agent.ui.screens.SettingsScreen
import com.privateagent.private_agent.ui.theme.PrivateAgentTheme

@Composable
fun PrivateAgentApp() {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val executor = remember { TaskExecutor.getInstance(context) }

    val themePreference by settingsRepo.themePreference.collectAsState()
    val onboardingComplete by settingsRepo.onboardingComplete.collectAsState()

    PrivateAgentTheme(themePreference = themePreference) {
        val navController = rememberNavController()
        val startDestination = if (onboardingComplete) "home" else "onboarding"

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onFinishOnboarding = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToHistory = { navController.navigate("history") },
                    onNavigateToSession = { sessionId -> navController.navigate("session/$sessionId") }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("history") {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSession = { sessionId -> navController.navigate("session/$sessionId") }
                )
            }

            composable(
                route = "session/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                SessionDetailScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onRerunTask = { goal ->
                        executor.startTask(goal)
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }
        }
    }
}

package com.smartreminder.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartreminder.ui.create.CreateScreen
import com.smartreminder.ui.edit.EditScreen
import com.smartreminder.ui.history.HistoryScreen
import com.smartreminder.ui.home.HomeScreen
import com.smartreminder.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Create : Screen("create")
    object Edit : Screen("edit/{reminderId}") {
        fun createRoute(reminderId: Long) = "edit/$reminderId"
    }
    object History : Screen("history")
    object Settings : Screen("settings")
}

@Composable
fun SmartReminderNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCreate = { navController.navigate(Screen.Create.route) },
                onNavigateToEdit = { reminderId ->
                    navController.navigate(Screen.Edit.createRoute(reminderId))
                },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(Screen.Create.route) {
            CreateScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Edit.route,
            arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: return@composable
            EditScreen(
                reminderId = reminderId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

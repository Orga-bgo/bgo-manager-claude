package com.mgomanager.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mgomanager.app.ui.screens.detail.DetailScreen
import com.mgomanager.app.ui.screens.home.HomeScreen
import com.mgomanager.app.ui.screens.settings.SettingsScreen
import com.mgomanager.app.ui.screens.logs.LogScreen
import com.mgomanager.app.ui.screens.idcompare.IdCompareScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Detail : Screen("detail/{accountId}") {
        fun createRoute(accountId: Long) = "detail/$accountId"
    }
    object Settings : Screen("settings")
    object Logs : Screen("logs")
    object IdCompare : Screen("id_compare")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: return@composable
            DetailScreen(navController = navController, accountId = accountId)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Screen.Logs.route) {
            LogScreen(navController = navController)
        }

        composable(Screen.IdCompare.route) {
            IdCompareScreen(navController = navController)
        }
    }
}

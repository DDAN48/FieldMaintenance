package com.example.fieldmaintenance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.fieldmaintenance.ui.screens.AddAssetScreen
import com.example.fieldmaintenance.ui.screens.AssetSummaryScreen
import com.example.fieldmaintenance.ui.screens.GeneralInfoScreen
import com.example.fieldmaintenance.ui.screens.HomeScreen
import com.example.fieldmaintenance.ui.screens.ManualScreen
import com.example.fieldmaintenance.ui.screens.MonitorQrScreen
import com.example.fieldmaintenance.ui.screens.PassivesScreen
import com.example.fieldmaintenance.ui.screens.SettingsScreen
import com.example.fieldmaintenance.ui.screens.TrashScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Trash : Screen("trash")
    object Settings : Screen("settings")
    object Manual : Screen("manual")
    object GeneralInfo : Screen("general_info/{reportId}") {
        fun createRoute(reportId: String) = "general_info/$reportId"
    }
    object AddAsset : Screen("add_asset/{reportId}?assetId={assetId}") {
        fun createRoute(reportId: String, assetId: String? = null): String {
            return if (assetId.isNullOrBlank()) {
                "add_asset/$reportId"
            } else {
                "add_asset/$reportId?assetId=$assetId"
            }
        }
    }
    object AssetSummary : Screen("asset_summary/{reportId}") {
        fun createRoute(reportId: String) = "asset_summary/$reportId"
    }
    object Passives : Screen("passives/{reportId}") {
        fun createRoute(reportId: String) = "passives/$reportId"
    }
    object MonitorQr : Screen("monitor_qr/{reportId}") {
        fun createRoute(reportId: String) = "monitor_qr/$reportId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Trash.route) {
            TrashScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Manual.route) {
            ManualScreen(navController = navController)
        }
        composable(Screen.GeneralInfo.route) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            GeneralInfoScreen(
                navController = navController,
                reportId = reportId
            )
        }
        composable(Screen.AddAsset.route) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            val assetId = backStackEntry.arguments?.getString("assetId")
            AddAssetScreen(
                navController = navController,
                reportId = reportId,
                assetId = assetId
            )
        }
        composable(Screen.AssetSummary.route) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            AssetSummaryScreen(
                navController = navController,
                reportId = reportId
            )
        }
        composable(Screen.Passives.route) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            PassivesScreen(navController = navController, reportId = reportId)
        }
        composable(Screen.MonitorQr.route) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            MonitorQrScreen(navController = navController, reportId = reportId)
        }
    }
}


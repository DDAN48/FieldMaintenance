@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fieldmaintenance.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DonutSmall
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldmaintenance.ui.navigation.Screen

enum class ReportTab { GENERAL, ACTIVOS, PASIVOS, MONITOR }

@Composable
fun ReportBottomBar(
    navController: NavController,
    reportId: String,
    selected: ReportTab,
    generalInfoComplete: Boolean = true
) {
    Column {
        // Borde superior sutil para separar la cinta del contenido scrolleable
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        )
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(
                selected = selected == ReportTab.GENERAL,
                onClick = {
                    navController.navigate(Screen.GeneralInfo.createRoute(reportId)) {
                        launchSingleTop = true
                    }
                },
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Información General") },
                label = { Text("Info") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
            NavigationBarItem(
                selected = selected == ReportTab.ACTIVOS,
                enabled = generalInfoComplete,
                onClick = {
                    navController.navigate(Screen.AssetSummary.createRoute(reportId)) {
                        launchSingleTop = true
                    }
                },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Activos") },
                label = { Text("Activos") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
            NavigationBarItem(
                selected = selected == ReportTab.PASIVOS,
                enabled = generalInfoComplete,
                onClick = {
                    navController.navigate(Screen.Passives.createRoute(reportId)) {
                        launchSingleTop = true
                    }
                },
                icon = { Icon(Icons.Default.DonutSmall, contentDescription = "Pasivos") },
                label = { Text("Pasivos") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
            NavigationBarItem(
                selected = selected == ReportTab.MONITOR,
                enabled = generalInfoComplete,
                onClick = {
                    navController.navigate(Screen.MonitorQr.createRoute(reportId)) {
                        launchSingleTop = true
                    }
                },
                icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Monitoria y QR") },
                label = { Text("Monitor") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}



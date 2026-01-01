package com.example.fieldmaintenance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.fieldmaintenance.ui.navigation.NavGraph
import com.example.fieldmaintenance.ui.theme.FieldMaintenanceTheme
import com.example.fieldmaintenance.util.PlanRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldMaintenanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        // Refresh del Plan al iniciar. Si falla/no trae filas, se usa el cache guardado.
                        runCatching { PlanRepository(context).refreshOnAppStart() }
                    }
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
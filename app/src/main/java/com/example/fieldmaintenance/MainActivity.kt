package com.example.fieldmaintenance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.fieldmaintenance.ui.navigation.NavGraph
import com.example.fieldmaintenance.ui.theme.FieldMaintenanceTheme
import com.example.fieldmaintenance.util.PlanRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sharedUrisState = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
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
                    NavGraph(
                        navController = navController,
                        sharedUris = sharedUrisState.value,
                        onShareHandled = { sharedUrisState.value = emptyList() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val uris = when (action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.toList().orEmpty()
            }
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        sharedUrisState.value = uris
        intent.action = null
        intent.replaceExtras(Bundle())
    }
}

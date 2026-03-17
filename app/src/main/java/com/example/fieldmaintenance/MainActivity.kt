package com.example.fieldmaintenance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.fieldmaintenance.ui.navigation.NavGraph
import com.example.fieldmaintenance.ui.theme.FieldMaintenanceTheme
import com.example.fieldmaintenance.util.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
                    var appReady by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        // Let Compose render the splash at least once.
                        withFrameNanos { }
                        val start = SystemClock.uptimeMillis()
                        // Refresh del Plan al iniciar. Si falla/no trae filas, se usa el cache guardado.
                        runCatching {
                            withContext(Dispatchers.IO) {
                                PlanRepository(context).refreshOnAppStart()
                            }
                        }
                        // Ensure the splash is visible (avoid white screen flicker).
                        val elapsed = SystemClock.uptimeMillis() - start
                        val minSplashMs = 450L
                        if (elapsed < minSplashMs) delay(minSplashMs - elapsed)
                        appReady = true
                    }
                    val navController = rememberNavController()
                    if (!appReady) {
                        LaunchSplash(versionName = BuildConfig.VERSION_NAME)
                    } else {
                        NavGraph(
                            navController = navController,
                            sharedUris = sharedUrisState.value,
                            onShareHandled = { sharedUrisState.value = emptyList() }
                        )
                    }
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

@androidx.compose.runtime.Composable
private fun LaunchSplash(versionName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            androidx.compose.material3.Text(
                text = "Versión $versionName",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
            )
        }
    }
}

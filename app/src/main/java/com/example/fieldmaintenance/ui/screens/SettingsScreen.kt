package com.example.fieldmaintenance.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.util.PlanCache
import com.example.fieldmaintenance.util.PlanCacheStore
import com.example.fieldmaintenance.util.PlanCsv
import com.example.fieldmaintenance.util.PlanParser
import com.example.fieldmaintenance.util.PlanRepository
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val planRepo = remember { PlanRepository(context.applicationContext) }
    val planCacheStore = remember { PlanCacheStore(context.applicationContext) }
    val settings by store.settings.collectAsState(initial = AppSettings())
    val planCache by planCacheStore.cache.collectAsState(initial = PlanCache())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var sheetUrl by remember { mutableStateOf("") }
    var defaultResponsible by remember { mutableStateOf("") }
    var defaultContractor by remember { mutableStateOf("") }
    var defaultMeter by remember { mutableStateOf("") }
    var showCsvColumnsHelp by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        sheetUrl = settings.sheetUrl
        defaultResponsible = settings.defaultResponsible
        defaultContractor = settings.defaultContractor
        defaultMeter = settings.defaultMeterNumber
    }

    fun save() {
        scope.launch {
            store.setSheetUrl(sheetUrl)
            store.setDefaultResponsible(defaultResponsible)
            store.setDefaultContractor(defaultContractor)
            store.setDefaultMeterNumber(defaultMeter)
            snackbarHostState.showSnackbar("Configuración guardada")
        }
    }

    fun testPlanConnection() {
        scope.launch {
            val result = runCatching { planRepo.refreshFromUrlDetailed(sheetUrl) }
                .getOrElse {
                    com.example.fieldmaintenance.util.PlanRepository.RefreshResult(
                        ok = false,
                        message = "Comunicación con Plan fallida"
                    )
                }

            if (result.ok) {
                snackbarHostState.showSnackbar("Conexión OK: Plan actualizado (${result.rowCount} filas)")
            } else {
                val suffix = if (result.message.isNotBlank()) ": ${result.message}" else ""
                snackbarHostState.showSnackbar("Comunicación con Plan fallida$suffix (se usará el último guardado)")
            }
        }
    }

    fun validateCsvHeader(csv: String): Pair<Boolean, String> {
        val table = PlanCsv.parse(csv)
        val header = table.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
        val required = listOf(
            "contratista",
            "nombre del nodo",
            "tecnología",
            "nombre del evento",
            "po directa",
            "po retorno",
            "distancia sfp"
        )
        val missing = required.filter { it !in header }
        return if (missing.isEmpty()) true to "" else false to "Faltan columnas: ${missing.joinToString(", ")}"
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull().orEmpty()

            if (text.isBlank()) {
                snackbarHostState.showSnackbar("CSV vacío o no se pudo leer")
                return@launch
            }

            val (okHeader, msg) = validateCsvHeader(text)
            if (!okHeader) {
                snackbarHostState.showSnackbar(msg)
                return@launch
            }

            val rows = runCatching { PlanParser.parseRows(text) }.getOrDefault(emptyList())
            if (rows.isEmpty()) {
                snackbarHostState.showSnackbar("No se encontraron filas válidas en el CSV")
                return@launch
            }

            planCacheStore.save(
                PlanCache(
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    sourceUrl = "local_csv",
                    rows = rows
                )
            )
            snackbarHostState.showSnackbar("Plan cargado desde CSV (${rows.size} filas)")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Manual.route) }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Manual")
                    }
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Valores por defecto (Información General)",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = defaultResponsible,
                onValueChange = { defaultResponsible = it },
                label = { Text("Responsable") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = defaultContractor,
                onValueChange = { defaultContractor = it },
                label = { Text("Contratista") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = defaultMeter,
                onValueChange = { defaultMeter = it },
                label = { Text("Número de Medidor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Plan (Google Sheet)",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = sheetUrl,
                onValueChange = { sheetUrl = it },
                label = { Text("URL de Google Sheet") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Debe estar público o publicado. Si falla, puedes cargar el Plan desde CSV.")
                }
            )

            OutlinedButton(
                onClick = { testPlanConnection() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar conexión con Plan")
            }

            OutlinedButton(
                onClick = { csvPicker.launch(arrayOf("text/csv", "text/plain", "application/vnd.ms-excel")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cargar Plan desde CSV")
            }
            IconButton(onClick = { showCsvColumnsHelp = true }) {
                Icon(Icons.Default.Warning, contentDescription = "Columnas requeridas CSV")
            }

            val csvLoaded = planCache.sourceUrl == "local_csv" && planCache.rows.isNotEmpty()
            Text(
                text = if (csvLoaded) "CSV cargado: Sí (${planCache.rows.size} filas)" else "CSV cargado: No",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }

    if (showCsvColumnsHelp) {
        AlertDialog(
            onDismissRequest = { showCsvColumnsHelp = false },
            title = { Text("Columnas requeridas (CSV)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("El documento debe tener exactamente estos encabezados:")
                    Text("Contratista")
                    Text("Nombre del Nodo")
                    Text("Tecnología")
                    Text("Nombre del Evento")
                    Text("PO Directa")
                    Text("PO Retorno")
                    Text("Distancia SFP")
                }
            },
            confirmButton = {
                TextButton(onClick = { showCsvColumnsHelp = false }) { Text("OK") }
            }
        )
    }
}



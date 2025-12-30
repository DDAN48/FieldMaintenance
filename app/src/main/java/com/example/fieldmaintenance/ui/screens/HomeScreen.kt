@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fieldmaintenance.ui.screens

import android.net.Uri
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.MaintenanceViewModel
import com.example.fieldmaintenance.ui.viewmodel.MaintenanceViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.ExportManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    
    val repository = DatabaseProvider.getRepository()
    val viewModel: MaintenanceViewModel = viewModel(
        factory = MaintenanceViewModelFactory(repository)
    )
    val exportManager = remember { ExportManager(context, repository) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val reports by viewModel.reports.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var reportToDelete by remember { mutableStateOf<MaintenanceReport?>(null) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported =
                exportManager.importFromZip(uri)
                    ?: exportManager.importFromJSON(uri)
            if (imported != null) {
                snackbarHostState.showSnackbar("Mantenimiento importado")
                navController.navigate(Screen.AssetSummary.createRoute(imported.id))
            } else {
                snackbarHostState.showSnackbar("Archivo inválido o no compatible")
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column {
                // Borde superior sutil para separar la cinta del contenido scrolleable
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                )
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {
                        showSearch = false
                        searchQuery = ""
                        viewModel.search("")
                        focusManager.clearFocus()
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = showSearch,
                    onClick = { showSearch = true },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                    label = { Text("Buscar") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        val reportId = viewModel.createNewReport()
                        navController.navigate(Screen.GeneralInfo.createRoute(reportId))
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo") },
                    label = { Text("Nuevo") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/json", "text/json", "application/octet-stream"))
                    },
                    icon = { Icon(Icons.Default.Upload, contentDescription = "Importar") },
                    label = { Text("Importar") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Trash.route) },
                    icon = { Icon(Icons.Default.Delete, contentDescription = "Papelera") },
                    label = { Text("Papelera") }
                )
            }
                }
        },
        topBar = {
            TopAppBar(
                title = { Text("Inicio del Mantenimiento") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.search(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Buscar por ticket o nodo") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            LaunchedEffect(showSearch) {
                if (showSearch) {
                    kotlinx.coroutines.delay(50)
                    runCatching { focusRequester.requestFocus() }
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reports) { report ->
                    MaintenanceReportCard(
                        report = report,
                        onClick = {
                            navController.navigate(Screen.GeneralInfo.createRoute(report.id))
                        },
                        onLongClick = { reportToDelete = report }
                    )
                }
            }
        }
    }
    
    reportToDelete?.let { report ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text("Mover a la papelera") },
            text = { Text("¿Estás seguro de mover este mantenimiento a la papelera?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(report)
                    reportToDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun MaintenanceReportCard(
    report: MaintenanceReport,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.nodeName.ifEmpty { "Sin nodo" },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .format(Date(report.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (report.eventName.isNotBlank()) {
                    Text(
                        text = report.eventName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


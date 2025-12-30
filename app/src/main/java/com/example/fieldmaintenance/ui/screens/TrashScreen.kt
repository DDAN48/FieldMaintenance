@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fieldmaintenance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.ui.viewmodel.MaintenanceViewModel
import com.example.fieldmaintenance.ui.viewmodel.MaintenanceViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(navController: NavController) {
    val context = LocalContext.current
    DatabaseProvider.init(context)

    val repository = DatabaseProvider.getRepository()
    val viewModel: MaintenanceViewModel = viewModel(factory = MaintenanceViewModelFactory(repository))

    val reports by viewModel.trashReports.collectAsState()
    var selected by remember { mutableStateOf<MaintenanceReport?>(null) }
    var showPermanentConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { viewModel.loadTrash() }

    Scaffold(
        bottomBar = {
            Column {
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
                            focusManager.clearFocus()
                            navController.navigate(com.example.fieldmaintenance.ui.navigation.Screen.Home.route) { popUpTo(0) }
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
                    // Nuevo (deshabilitado en papelera)
                    NavigationBarItem(
                        selected = false,
                        enabled = false,
                        onClick = { },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo") },
                        label = { Text("Nuevo") }
                    )
                    // Importar (deshabilitado en papelera)
                    NavigationBarItem(
                        selected = false,
                        enabled = false,
                        onClick = { },
                        icon = { Icon(Icons.Default.Upload, contentDescription = "Importar") },
                        label = { Text("Importar") }
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick = { /* ya estás en papelera */ },
                        icon = { Icon(Icons.Default.Delete, contentDescription = "Papelera") },
                        label = { Text("Papelera") }
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Papelera") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Buscar por nodo") },
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

            val filtered = remember(reports, searchQuery) {
                if (searchQuery.isBlank()) reports
                else reports.filter { it.nodeName.contains(searchQuery, ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { selected = report },
                            onLongClick = { selected = report }
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
                    }
                }
            }
            }
        }
    }

    selected?.let { report ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Mantenimiento borrado") },
            text = { Text("¿Qué deseas hacer con este mantenimiento?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreReport(report)
                    selected = null
                    navController.popBackStack()
                }) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recuperar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermanentConfirm = true
                }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Borrar definitivamente")
                }
            }
        )
    }

    if (showPermanentConfirm && selected != null) {
        val report = selected!!
        AlertDialog(
            onDismissRequest = { showPermanentConfirm = false },
            title = { Text("Borrar definitivamente") },
            text = { Text("¿Estás seguro? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReportPermanently(report)
                    showPermanentConfirm = false
                    selected = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}



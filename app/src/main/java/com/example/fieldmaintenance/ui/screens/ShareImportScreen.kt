@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fieldmaintenance.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.navigation.NavController
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import java.net.URLConnection
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.AssetType
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementAssetIdKey
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementReportIdKey
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementAssetTypeKey
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.MaintenanceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareImportScreen(
    navController: NavController,
    sharedUris: List<Uri>,
    onShareHandled: () -> Unit
) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    val repository = DatabaseProvider.getRepository()
    val reports by repository.getAllReports().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isAutoImporting by remember { mutableStateOf(false) }
    val previousEntry = navController.previousBackStackEntry
    val pendingReportId = previousEntry?.savedStateHandle?.get<String>(PendingMeasurementReportIdKey)
    val pendingAssetId = previousEntry?.savedStateHandle?.get<String>(PendingMeasurementAssetIdKey)
    val pendingAssetType = previousEntry?.savedStateHandle?.get<String>(PendingMeasurementAssetTypeKey)
    val hasPendingAsset = !pendingReportId.isNullOrBlank() && !pendingAssetId.isNullOrBlank()

    LaunchedEffect(sharedUris, hasPendingAsset) {
        if (!hasPendingAsset || sharedUris.isEmpty()) return@LaunchedEffect
        isAutoImporting = true
        try {
            val reportId = pendingReportId ?: return@LaunchedEffect
            val assetId = pendingAssetId ?: return@LaunchedEffect
            val report = withContext(Dispatchers.IO) { repository.getReportById(reportId) }
            val asset = withContext(Dispatchers.IO) { repository.getAssetById(assetId) }
            if (report == null || asset == null) {
                previousEntry?.savedStateHandle?.remove<String>(PendingMeasurementReportIdKey)
                previousEntry?.savedStateHandle?.remove<String>(PendingMeasurementAssetIdKey)
                previousEntry?.savedStateHandle?.remove<String>(PendingMeasurementAssetTypeKey)
                return@LaunchedEffect
            }
            val resolvedAsset = if (pendingAssetType == AssetType.AMPLIFIER.name) {
                asset.copy(type = AssetType.AMPLIFIER)
            } else {
                asset
            }
            val assetLabel = when (resolvedAsset.type) {
                AssetType.NODE -> "Nodo"
                AssetType.AMPLIFIER -> {
                    val portName = asset.port?.name ?: ""
                    val portIndex = asset.portIndex?.let { String.format("%02d", it) } ?: ""
                    "Amplificador $portName$portIndex".trim()
                }
            }
            withContext(Dispatchers.IO) {
                val reportFolder = MaintenanceStorage.reportFolderName(report.eventName, report.id)
                val assetDir = MaintenanceStorage.ensureAssetDir(context, reportFolder, resolvedAsset)
                sharedUris.forEach { uri ->
                    MaintenanceStorage.copySharedFileToDir(context, uri, assetDir)
                }
            }
            previousEntry?.savedStateHandle?.remove<String>(PendingMeasurementReportIdKey)
            previousEntry?.savedStateHandle?.remove<String>(PendingMeasurementAssetIdKey)
            previousEntry?.savedStateHandle?.remove<String>(PendingMeasurementAssetTypeKey)
            snackbarHostState.showSnackbar("Archivos guardados en $assetLabel")
            onShareHandled()
            navController.popBackStack()
        } finally {
            isAutoImporting = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Importar Mediciones") },
                navigationIcon = {
                    IconButton(onClick = {
                        onShareHandled()
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onShareHandled()
                        navController.navigate(Screen.Home.route)
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
            Spacer(modifier = Modifier.height(12.dp))

                if (reports.isEmpty()) {
                    Text("No hay mantenimientos creados todavía.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { navController.navigate(Screen.Home.route) }) {
                        Text("Crear mantenimiento")
                    }
                    return@Box
                }

                Text(
                    text = if (sharedUris.isEmpty()) {
                        "Seleccione un mantenimiento para ver las mediciones."
                    } else {
                        "Seleccione una carpeta para guardar las mediciones."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports) { report ->
                        ReportShareCard(
                            report = report,
                            sharedUris = sharedUris,
                            context = context,
                            autoReturn = hasPendingAsset,
                            onImportFinished = {
                                onShareHandled()
                                if (!navController.popBackStack()) {
                                    navController.navigate(Screen.Home.route)
                                }
                            },
                            onShowMessage = { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        )
                    }
                }
            }

            if (isAutoImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "Subiendo mediciones...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportShareCard(
    report: MaintenanceReport,
    sharedUris: List<Uri>,
    context: Context,
    autoReturn: Boolean,
    onImportFinished: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val repository = DatabaseProvider.getRepository()
    val assets by repository.getAssetsByReportId(report.id).collectAsState(initial = emptyList())
    val reportFolder = MaintenanceStorage.reportFolderName(report.eventName, report.id)
    var isExpanded by remember(report.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reportCardTitle(report),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (isExpanded) {
                Text(
                    text = "Carpeta: $reportFolder",
                    style = MaterialTheme.typography.bodySmall
                )

                if (assets.isNotEmpty()) {
                    Text(
                        text = "Activos",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    assets.forEach { asset ->
                        AssetShareRow(
                            asset = asset,
                            reportFolder = reportFolder,
                            sharedUris = sharedUris,
                            context = context,
                            autoReturn = autoReturn,
                            onImportFinished = onImportFinished,
                            onShowMessage = onShowMessage
                        )
                    }
                }
            }
        }
    }
}

private fun reportCardTitle(report: MaintenanceReport): String {
    val nodeName = report.nodeName.trim()
    val eventName = report.eventName.trim()
    return when {
        nodeName.isNotEmpty() && eventName.isNotEmpty() -> "$nodeName-$eventName"
        nodeName.isNotEmpty() -> nodeName
        eventName.isNotEmpty() -> eventName
        else -> "Mantenimiento ${report.id.take(6)}"
    }
}

@Composable
private fun AssetShareRow(
    asset: Asset,
    reportFolder: String,
    sharedUris: List<Uri>,
    context: Context,
    autoReturn: Boolean,
    onImportFinished: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val assetLabel = when (asset.type) {
        AssetType.NODE -> "Nodo"
        AssetType.AMPLIFIER -> {
            val portName = asset.port?.name ?: ""
            val portIndex = asset.portIndex?.let { String.format("%02d", it) } ?: ""
            "Amplificador $portName$portIndex".trim()
        }
    }
    val assetDir = remember(reportFolder, asset) {
        MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
    }
    var files by remember(assetDir) { mutableStateOf(assetDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }

    Button(
        onClick = {
            scope.launch(Dispatchers.IO) {
                val assetDir = MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
                sharedUris.forEach { uri ->
                    MaintenanceStorage.copySharedFileToDir(context, uri, assetDir)
                }
                val updated = assetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                withContext(Dispatchers.Main) {
                    files = updated
                    onShowMessage("Archivos guardados en $assetLabel")
                    if (autoReturn) {
                        onImportFinished()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = sharedUris.isNotEmpty()
    ) {
        Icon(Icons.Default.Folder, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Guardar en $assetLabel")
    }

    if (files.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            files.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        uri,
                                        URLConnection.guessContentTypeFromName(file.name) ?: "*/*"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                                    .onFailure { onShowMessage("No se pudo abrir el archivo") }
                            }
                    )
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            file.delete()
                            val updated = assetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            withContext(Dispatchers.Main) {
                                files = updated
                                onShowMessage("Archivo eliminado")
                            }
                        }
                    }) {
                        Text("Borrar")
                    }
                }
            }
        }
    }
}

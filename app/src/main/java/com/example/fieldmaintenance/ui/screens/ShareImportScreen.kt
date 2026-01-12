package com.example.fieldmaintenance.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.AssetType
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.MaintenanceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Importar archivos compartidos") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = {
                        onShareHandled()
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }) {
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
            Text(
                text = "Ruta base: ${MaintenanceStorage.baseDir(context).path}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (sharedUris.isEmpty()) {
                Text("No hay archivos para importar.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { navController.navigate(Screen.Home.route) }) {
                    Text("Ir al inicio")
                }
                return@Column
            }

            if (reports.isEmpty()) {
                Text("No hay mantenimientos creados todavía.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { navController.navigate(Screen.Home.route) }) {
                    Text("Crear mantenimiento")
                }
                return@Column
            }

            Text(
                text = "Selecciona una carpeta para guardar los archivos:",
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
                        onShareHandled = onShareHandled,
                        onShowMessage = { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    )
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
    onShareHandled: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val repository = DatabaseProvider.getRepository()
    val assets by repository.getAssetsByReportId(report.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val reportFolder = MaintenanceStorage.reportFolderName(report.eventName, report.id)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = report.eventName.ifBlank { "Mantenimiento ${report.id.take(6)}" },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Carpeta: $reportFolder",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val reportDir = MaintenanceStorage.ensureReportDir(context, reportFolder)
                        sharedUris.forEach { uri ->
                            MaintenanceStorage.copySharedFileToDir(context, uri, reportDir)
                        }
                        onShareHandled()
                        onShowMessage("Archivos guardados en $reportFolder")
                    }
                }
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar en mantenimiento")
            }

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
                        onShareHandled = onShareHandled,
                        onShowMessage = onShowMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetShareRow(
    asset: Asset,
    reportFolder: String,
    sharedUris: List<Uri>,
    context: Context,
    onShareHandled: () -> Unit,
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

    Button(
        onClick = {
            scope.launch(Dispatchers.IO) {
                val assetDir = MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
                sharedUris.forEach { uri ->
                    MaintenanceStorage.copySharedFileToDir(context, uri, assetDir)
                }
                onShareHandled()
                onShowMessage("Archivos guardados en $assetLabel")
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Folder, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Guardar en $assetLabel")
    }
}

@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.fieldmaintenance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.fieldmaintenance.data.model.ReportPhoto
import com.example.fieldmaintenance.data.model.ReportPhotoType
import com.example.fieldmaintenance.ui.components.ReportBottomBar
import com.example.fieldmaintenance.ui.components.ReportTab
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.EmailManager
import com.example.fieldmaintenance.util.ExportManager
import com.example.fieldmaintenance.util.ImageStore
import com.example.fieldmaintenance.util.PhotoManager
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import com.example.fieldmaintenance.util.hasIncompleteAssets
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun MonitorQrScreen(navController: androidx.navigation.NavController, reportId: String) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    val repository = DatabaseProvider.getRepository()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportManager = remember { ExportManager(context, repository) }

    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    val report by viewModel.report.collectAsState()
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var hasMissingAssets by remember { mutableStateOf(false) }

    val photos by repository.getReportPhotosByReportId(reportId).collectAsState(initial = emptyList())
    val byType = photos.groupBy { it.type }

    var pendingDelete by remember { mutableStateOf<ReportPhoto?>(null) }

    // Camera handling
    var pendingCameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraType by rememberSaveable { mutableStateOf<ReportPhotoType?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraFilePath
        val type = pendingCameraType
        if (path == null || type == null) return@rememberLauncherForActivityResult

        val file = File(path)
        val ok = success || (file.exists() && file.length() > 0)
        if (!ok) {
            Toast.makeText(context, "No se pudo capturar la foto", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            upsertReportPhotoReplacingIfNeeded(
                reportId = reportId,
                type = type,
                sourceFile = file,
                repository = repository,
                context = context
            )
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val type = pendingCameraType
        if (uri == null || type == null) return@rememberLauncherForActivityResult
        scope.launch {
            val destDir = ImageStore.reportPhotoDir(context, reportId, type.name.lowercase())
            val dest = File(destDir, "${type.name.lowercase()}_${UUID.randomUUID()}.jpg")
            runCatching { ImageStore.copyUriToFile(context, uri, dest) }
                .onFailure {
                    snackbarHostState.showSnackbar("No se pudo copiar la imagen")
                    return@launch
                }
            upsertReportPhotoReplacingIfNeeded(
                reportId = reportId,
                type = type,
                sourceFile = dest,
                repository = repository,
                context = context
            )
        }
    }

    fun requestCameraAndLaunch(type: ReportPhotoType) {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            pendingCameraType = type
            cameraPermissionLauncher.launch(perm)
            return
        }
        val file = PhotoManager.createImageFile(context, "report_${type.name.lowercase()}")
        pendingCameraFilePath = file.absolutePath
        pendingCameraType = type
        val uri = PhotoManager.getFileUri(context, file)
        runCatching { takePictureLauncher.launch(uri) }
            .onFailure { Toast.makeText(context, "Error al abrir cámara", Toast.LENGTH_SHORT).show() }
    }

    fun openGallery(type: ReportPhotoType) {
        pendingCameraType = type
        pickImageLauncher.launch("image/*")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { ReportBottomBar(navController = navController, reportId = reportId, selected = ReportTab.MONITOR) },
        topBar = {
            TopAppBar(
                title = { Text("Monitoria y QR") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Home.route) { popUpTo(0) } }) {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    }
                    IconButton(onClick = { showFinalizeDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Finalizar Reporte")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReportPhotoSection(
                title = "Resultado de QR",
                maxCount = 1,
                photos = byType[ReportPhotoType.QR_RESULT].orEmpty(),
                onCamera = { requestCameraAndLaunch(ReportPhotoType.QR_RESULT) },
                onGallery = { openGallery(ReportPhotoType.QR_RESULT) },
                onDelete = { pendingDelete = it }
            )

            ReportPhotoSection(
                title = "Parámetros de Nodo",
                maxCount = 1,
                photos = byType[ReportPhotoType.NODE_PARAMS].orEmpty(),
                onCamera = { requestCameraAndLaunch(ReportPhotoType.NODE_PARAMS) },
                onGallery = { openGallery(ReportPhotoType.NODE_PARAMS) },
                onDelete = { pendingDelete = it }
            )

            ReportPhotoSection(
                title = "Otros Parámetros",
                maxCount = 2,
                photos = byType[ReportPhotoType.OTHER_PARAMS].orEmpty(),
                onCamera = { requestCameraAndLaunch(ReportPhotoType.OTHER_PARAMS) },
                onGallery = { openGallery(ReportPhotoType.OTHER_PARAMS) },
                onDelete = { pendingDelete = it }
            )
        }
    }

    if (showFinalizeDialog && report != null) {
        FinalizeReportDialog(
            onDismiss = { showFinalizeDialog = false },
            onSendEmailPdf = {
                scope.launch {
                    val pdfFile = exportManager.exportToPDF(report!!)
                    EmailManager.sendEmail(context, report!!.eventName, listOf(pdfFile))
                }
            },
            onSendEmailJson = {
                scope.launch {
                    val zipFile = exportManager.exportToZIP(report!!)
                    EmailManager.sendEmail(context, report!!.eventName, listOf(zipFile))
                }
            },
            onExportPDF = {
                scope.launch {
                    exportManager.exportPdfToDownloads(report!!)
                    snackbarHostState.showSnackbar("PDF guardado en Descargas/FieldMaintenance")
                }
            },
            onExportJSON = {
                scope.launch {
                    exportManager.exportZipToDownloads(report!!)
                    snackbarHostState.showSnackbar("ZIP guardado en Descargas/FieldMaintenance")
                }
            },
            onGoHome = {
                navController.navigate(Screen.Home.route) { popUpTo(0) }
            },
            showMissingWarning = hasMissingAssets
        )
    }

    LaunchedEffect(reportId, report) {
        hasMissingAssets = hasIncompleteAssets(context, reportId, report, repository)
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar foto") },
            text = { Text("¿Deseas eliminar esta foto?") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.deleteReportPhoto(photo)
                        pendingDelete = null
                        snackbarHostState.showSnackbar("Foto eliminada")
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ReportPhotoSection(
    title: String,
    maxCount: Int,
    photos: List<ReportPhoto>,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDelete: (ReportPhoto) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("${photos.size}/$maxCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onGallery,
                    modifier = Modifier.weight(1f),
                    enabled = photos.size < maxCount
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Galería")
                }
                OutlinedButton(
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                    enabled = photos.size < maxCount
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Cámara")
                }
            }

            if (photos.isNotEmpty()) {
                Text(
                    text = "${photos.size} foto(s) seleccionada(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photos, key = { it.id }) { p ->
                        OutlinedCard(
                            modifier = Modifier
                                .size(96.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { onDelete(p) }
                                ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Black)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = rememberAsyncImagePainter(File(p.filePath)),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun upsertReportPhotoReplacingIfNeeded(
    reportId: String,
    type: ReportPhotoType,
    sourceFile: File,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository,
    context: android.content.Context
) {
    val existing = repository.listReportPhotosByReportId(reportId).filter { it.type == type }
    val max = if (type == ReportPhotoType.OTHER_PARAMS) 2 else 1

    if (existing.size >= max) {
        // Replace the oldest for max==1; for max==2 replace the oldest.
        val toReplace = existing.minByOrNull { it.createdAt }
        if (toReplace != null) {
            repository.deleteReportPhoto(toReplace)
        }
    }

    // Ensure file is inside app report storage folder.
    val destDir = ImageStore.reportPhotoDir(context, reportId, type.name.lowercase())
    val dest = if (sourceFile.parentFile == destDir) sourceFile
    else File(destDir, sourceFile.name).also { runCatching { sourceFile.copyTo(it, overwrite = true) } }

    repository.upsertReportPhoto(
        ReportPhoto(
            reportId = reportId,
            type = type,
            filePath = dest.absolutePath,
            fileName = dest.name
        )
    )
}


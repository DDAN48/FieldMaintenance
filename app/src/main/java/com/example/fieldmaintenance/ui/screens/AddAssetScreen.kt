@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.example.fieldmaintenance.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import android.widget.Toast
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.fieldmaintenance.data.model.*
import com.example.fieldmaintenance.data.model.label
import com.example.fieldmaintenance.ui.components.AmplifierAdjustmentCard
import com.example.fieldmaintenance.ui.components.NodeAdjustmentCard
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementAssetIdKey
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementReportIdKey
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.ImageStore
import com.example.fieldmaintenance.util.PhotoManager
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import com.example.fieldmaintenance.util.MaintenanceStorage
import com.example.fieldmaintenance.util.PlanCache
import com.example.fieldmaintenance.util.PlanRepository
import com.example.fieldmaintenance.util.hasIncompleteAssets
import java.util.Locale
import java.io.FileOutputStream
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
// Note: some Material3 versions only support menuAnchor() and menuAnchor(type, enabled).
import java.io.File
import java.net.URLConnection
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssetScreen(navController: NavController, reportId: String, assetId: String? = null) {
    val context = LocalContext.current
    DatabaseProvider.init(context)
    
    val repository = DatabaseProvider.getRepository()
    val viewModel: ReportViewModel = viewModel(
        factory = ReportViewModelFactory(repository, reportId)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val workingAssetId = rememberSaveable(assetId) { assetId ?: UUID.randomUUID().toString() }
    val isEdit = assetId != null
    val report by viewModel.report.collectAsState()
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var hasMissingAssets by remember { mutableStateOf(false) }

    // Mensaje emergente al ingresar al módulo de activos (SnackBar, para que no se corte el texto)
    val syncMsgShown = rememberSaveable(workingAssetId) { mutableStateOf(false) }
    LaunchedEffect(workingAssetId) {
        if (!syncMsgShown.value) {
            snackbarHostState.showSnackbar("Recuerde sincronizar las mediciones con el nombre del activo.")
            syncMsgShown.value = true
        }
    }
    
    var assetType by remember { mutableStateOf(AssetType.NODE) }
    val frequencySaver = Saver<Frequency?, String>(
        save = { it?.name ?: "" },
        restore = { if (it.isBlank()) null else Frequency.valueOf(it) }
    )
    val amplifierModeSaver = Saver<AmplifierMode?, String>(
        save = { it?.name ?: "" },
        restore = { if (it.isBlank()) null else AmplifierMode.valueOf(it) }
    )
    val portSaver = Saver<Port?, String>(
        save = { it?.name ?: "" },
        restore = { if (it.isBlank()) null else Port.valueOf(it) }
    )
    var frequency by rememberSaveable(stateSaver = frequencySaver) { mutableStateOf<Frequency?>(null) }
    var technology by rememberSaveable { mutableStateOf<String?>(null) }
    var amplifierMode by rememberSaveable(stateSaver = amplifierModeSaver) { mutableStateOf<AmplifierMode?>(null) }
    var port by rememberSaveable(stateSaver = portSaver) { mutableStateOf<Port?>(null) }
    var portIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var hasNode by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }
    var modulePhotoCount by remember { mutableStateOf(0) }
    var opticsPhotoCount by remember { mutableStateOf(0) }
    var monitoringPhotoCount by remember { mutableStateOf(0) }
    var spectrumPhotoCount by remember { mutableStateOf(0) }
    var autoSaved by rememberSaveable(workingAssetId) { mutableStateOf(false) }

    // Amplifier adjustment (persisted per asset)
    val amplifierAdjustment by repository.getAmplifierAdjustment(workingAssetId)
        .collectAsState(initial = null)
    var currentAmplifierAdjustment by remember(workingAssetId) { mutableStateOf<com.example.fieldmaintenance.data.model.AmplifierAdjustment?>(null) }

    // Node adjustment (persisted per asset)
    val nodeAdjustment by repository.getNodeAdjustment(workingAssetId).collectAsState(initial = null)

    val techNormalized = technology?.trim()?.lowercase(Locale.getDefault()) ?: ""
    val ampAdj = currentAmplifierAdjustment ?: amplifierAdjustment
    val autoBaseOk = frequency != null
    val autoNodeOk = assetType != AssetType.NODE || technology != null
    val autoAmplifierOk = assetType != AssetType.AMPLIFIER || (amplifierMode != null && port != null && portIndex != null)
    val autoAmplifierTablesOk = if (assetType != AssetType.AMPLIFIER) true else {
        val okAdj = (frequency != null && amplifierMode != null) &&
            ampAdj != null &&
            ampAdj.inputCh50Dbmv != null &&
            ampAdj.inputCh116Dbmv != null &&
            (ampAdj.inputHighFreqMHz == 750 || ampAdj.inputHighFreqMHz == 870) &&
            ampAdj.planLowDbmv != null &&
            ampAdj.planHighDbmv != null &&
            ampAdj.outCh50Dbmv != null &&
            ampAdj.outCh70Dbmv != null &&
            ampAdj.outCh110Dbmv != null &&
            ampAdj.outCh116Dbmv != null &&
            ampAdj.outCh136Dbmv != null
        okAdj
    }

    val autoNodeAdjOk = if (assetType != AssetType.NODE) true else {
        val adj = nodeAdjustment
            ?: com.example.fieldmaintenance.data.model.NodeAdjustment(assetId = workingAssetId, reportId = reportId)
        when {
            techNormalized == "rphy" -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
            }
            techNormalized == "vccap" -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed &&
                    adj.spectrumConfirmed && adj.docsisConfirmed && frequency != null
            }
            techNormalized == "legacy" -> {
                val txOk = adj.tx1310Confirmed || adj.tx1550Confirmed
                val poOk = adj.poConfirmed
                val rxOk = !adj.rxPadSelection.isNullOrBlank()
                val measOk = adj.measurementConfirmed
                val specOk = adj.spectrumConfirmed
                txOk && poOk && rxOk && measOk && specOk && frequency != null
            }
            else -> {
                adj.nonLegacyConfirmed
            }
        }
    }

    val nodeAllowed = !(assetType == AssetType.NODE && hasNode && !isEdit)
    val autoSaveReady = autoBaseOk && autoNodeOk && autoAmplifierOk && autoAmplifierTablesOk && autoNodeAdjOk && nodeAllowed
    
    LaunchedEffect(Unit) {
        hasNode = viewModel.hasNode()
        if (!isEdit && hasNode) {
            assetType = AssetType.AMPLIFIER
        }
    }

    val planRepo = remember { PlanRepository(context.applicationContext) }
    val planCache by planRepo.cacheFlow().collectAsState(initial = PlanCache())

    fun normalizeNode(v: String): String =
        v.trim().replace(Regex("\\s+"), " ").uppercase(Locale.getDefault())

    val reportNodeName = report?.nodeName.orEmpty()
    val planRowForNode = remember(planCache, reportNodeName) {
        val key = normalizeNode(reportNodeName)
        if (key.isBlank()) null
        else planCache.rows.firstOrNull { normalizeNode(it.nodeCmts) == key }
    }

    LaunchedEffect(assetId) {
        if (assetId != null) {
            val asset = repository.getAssetById(assetId)
            if (asset != null) {
                assetType = asset.type
                frequency = when (asset.frequencyMHz) {
                    42 -> Frequency.MHz_42
                    85 -> Frequency.MHz_85
                    else -> null
                }
                technology = asset.technology
                amplifierMode = asset.amplifierMode
                port = asset.port
                portIndex = asset.portIndex
            }
        }
    }

    LaunchedEffect(reportId, report) {
        hasMissingAssets = hasIncompleteAssets(context, reportId, report, repository)
    }

    // Auto-fill technology from plan if available and not already set
    LaunchedEffect(planRowForNode, technology) {
        if (assetType == AssetType.NODE && technology == null && planRowForNode != null) {
            val planTech = planRowForNode.technology.trim()
            if (planTech.isNotBlank()) {
                val normalized = planTech.lowercase(Locale.getDefault())
                when {
                    normalized == "legacy" -> technology = "Legacy"
                    normalized == "rphy" -> technology = "RPHY"
                    normalized == "vccap" -> technology = "VCCAP"
                }
            }
        }
    }
    
    suspend fun validateAndShowErrors(): Boolean {
        attemptedSave = true

        val baseOk = frequency != null
        val nodeOk = assetType != AssetType.NODE || technology != null
        val amplifierOk = assetType != AssetType.AMPLIFIER || (amplifierMode != null && port != null && portIndex != null)

        val ampAdj = currentAmplifierAdjustment ?: amplifierAdjustment
        val amplifierTablesOk = if (assetType != AssetType.AMPLIFIER) true else {
            val okAdj = (frequency != null && amplifierMode != null) &&
                ampAdj != null &&
                ampAdj.inputCh50Dbmv != null &&
                ampAdj.inputCh116Dbmv != null &&
                (ampAdj.inputHighFreqMHz == 750 || ampAdj.inputHighFreqMHz == 870) &&
                ampAdj.planLowDbmv != null &&
                ampAdj.planHighDbmv != null &&
                ampAdj.outCh50Dbmv != null &&
                ampAdj.outCh70Dbmv != null &&
                ampAdj.outCh110Dbmv != null &&
                ampAdj.outCh116Dbmv != null &&
                ampAdj.outCh136Dbmv != null
            okAdj
        }

        // Photos required
        // We compute counts in PhotoSection via callbacks
        val techNormalized = technology?.trim()?.lowercase(Locale.getDefault()) ?: ""
        val moduleOk = if (assetType == AssetType.NODE && techNormalized == "rphy") true else modulePhotoCount == 2
        val opticsOk = if (assetType == AssetType.NODE && (techNormalized == "rphy" || techNormalized == "vccap")) true 
                      else assetType != AssetType.NODE || (opticsPhotoCount in 1..2)

        val nodeAllowed = !(assetType == AssetType.NODE && hasNode && !isEdit)

        val nodeAdjOk = if (assetType != AssetType.NODE) true else {
            val adj = nodeAdjustment
                ?: com.example.fieldmaintenance.data.model.NodeAdjustment(assetId = workingAssetId, reportId = reportId)
            val tech = technology?.trim()?.lowercase(Locale.getDefault()) 
                ?: planRowForNode?.technology?.trim()?.lowercase(Locale.getDefault()) ?: "legacy"
            when {
                tech == "rphy" -> {
                    adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
                }
                tech == "vccap" -> {
                    adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed && 
                    adj.spectrumConfirmed && adj.docsisConfirmed && frequency != null
                }
                tech == "legacy" -> {
                    val txOk = adj.tx1310Confirmed || adj.tx1550Confirmed
                    val poOk = adj.poConfirmed
                    val rxOk = !adj.rxPadSelection.isNullOrBlank()
                    val measOk = adj.measurementConfirmed
                    val specOk = adj.spectrumConfirmed
                    txOk && poOk && rxOk && measOk && specOk && frequency != null
                }
                else -> {
                    adj.nonLegacyConfirmed
                }
            }
        }

        val ok = baseOk && nodeOk && amplifierOk && amplifierTablesOk && moduleOk && opticsOk && nodeAllowed && nodeAdjOk
        if (!ok) {
            if (assetType == AssetType.AMPLIFIER && !amplifierTablesOk) {
                snackbarHostState.showSnackbar("Completa todas las tablas del ajuste del amplificador")
            } else if (assetType == AssetType.NODE && planRowForNode != null && !nodeAdjOk) {
                snackbarHostState.showSnackbar("Completa el Ajuste de Nodo")
            } else {
                snackbarHostState.showSnackbar("Faltan campos o fotos obligatorias")
            }
        }
        return ok
    }

    LaunchedEffect(
        autoSaveReady,
        assetType,
        frequency,
        technology,
        amplifierMode,
        port,
        portIndex,
        nodeAdjustment,
        currentAmplifierAdjustment,
        amplifierAdjustment
    ) {
        if (!autoSaveReady) return@LaunchedEffect
        val asset = Asset(
            id = workingAssetId,
            reportId = reportId,
            type = assetType,
            frequencyMHz = frequency?.mhz ?: 0,
            amplifierMode = amplifierMode,
            port = port,
            portIndex = portIndex,
            technology = if (assetType == AssetType.NODE) technology else null
        )
        if (isEdit || autoSaved) {
            viewModel.updateAsset(asset)
        } else {
            viewModel.addAsset(asset)
        }
        autoSaved = true
        withContext(Dispatchers.IO) {
            val reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId)
            MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Editar Activo" else "Agregar Activo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    }
                    IconButton(onClick = { showFinalizeDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Finalizar Reporte")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            if (validateAndShowErrors()) {
                                if (assetType == AssetType.AMPLIFIER) {
                                    val existing = repository.listAssetsByReportId(reportId)
                                    val dup = existing.any {
                                        it.type == AssetType.AMPLIFIER &&
                                            it.id != workingAssetId &&
                                            it.port == port &&
                                            it.portIndex == portIndex
                                    }
                                    if (dup) {
                                        snackbarHostState.showSnackbar("Activo ya existe: ${port?.name}${String.format("%02d", portIndex)}")
                                        return@launch
                                    }
                                }
                                val asset = Asset(
                                    id = workingAssetId,
                                    reportId = reportId,
                                    type = assetType,
                                    frequencyMHz = frequency!!.mhz,
                                    amplifierMode = amplifierMode,
                                    port = port,
                                    portIndex = portIndex
                                )
                                if (isEdit) viewModel.updateAsset(asset) else viewModel.addAsset(asset)
                                withContext(Dispatchers.IO) {
                                    val reportFolder = MaintenanceStorage.reportFolderName(
                                        report?.eventName,
                                        reportId
                                    )
                                    MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
                                }
                                snackbarHostState.showSnackbar("Guardado")
                            } else {
                                snackbarHostState.showSnackbar("Completa los datos del activo")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tipo de Activo + Frecuencia en la misma línea (desplegables)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tipo de Activo
                var expandedType by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = if (assetType == AssetType.NODE) "Nodo" else "Amplificador",
                        onValueChange = {},
                        readOnly = true,
                        enabled = true,
                        label = { Text("Tipo de Activo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedType,
                        onDismissRequest = { expandedType = false }
                    ) {
                        if (!hasNode) {
                            DropdownMenuItem(
                                text = { Text("Nodo") },
                                onClick = {
                                    assetType = AssetType.NODE
                                    expandedType = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Amplificador") },
                            onClick = {
                                assetType = AssetType.AMPLIFIER
                                expandedType = false
                            }
                        )
                    }
                }

                // Frecuencia
                var expandedFreq by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedFreq,
                    onExpandedChange = { expandedFreq = !expandedFreq },
                    modifier = Modifier.width(130.dp)
                ) {
                    OutlinedTextField(
                        value = frequency?.let { "${it.mhz} MHz" } ?: "Seleccionar",
                        onValueChange = {},
                        readOnly = true,
                        enabled = true,
                        label = { Text("Frec módulo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFreq)
                        },
                        isError = attemptedSave && frequency == null,
                        supportingText = {
                            if (attemptedSave && frequency == null) Text("Obligatorio")
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFreq,
                        onDismissRequest = { expandedFreq = false }
                    ) {
                        listOf(Frequency.MHz_42, Frequency.MHz_85).forEach { freq ->
                            DropdownMenuItem(
                                text = { Text("${freq.mhz} MHz") },
                                onClick = {
                                    frequency = freq
                                    expandedFreq = false
                                }
                            )
                        }
                    }
                }
            }

            // Tecnología (solo para Nodo)
            if (assetType == AssetType.NODE) {
                var expandedTech by remember { mutableStateOf(false) }
                val techOptions = listOf("Legacy", "RPHY", "VCCAP")
                ExposedDropdownMenuBox(
                    expanded = expandedTech,
                    onExpandedChange = { expandedTech = !expandedTech },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = technology ?: "Seleccionar",
                        onValueChange = {},
                        readOnly = true,
                        enabled = true,
                        label = { Text("Tecnología") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTech)
                        },
                        isError = attemptedSave && technology == null,
                        supportingText = {
                            if (attemptedSave && technology == null) Text("Obligatorio")
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTech,
                        onDismissRequest = { expandedTech = false }
                    ) {
                        techOptions.forEach { tech ->
                            DropdownMenuItem(
                                text = { Text(tech) },
                                onClick = {
                                    technology = tech
                                    expandedTech = false
                                }
                            )
                        }
                    }
                }
            }

            // Submódulo: Ajuste de Nodo (después de Tipo + Frecuencia + Tecnología)
            if (assetType == AssetType.NODE) {
                NodeAdjustmentCard(
                    assetId = workingAssetId,
                    reportId = reportId,
                    nodeName = reportNodeName,
                    frequency = frequency,
                    technology = technology,
                    planRow = planRowForNode,
                    adjustment = nodeAdjustment,
                    showRequiredErrors = attemptedSave,
                    onPersist = { adj ->
                        scope.launch { repository.upsertNodeAdjustment(adj) }
                    }
                )
            }
            
            // Amplificador: 2da línea = Tipo (HGD/HGBT/LE), 3ra línea = Puerto + N°
            if (assetType == AssetType.AMPLIFIER) {
                // 2da línea: Tipo
                var expandedMode by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedMode,
                    onExpandedChange = { expandedMode = !expandedMode },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (amplifierMode) {
                            AmplifierMode.HGD -> AmplifierMode.HGD.label
                            AmplifierMode.HGDT -> AmplifierMode.HGDT.label
                            AmplifierMode.LE -> AmplifierMode.LE.label
                            null -> "Seleccionar"
                        },
                        onValueChange = {},
                        readOnly = true,
                        enabled = true,
                        label = { Text("Tipo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode)
                        },
                        isError = attemptedSave && amplifierMode == null,
                        supportingText = {
                            if (attemptedSave && amplifierMode == null) Text("Obligatorio")
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedMode,
                        onDismissRequest = { expandedMode = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("HGD") },
                            onClick = {
                                amplifierMode = AmplifierMode.HGD
                                expandedMode = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("HGBT") },
                            onClick = {
                                amplifierMode = AmplifierMode.HGDT
                                expandedMode = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("LE") },
                            onClick = {
                                amplifierMode = AmplifierMode.LE
                                expandedMode = false
                            }
                        )
                    }
                }

                // 3ra línea: Puerto + N°
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var expandedPort by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedPort,
                        onExpandedChange = { expandedPort = !expandedPort },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = port?.name ?: "Puerto",
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
                            label = { Text("Puerto") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPort)
                            },
                            isError = attemptedSave && port == null,
                            supportingText = {
                                if (attemptedSave && port == null) Text("Obligatorio")
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPort,
                            onDismissRequest = { expandedPort = false }
                        ) {
                            Port.values().forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        port = p
                                        expandedPort = false
                                    }
                                )
                            }
                        }
                    }

                    var expandedIndex by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedIndex,
                        onExpandedChange = { expandedIndex = !expandedIndex },
                        modifier = Modifier.width(120.dp)
                    ) {
                        val labelValue = portIndex?.let { String.format("%02d", it) } ?: "N°"
                        OutlinedTextField(
                            value = labelValue,
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
                            label = { Text("N°") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIndex)
                            },
                            isError = attemptedSave && portIndex == null,
                            supportingText = {
                                if (attemptedSave && portIndex == null) Text("Obligatorio")
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedIndex,
                            onDismissRequest = { expandedIndex = false }
                        ) {
                            (1..4).forEach { idx ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", idx)) },
                                    onClick = {
                                        portIndex = idx
                                        expandedIndex = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Submódulo: Ajuste de Amplificador (antes de Fotos)
            if (assetType == AssetType.AMPLIFIER) {
                if (frequency == null || amplifierMode == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = if (attemptedSave) androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error
                        ) else null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Ajuste de Amplificador", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(
                                "Selecciona Frecuencia y Tipo (HGBT/HGD/LE) para habilitar el ajuste.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                    }
                } else {
                    AmplifierAdjustmentCard(
                        assetId = workingAssetId,
                        bandwidth = frequency,
                        amplifierMode = amplifierMode,
                        initial = amplifierAdjustment,
                        showRequiredErrors = attemptedSave,
                        onCurrentChange = { currentAmplifierAdjustment = it },
                        onPersist = { adj -> repository.upsertAmplifierAdjustment(adj.copy(assetId = workingAssetId)) }
                    )
                }
            }
            
            // Fotos
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Fotos",
                style = MaterialTheme.typography.titleMedium
            )

            val assetDisplayName = remember(reportNodeName, assetType, port, portIndex) {
                val baseNodeName = reportNodeName.ifBlank { "Nodo" }
                if (assetType == AssetType.NODE) {
                    baseNodeName
                } else {
                    val code = if (port != null && portIndex != null) {
                        "${port?.name}${String.format("%02d", portIndex)}"
                    } else {
                        "SIN-COD"
                    }
                    "$baseNodeName $code".trim()
                }
            }
            val eventName = remember(report?.eventName) {
                report?.eventName?.trim().orEmpty().ifBlank { "Sin evento" }
            }
            
            // Foto del Módulo (no para RPHY)
            if (assetType != AssetType.NODE || technology != "RPHY") {
                PhotoSection(
                    title = "Foto del Módulo y Tapa",
                    reportId = reportId,
                    assetId = workingAssetId,
                    photoType = PhotoType.MODULE,
                    assetLabel = assetDisplayName,
                    eventName = eventName,
                    repository = repository,
                    minRequired = if (assetType == AssetType.NODE && technology != "RPHY") 2 else 0,
                    showRequiredError = attemptedSave && (assetType != AssetType.NODE || technology != "RPHY"),
                    maxAllowed = 2,
                    onCountChange = { modulePhotoCount = it }
                )
            }
            
            // Fotos adicionales según tipo
            // Foto TX y RX con pads (no para RPHY ni VCCAP)
            if (assetType == AssetType.NODE && technology != "RPHY" && technology != "VCCAP") {
                PhotoSection(
                    title = "Foto TX  y RX con pads",
                    reportId = reportId,
                    assetId = workingAssetId,
                    photoType = PhotoType.OPTICS,
                    assetLabel = assetDisplayName,
                    eventName = eventName,
                    repository = repository,
                    minRequired = 1,
                    showRequiredError = attemptedSave,
                    maxAllowed = 2,
                    onCountChange = { opticsPhotoCount = it }
                )
            }

            if (assetType == AssetType.NODE) {
                PhotoSection(
                    title = "Foto de monitoria de PO directa y retorno",
                    reportId = reportId,
                    assetId = workingAssetId,
                    photoType = PhotoType.MONITORING,
                    assetLabel = assetDisplayName,
                    eventName = eventName,
                    repository = repository,
                    minRequired = 0,
                    showRequiredError = false,
                    maxAllowed = 2,
                    onCountChange = { monitoringPhotoCount = it }
                )
            }
            
            // Fotos de Inyección de portadoras (no para RPHY)
            if (assetType != AssetType.NODE || technology != "RPHY") {
                // Para NODO con Legacy o VCCAP: 4 fotos, para otros: 3 fotos
                val maxSpectrumPhotos = if (assetType == AssetType.NODE && (technology == "Legacy" || technology == "VCCAP")) 4 else 3
                PhotoSection(
                    title = "Fotos de Inyección de portadoras por puerto",
                    reportId = reportId,
                    assetId = workingAssetId,
                    photoType = PhotoType.SPECTRUM,
                    assetLabel = assetDisplayName,
                    eventName = eventName,
                    repository = repository,
                    minRequired = 0,
                    showRequiredError = false,
                    maxAllowed = maxSpectrumPhotos,
                    onCountChange = { spectrumPhotoCount = it }
                )
            }
            
            if (assetType == AssetType.NODE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Solo se permite un nodo por zona.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (autoSaved) {
                Spacer(modifier = Modifier.height(8.dp))
                AssetFileSection(
                    context = context,
                    navController = navController,
                    reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId),
                    asset = Asset(
                        id = workingAssetId,
                        reportId = reportId,
                        type = assetType,
                        frequencyMHz = frequency?.mhz ?: 0,
                        amplifierMode = amplifierMode,
                        port = port,
                        portIndex = portIndex,
                        technology = if (assetType == AssetType.NODE) technology else null
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        val asset = Asset(
                            id = workingAssetId,
                            reportId = reportId,
                            type = assetType,
                            frequencyMHz = (frequency?.mhz ?: 0),
                            technology = if (assetType == AssetType.NODE) technology else null,
                            amplifierMode = amplifierMode,
                            port = port,
                            portIndex = portIndex
                        )
                        if (validateAndShowErrors()) {
                            if (assetType == AssetType.AMPLIFIER) {
                                val existing = repository.listAssetsByReportId(reportId)
                                val dup = existing.any {
                                    it.type == AssetType.AMPLIFIER &&
                                        it.id != workingAssetId &&
                                        it.port == port &&
                                        it.portIndex == portIndex
                                }
                                if (dup) {
                                    snackbarHostState.showSnackbar("Activo ya existe: ${port?.name}${String.format("%02d", portIndex)}")
                                    return@launch
                                }
                            }
                            val fixedAsset = asset.copy(frequencyMHz = frequency!!.mhz)
                            if (isEdit) viewModel.updateAsset(asset) else viewModel.addAsset(asset)
                            navController.navigate(Screen.AssetSummary.createRoute(reportId))
                        } else {
                            snackbarHostState.showSnackbar("Completa los datos del activo")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = if (assetType == AssetType.AMPLIFIER) {
                    amplifierMode != null && port != null && portIndex != null
                } else {
                    true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (assetType == AssetType.NODE) "Agregar Nodo" else "+ Agregar Activo")
            }
        }
    }

    if (showFinalizeDialog && report != null) {
        val exportManager = remember { com.example.fieldmaintenance.util.ExportManager(context, repository) }
        val r = report ?: return
        FinalizeReportDialog(
            onDismiss = { showFinalizeDialog = false },
            onSendEmailPackage = {
                scope.launch {
                    val bundleFile = exportManager.exportToBundleZip(r)
                    com.example.fieldmaintenance.util.EmailManager.sendEmail(context, r.eventName, listOf(bundleFile))
                }
            },
            onExportPackage = {
                scope.launch {
                    exportManager.exportBundleToDownloads(r)
                    snackbarHostState.showSnackbar("ZIP guardado en Descargas/FieldMaintenance")
                }
            },
            onGoHome = {
                navController.navigate(Screen.Home.route) { popUpTo(0) }
            },
            showMissingWarning = hasMissingAssets
        )
    }
}

@Composable
fun PhotoSection(
    title: String,
    reportId: String,
    assetId: String,
    photoType: PhotoType,
    assetLabel: String,
    eventName: String,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository,
    minRequired: Int,
    showRequiredError: Boolean,
    maxAllowed: Int,
    onCountChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by repository
        .getPhotosByAssetId(assetId)
        .map { list -> list.filter { it.photoType == photoType } }
        .collectAsState(initial = emptyList())

    LaunchedEffect(photos.size) { onCountChange(photos.size) }

    val isMissingRequired = showRequiredError && minRequired > 0 && photos.size < minRequired
    val isOverMax = photos.size > maxAllowed
    val isAtMax = photos.size >= maxAllowed
    val allowsGallery = photoType != PhotoType.MODULE && photoType != PhotoType.OPTICS

    var photoToDelete by remember { mutableStateOf<com.example.fieldmaintenance.data.model.Photo?>(null) }
    var photoToPreview by remember { mutableStateOf<com.example.fieldmaintenance.data.model.Photo?>(null) }
    // These must survive configuration changes (e.g., rotating to landscape opens camera and Activity may recreate)
    var pendingCameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Launcher para seleccionar foto de la galería
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val dir = ImageStore.assetPhotoDir(
                        context = context,
                        reportId = reportId,
                        assetId = assetId,
                        photoType = photoType.name.lowercase()
                    )
                    val dest = File(dir, "gallery_${System.currentTimeMillis()}.jpg")
                    ImageStore.copyUriToFile(context, it, dest)
                    repository.insertPhoto(
                        com.example.fieldmaintenance.data.model.Photo(
                            assetId = assetId,
                            photoType = photoType,
                            filePath = dest.absolutePath,
                            fileName = dest.name
                        )
                    )
                } catch (_: Exception) {
                    // ignore for now
                }
            }
        }
    }

    // Launcher para tomar foto con la cámara
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraFilePath
        if (path.isNullOrBlank()) return@rememberLauncherForActivityResult

        val file = File(path)
        // Some camera apps return success=false but still write the file; treat as success if file exists and has bytes.
        val ok = success || (file.exists() && file.length() > 0)
        if (!ok) return@rememberLauncherForActivityResult

        scope.launch {
            val labelInfo = buildPhotoLabel(context, file, assetLabel, eventName)
            if (labelInfo != null) {
                annotateImageWithLabel(file, labelInfo)
            }
            repository.insertPhoto(
                com.example.fieldmaintenance.data.model.Photo(
                    assetId = assetId,
                    photoType = photoType,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    latitude = labelInfo?.latitude,
                    longitude = labelInfo?.longitude
                )
            )
            // clear pending
            pendingCameraFilePath = null
            pendingCameraUriString = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = pendingCameraUriString?.let { Uri.parse(it) }
            if (uri != null) {
                runCatching { cameraLauncher.launch(uri) }
                    .onFailure { Toast.makeText(context, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show() }
            }
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para crear archivo temporal para la cámara
    val takePicture = {
        try {
            val dir = ImageStore.assetPhotoDir(
                context = context,
                reportId = reportId,
                assetId = assetId,
                photoType = photoType.name.lowercase()
            )
            val photoFile = File(dir, "camera_${System.currentTimeMillis()}.jpg")
            val photoUri = PhotoManager.getFileUri(context, photoFile)
            pendingCameraFilePath = photoFile.absolutePath
            pendingCameraUriString = photoUri.toString()

            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val locationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!locationGranted) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (granted) {
                cameraLauncher.launch(photoUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Error preparando la cámara", Toast.LENGTH_SHORT).show()
        }
    }
    
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMissingRequired || isOverMax) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Black
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
                // Conteo estilo 0/2, 1/3, etc.
                Text(
                    text = "${photos.size}/$maxAllowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isMissingRequired) {
                Text(
                    text = "Obligatorio: mínimo $minRequired foto(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isOverMax) {
                Text(
                    text = "Máximo permitido: $maxAllowed foto(s) (borra las extra)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allowsGallery) {
                    OutlinedButton(
                        onClick = { if (!isAtMax) galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !isAtMax
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería")
                    }
                }
                
                OutlinedButton(
                    onClick = { if (!isAtMax) takePicture() },
                    modifier = Modifier.weight(1f),
                    enabled = !isAtMax
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cámara")
                }
            }
            
            if (photos.isNotEmpty()) {
            Text(
                text = "${photos.size} foto(s) seleccionada(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos) { photo ->
                    Card(
                        modifier = Modifier
                            .size(72.dp)
                            .combinedClickable(
                                onClick = { photoToPreview = photo },
                                onLongClick = { photoToDelete = photo }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = rememberAsyncImagePainter(File(photo.filePath)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            }
        }
    }

    photoToPreview?.let { photo ->
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            offsetX += panChange.x
            offsetY += panChange.y
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { photoToPreview = null }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainter(File(photo.filePath)),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .transformable(transformState)
                    )
                    TextButton(
                        onClick = { photoToPreview = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
    
    photoToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Borrar foto") },
            text = { Text("¿Deseas borrar esta foto?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { File(target.filePath).delete() }
                        repository.deletePhoto(target)
                        photoToDelete = null
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

private data class PhotoLabelInfo(
    val lines: List<String>,
    val mapBitmap: Bitmap?,
    val latitude: Double?,
    val longitude: Double?
)

private suspend fun buildPhotoLabel(
    context: Context,
    file: File,
    assetLabel: String,
    eventName: String
): PhotoLabelInfo? {
    val latLong = readExifLatLongWithRetry(file)
    val fallbackLocation = if (latLong == null) {
        getLastKnownLocation(context)
    } else {
        null
    }
    val latitude = latLong?.getOrNull(0) ?: fallbackLocation?.latitude
    val longitude = latLong?.getOrNull(1) ?: fallbackLocation?.longitude
    val address = if (latitude != null && longitude != null) {
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            }.getOrNull()
        }
    } else {
        null
    }
    val coords = if (latitude != null && longitude != null) {
        String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
    } else {
        null
    }
    val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
    val dateTime = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
    val formattedDateTime = dateTime?.let { raw ->
        runCatching {
            val parsed = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).parse(raw)
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(parsed ?: Date())
        }.getOrNull()
    } ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

    val headerLine = "$assetLabel - $eventName"
    val locationLine = when {
        !address.isNullOrBlank() && coords != null -> "$address ($coords)"
        coords != null -> coords
        else -> "Ubicación no disponible"
    }
    val timeLine = formattedDateTime
    val mapBitmap = if (latitude != null && longitude != null) {
        loadStaticMap(latitude, longitude)
    } else {
        null
    }
    return PhotoLabelInfo(
        lines = listOf(headerLine, locationLine, timeLine),
        mapBitmap = mapBitmap,
        latitude = latitude,
        longitude = longitude
    )
}

private suspend fun readExifLatLongWithRetry(file: File): DoubleArray? {
    repeat(6) { attempt ->
        val latLong = runCatching { ExifInterface(file.absolutePath).latLong }.getOrNull()
        if (latLong != null) return latLong
        if (attempt < 5) {
            delay(400)
        }
    }
    return null
}

private fun annotateImageWithLabel(file: File, labelInfo: PhotoLabelInfo) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    val padding = (mutable.width * 0.012f).coerceIn(6f, 20f)
    val textSize = (mutable.width * 0.055f).coerceIn(26f, 68f)
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.textSize = textSize
    }
    val maxWidth = (mutable.width - padding * 2).toInt()
    val labelText = labelInfo.lines.joinToString("\n")
    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder
            .obtain(labelText, 0, labelText.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(
            labelText,
            textPaint,
            maxWidth,
            Layout.Alignment.ALIGN_CENTER,
            1f,
            0f,
            false
        )
    }
    val backgroundPaint = Paint().apply {
        color = android.graphics.Color.argb(170, 0, 0, 0)
    }
    val mapHeight = (mutable.height * 0.1f).roundToInt()
    val mapWidth = (mapHeight * 1.3f).roundToInt()
    val totalHeight = maxOf(layout.height + (padding * 2).toInt(), mapHeight + (padding * 2).toInt())
    val top = (mutable.height - totalHeight).coerceAtLeast(0)
    canvas.drawRect(
        0f,
        top.toFloat(),
        mutable.width.toFloat(),
        mutable.height.toFloat(),
        backgroundPaint
    )
    labelInfo.mapBitmap?.let { map ->
        val destWidth = mapWidth.coerceAtMost(mutable.width)
        val destHeight = mapHeight.coerceAtMost(mutable.height)
        val left = padding
        val topMap = top + ((totalHeight - destHeight) / 2f)
        val dest = android.graphics.Rect(
            left.toInt(),
            topMap.toInt(),
            (left + destWidth).toInt(),
            (topMap + destHeight).toInt()
        )
        canvas.drawBitmap(map, null, dest, null)
    }
    canvas.save()
    val textX = (mutable.width - layout.width) / 2f
    canvas.translate(textX, top + padding)
    layout.draw(canvas)
    canvas.restore()

    FileOutputStream(file).use { out ->
        mutable.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
}

private fun getLastKnownLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    return providers.asSequence()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .firstOrNull()
}

private suspend fun loadStaticMap(latitude: Double, longitude: Double): Bitmap? {
    return withContext(Dispatchers.IO) {
        val url = "https://staticmap.openstreetmap.de/staticmap.php?center=$latitude,$longitude&zoom=15&size=300x200&markers=$latitude,$longitude,red-pushpin"
        runCatching {
            URL(url).openStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
}

@Composable
private fun AssetFileSection(
    context: Context,
    navController: NavController,
    reportFolder: String,
    asset: Asset
) {
    val assetDir = remember(reportFolder, asset) {
        MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
    }
    var files by remember(assetDir) { mutableStateOf(assetDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    val viaviIntent = remember {
        context.packageManager.getLaunchIntentForPackage("com.viavisolutions.mobiletech")
    }

    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Carga de Mediciones",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                    )
                }
            }
            if (isExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (viaviIntent != null) {
                            navController.currentBackStackEntry?.savedStateHandle?.apply {
                                set(PendingMeasurementReportIdKey, asset.reportId)
                                set(PendingMeasurementAssetIdKey, asset.id)
                            }
                            runCatching { context.startActivity(viaviIntent) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "No se pudo abrir Viavi",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                context,
                                "Viavi (mobiletech) no está instalada",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agregar Mediciones")
                    }
                }

                if (files.isEmpty()) {
                    Text("No hay archivos adjuntos.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        files.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, URLConnection.guessContentTypeFromName(file.name) ?: "*/*")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        "No se pudo abrir el archivo",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        },
                                        onLongClick = { fileToDelete = file }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = file.name,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Mover a papelera") },
            text = { Text("¿Esta seguro de mover este archivo a la papelera?") },
            confirmButton = {
                TextButton(onClick = {
                    val target = fileToDelete
                    fileToDelete = null
                    if (target != null) {
                        scope.launch(Dispatchers.IO) {
                            MaintenanceStorage.moveMeasurementFileToTrash(context, target)
                            val updated = assetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            withContext(Dispatchers.Main) {
                                files = updated
                            }
                        }
                    }
                }) { Text("Mover") }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

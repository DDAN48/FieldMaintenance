@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.example.fieldmaintenance.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
import com.example.fieldmaintenance.ui.navigation.PendingMeasurementAssetTypeKey
import com.example.fieldmaintenance.ui.navigation.Screen
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModel
import com.example.fieldmaintenance.ui.viewmodel.ReportViewModelFactory
import com.example.fieldmaintenance.util.DatabaseProvider
import com.example.fieldmaintenance.util.CiscoHfcAmpCalculator
import com.example.fieldmaintenance.util.ImageStore
import com.example.fieldmaintenance.util.PhotoManager
import com.example.fieldmaintenance.util.SettingsStore
import com.example.fieldmaintenance.util.AppSettings
import com.example.fieldmaintenance.util.MaintenanceStorage
import com.example.fieldmaintenance.util.PlanCache
import com.example.fieldmaintenance.util.PlanRepository
import com.example.fieldmaintenance.util.hasIncompleteAssets
import java.util.Locale
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
// Note: some Material3 versions only support menuAnchor() and menuAnchor(type, enabled).
import java.io.File
import java.net.URLConnection
import java.util.UUID
import org.json.JSONObject

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
    var collapseAdjustmentsSignal by rememberSaveable { mutableStateOf(0) }
    val triggerAdjustmentsCollapse = { collapseAdjustmentsSignal += 1 }

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
                    collapseSignal = collapseAdjustmentsSignal,
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
                        collapseSignal = collapseAdjustmentsSignal,
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
                    onCountChange = {
                        if (it > modulePhotoCount) {
                            triggerAdjustmentsCollapse()
                        }
                        modulePhotoCount = it
                    }
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
                    onCountChange = {
                        if (it > opticsPhotoCount) {
                            triggerAdjustmentsCollapse()
                        }
                        opticsPhotoCount = it
                    }
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
                    onCountChange = {
                        if (it > monitoringPhotoCount) {
                            triggerAdjustmentsCollapse()
                        }
                        monitoringPhotoCount = it
                    }
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
                    onCountChange = {
                        if (it > spectrumPhotoCount) {
                            triggerAdjustmentsCollapse()
                        }
                        spectrumPhotoCount = it
                    }
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

            val techNormalized = technology?.trim()?.lowercase(Locale.getDefault()) ?: ""
            val isRphyNode = assetType == AssetType.NODE && techNormalized == "rphy"

            if (autoSaved && !isRphyNode) {
                Spacer(modifier = Modifier.height(8.dp))
                AssetFileSection(
                    context = context,
                    navController = navController,
                    repository = repository,
                    reportFolder = MaintenanceStorage.reportFolderName(report?.eventName, reportId),
                    onInteraction = triggerAdjustmentsCollapse,
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

private fun loadDiscardedLabels(file: File): Set<String> {
    if (!file.exists()) return emptySet()
    return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

private fun saveDiscardedLabels(file: File, labels: Set<String>) {
    file.writeText(labels.joinToString(separator = "\n"))
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
    var latestLocation by remember { mutableStateOf<Location?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
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
            val labelInfo = buildPhotoLabel(
                context = context,
                file = file,
                assetLabel = assetLabel,
                eventName = eventName,
                overrideLocation = latestLocation
            )
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
        locationPermissionGranted = granted
        if (!granted) {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = LocationListener { location ->
            latestLocation = location
        }
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, 1000L, 1f, listener, Looper.getMainLooper())
            }
        }
        onDispose {
            runCatching { manager.removeUpdates(listener) }
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
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            val maxX = ((containerSize.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
            val maxY = ((containerSize.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
            offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
        }
        LaunchedEffect(scale) {
            if (scale <= 1.01f) {
                offsetX = 0f
                offsetY = 0f
            }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size -> containerSize = size }
                            .transformable(transformState)
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
                        )
                    }
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
    eventName: String,
    overrideLocation: Location?
): PhotoLabelInfo? {
    val latLong = readExifLatLongWithRetry(file)
    val bestLocation = selectBestLocation(latLong, overrideLocation, getLastKnownLocation(context))
    if (latLong == null && bestLocation != null) {
        applyLocationToExif(file, bestLocation)
    }
    val latitude = latLong?.getOrNull(0) ?: bestLocation?.latitude
    val longitude = latLong?.getOrNull(1) ?: bestLocation?.longitude
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

private fun selectBestLocation(
    exifLatLong: DoubleArray?,
    liveLocation: Location?,
    fallbackLocation: Location?
): Location? {
    if (exifLatLong != null) return null
    val liveOk = liveLocation?.accuracy?.let { it <= 30f } == true
    return when {
        liveOk -> liveLocation
        fallbackLocation != null -> fallbackLocation
        else -> null
    }
}

private fun applyLocationToExif(file: File, location: Location) {
    runCatching {
        val exif = ExifInterface(file.absolutePath)
        exif.setGpsInfo(location)
        exif.saveAttributes()
    }
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
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) {
        return null
    }
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

private fun loadMeasurementRules(context: Context): JSONObject? {
    return runCatching {
        context.assets.open("measurement_validation.json").use { input ->
            val text = input.bufferedReader().use { it.readText() }
            JSONObject(text)
        }
    }.getOrNull()
}

private fun equipmentKeyFor(asset: Asset): String {
    return when (asset.frequencyMHz) {
        42 -> "42_55"
        85 -> "85_105"
        else -> "unknown"
    }
}

private data class ChannelRow(
    val channel: Int?,
    val frequencyMHz: Double?,
    val levelDbmv: Double?,
    val merDb: Double?,
    val berPre: Double?,
    val berPost: Double?,
    val icfrDb: Double?
)

private fun collectChannelRows(json: Any?): List<ChannelRow> {
    val rows = mutableListOf<ChannelRow>()

    fun parseNumber(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> {
            val normalized = value.replace(",", ".")
            val match = Regex("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?").find(normalized)?.value
            match?.toDoubleOrNull()
        }
        else -> null
    }

    fun parseInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    fun collectFromMap(map: Map<String, Any?>) {
        val channel = parseInt(map["channel"] ?: map["canal"])
        val frequency = parseNumber(map["frequency"] ?: map["frecuencia"] ?: map["frequencymhz"] ?: map["freqmhz"])
        val level = parseNumber(map["level"] ?: map["nivel"] ?: map["leveldbmv"] ?: map["niveldbmv"])
        val mer = parseNumber(map["mer"])
        val berPre = parseNumber(map["berpre"] ?: map["ber_pre"] ?: map["berprevio"])
        val berPost = parseNumber(map["berpost"] ?: map["ber_post"] ?: map["berposterior"])
        val icfr = parseNumber(map["icfr"])
        if (channel != null || frequency != null || level != null || mer != null || berPre != null || berPost != null || icfr != null) {
            rows.add(
                ChannelRow(
                    channel = channel,
                    frequencyMHz = frequency,
                    levelDbmv = level,
                    merDb = mer,
                    berPre = berPre,
                    berPost = berPost,
                    icfrDb = icfr
                )
            )
        }
    }

    fun collectFromDigitalFullScan(results: JSONObject) {
        val tableData = results.optJSONObject("08_digitalFullScanResults")?.optJSONArray("tableData") ?: return
        for (i in 0 until tableData.length()) {
            val row = tableData.optJSONArray(i) ?: continue
            if (row.length() < 9) continue
            val channelValue = row.optJSONObject(0)?.optString("value")
            val frequencyValue = row.optJSONObject(1)?.optString("value")
            val levelValue = row.optJSONObject(2)?.optString("value")
            val merValue = row.optJSONObject(3)?.optString("value")
            val berPreValue = row.optJSONObject(4)?.optString("value")
            val berPostValue = row.optJSONObject(5)?.optString("value")
            val icfrValue = row.optJSONObject(8)?.optString("value")
            val channel = parseInt(channelValue)
            val frequency = parseNumber(frequencyValue)
            val level = parseNumber(levelValue)
            val mer = parseNumber(merValue)
            val berPre = parseNumber(berPreValue)
            val berPost = parseNumber(berPostValue)
            val icfr = parseNumber(icfrValue)
            if (channel != null || frequency != null || level != null || mer != null || berPre != null || berPost != null || icfr != null) {
                rows.add(
                    ChannelRow(
                        channel = channel,
                        frequencyMHz = frequency,
                        levelDbmv = level,
                        merDb = mer,
                        berPre = berPre,
                        berPost = berPost,
                        icfrDb = icfr
                    )
                )
            }
        }
    }

    fun collectFromUpstreamTable(results: JSONObject) {
        fun collectTable(table: JSONObject) {
            val tableData = table.optJSONArray("tableData") ?: return
            for (i in 0 until tableData.length()) {
                val row = tableData.optJSONArray(i) ?: continue
                if (row.length() < 3) continue
                val channelValue = row.optJSONObject(0)?.optString("value")
                val frequencyValue = row.optJSONObject(1)?.optString("value")
                val levelValue = row.optJSONObject(2)?.optString("value")
                val channel = parseInt(channelValue)
                val frequency = parseNumber(frequencyValue)
                val level = parseNumber(levelValue)
                if (channel != null || frequency != null || level != null) {
                    rows.add(
                        ChannelRow(
                            channel = channel,
                            frequencyMHz = frequency,
                            levelDbmv = level,
                            merDb = null,
                            berPre = null,
                            berPost = null,
                            icfrDb = null
                        )
                    )
                }
            }
        }

        val keys = results.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.lowercase(Locale.getDefault()).endsWith("upstreamtable")) {
                results.optJSONObject(key)?.let { collectTable(it) }
            }
        }
    }

    fun collectFromSingleFullScan(results: JSONObject) {
        val tableData = results.optJSONObject("0A_singleFullScanResults")?.optJSONArray("tableData") ?: return
        for (i in 0 until tableData.length()) {
            val row = tableData.optJSONArray(i) ?: continue
            if (row.length() < 3) continue
            val channelValue = row.optJSONObject(0)?.optString("value")
            val frequencyValue = row.optJSONObject(1)?.optString("value")
            val levelValue = row.optJSONObject(2)?.optString("value")
            val channel = parseInt(channelValue)
            val frequency = parseNumber(frequencyValue)
            val level = parseNumber(levelValue)
            if (channel != null || frequency != null || level != null) {
                rows.add(
                    ChannelRow(
                        channel = channel,
                        frequencyMHz = frequency,
                        levelDbmv = level,
                        merDb = null,
                        berPre = null,
                        berPost = null,
                        icfrDb = null
                    )
                )
            }
        }
    }

    fun walk(value: Any?) {
        when (value) {
            is JSONObject -> {
                if (value.has("08_digitalFullScanResults")) {
                    collectFromDigitalFullScan(value)
                }
                collectFromUpstreamTable(value)
                if (value.has("0A_singleFullScanResults")) {
                    collectFromSingleFullScan(value)
                }
                val map = mutableMapOf<String, Any?>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key.lowercase(Locale.getDefault())] = value.opt(key)
                }
                collectFromMap(map)
                map.values.forEach { walk(it) }
            }
            is org.json.JSONArray -> {
                for (i in 0 until value.length()) {
                    walk(value.opt(i))
                }
            }
        }
    }

    walk(json)
    return rows
}

private fun collectMerPairs(results: JSONObject?): List<Pair<Double, Double>> {
    val pairs = mutableListOf<Pair<Double, Double>>()
    val data = results
        ?.optJSONObject("02_downstreamMerView")
        ?.optJSONObject("Passed")
        ?.optJSONArray("data")
        ?: return pairs
    for (i in 0 until data.length()) {
        val pair = data.optJSONArray(i) ?: continue
        if (pair.length() < 2) continue
        val first = pair.opt(0)
        val second = pair.opt(1)
        if (first is Number && second is Number) {
            pairs.add(first.toDouble() to second.toDouble())
        }
    }
    return pairs
}

private fun parseTestPointOffset(test: JSONObject): Double {
    val config = test.optJSONObject("configuration")
    val networkConfig = config?.optJSONObject("networkConfig")
    val forwardTPC = networkConfig?.optJSONObject("forwardTPC")?.optDouble("value", Double.NaN)
    val templateValue = networkConfig?.optJSONObject("testPointTemplate")?.optString("value")
    val parsedTemplate = templateValue
        ?.replace(",", ".")
        ?.filter { it.isDigit() || it == '.' || it == '-' }
        ?.toDoubleOrNull()
    val tpcValue = when {
        forwardTPC != null && !forwardTPC.isNaN() -> forwardTPC
        parsedTemplate != null -> parsedTemplate
        else -> null
    }
    return if (tpcValue == null || tpcValue != 20.0) 20.0 else 0.0
}

private fun validateMeasurementValues(
    rules: JSONObject?,
    test: JSONObject,
    type: String,
    equipmentKey: String,
    assetType: AssetType,
    amplifierTargets: Map<Int, Double>?,
    nodeTxType: String?
): List<String> {
    if (rules == null) return listOf("No se pudo cargar la tabla de validación.")
    val issues = mutableListOf<String>()
    val assetKey = when (assetType) {
        AssetType.NODE -> "node"
        AssetType.AMPLIFIER -> "amplifier"
        else -> return emptyList()
    }
    val results = test.optJSONObject("results")
    val testPointOffset = parseTestPointOffset(test)
    val rows = collectChannelRows(results)
    val merPairs = if (rows.any { it.merDb != null }) {
        rows.mapNotNull { row ->
            val mer = row.merDb ?: return@mapNotNull null
            (row.frequencyMHz ?: 0.0) to mer
        }
    } else {
        collectMerPairs(results)
    }

    if (type == "docsisexpert") {
        val ruleTable = rules.optJSONObject("docsisexpert")
            ?.optJSONObject(assetKey)
            ?.optJSONObject(equipmentKey)
        if (ruleTable == null) {
            return listOf("No hay reglas de DOCSIS para $assetKey/$equipmentKey.")
        }
        val targetFrequencies = listOf(16.8, 20.0, 24.8, 35.0)
        targetFrequencies.forEach { freq ->
            val rule = ruleTable.optJSONObject(freq.toString())
            if (rule == null) {
                issues.add("Sin rango para ${freq} MHz.")
            } else {
                val row = rows.firstOrNull { it.frequencyMHz != null && kotlin.math.abs(it.frequencyMHz - freq) <= 0.5 }
                val level = row?.levelDbmv
                if (level == null) {
                    issues.add("No se encontró nivel para ${freq} MHz.")
                } else {
                    val adjusted = level + testPointOffset
                    val min = rule.optDouble("min", Double.NaN)
                    val max = rule.optDouble("max", Double.NaN)
                    if (!min.isNaN() && adjusted < min) {
                        issues.add("Nivel ${freq} MHz bajo (${adjusted}).")
                    }
                    if (!max.isNaN() && adjusted > max) {
                        issues.add("Nivel ${freq} MHz alto (${adjusted}).")
                    }
                }
            }
        }
    }

    if (type == "channelexpert") {
        val common = rules.optJSONObject("channelexpert")?.optJSONObject("common")
        val merMin = common?.optJSONObject("mer")?.optDouble("min", Double.NaN)
        if (merMin != null && !merMin.isNaN()) {
            val lowMer = merPairs.filter { it.second < merMin }
            if (lowMer.isNotEmpty()) {
                issues.add("MER por debajo de ${merMin} dB en ${lowMer.size} punto(s).")
            }
        }
        val berPreMax = common?.optJSONObject("berPre")?.optDouble("max", Double.NaN)
        val berPostMax = common?.optJSONObject("berPost")?.optDouble("max", Double.NaN)
        val icfrMax = common?.optJSONObject("icfr")?.optDouble("max", Double.NaN)
        rows.filter { row -> row.channel != null && row.channel in 14..115 }.forEach { row ->
            if (merMin != null && !merMin.isNaN() && row.merDb != null && row.merDb < merMin) {
                issues.add("MER bajo en canal ${row.channel}.")
            }
            if (berPreMax != null && !berPreMax.isNaN() && row.berPre != null && row.berPre > berPreMax) {
                issues.add("BER previo alto en canal ${row.channel}.")
            }
            if (berPostMax != null && !berPostMax.isNaN() && row.berPost != null && row.berPost > berPostMax) {
                issues.add("BER posterior alto en canal ${row.channel}.")
            }
            if (icfrMax != null && !icfrMax.isNaN() && row.icfrDb != null && row.icfrDb > icfrMax) {
                issues.add("ICFR alto en canal ${row.channel}.")
            }
        }
        val channelRules = rules.optJSONObject("channelexpert")
            ?.optJSONObject(assetKey)
            ?.optJSONObject(equipmentKey)
        val txTargets = channelRules?.optJSONObject("txTargets")
        val txConfig = if (assetType == AssetType.NODE && nodeTxType != null) {
            txTargets?.optJSONObject(nodeTxType)
        } else {
            null
        }
        val channelTable = channelRules?.optJSONObject("channels")
        if (channelTable == null) {
            issues.add("No hay reglas de niveles para canales en $assetKey/$equipmentKey.")
        } else {
            val channels = listOf(50, 70, 110, 116, 136)
            if (txConfig != null) {
                val pilotTarget = txConfig.optDouble("pilotTarget", Double.NaN)
                val pilotTolerance = txConfig.optDouble("tolerance", 1.0)
                val digitalOffset = txTargets?.optDouble("digitalOffset", Double.NaN)
                val digitalTolerance = txTargets?.optDouble("digitalTolerance", Double.NaN)
                channels.forEach { channel ->
                    val row = rows.firstOrNull { it.channel == channel }
                    val level = row?.levelDbmv
                    if (level == null) {
                        issues.add("No se encontró nivel para canal $channel.")
                    } else if (!pilotTarget.isNaN()) {
                        val adjusted = level + testPointOffset
                        if (adjusted < pilotTarget - pilotTolerance || adjusted > pilotTarget + pilotTolerance) {
                            issues.add("Nivel fuera de rango en canal $channel.")
                        }
                    }
                }
                if (digitalOffset != null && !digitalOffset.isNaN() && digitalTolerance != null && !digitalTolerance.isNaN()) {
                    rows.filter { row -> row.channel != null && row.channel in 14..115 }
                        .forEach { row ->
                            val level = row.levelDbmv ?: return@forEach
                            val adjusted = level + testPointOffset
                            val target = pilotTarget + digitalOffset
                            if (adjusted < target - digitalTolerance || adjusted > target + digitalTolerance) {
                                issues.add("Nivel digital fuera de rango en canal ${row.channel}.")
                            }
                        }
                }
            } else {
                channels.forEach { channel ->
                    val rule = channelTable.optJSONObject(channel.toString())
                    if (rule == null) {
                        issues.add("Sin regla para canal $channel.")
                    } else if (rule.has("source")) {
                        val target = amplifierTargets?.get(channel)
                        if (target == null) {
                            issues.add("Falta tabla interna para canal $channel.")
                        } else {
                            val row = rows.firstOrNull { it.channel == channel }
                            val level = row?.levelDbmv
                            if (level == null) {
                                issues.add("No se encontró nivel para canal $channel.")
                            } else {
                                val tolerance = rule.optDouble("tolerance", 1.5)
                                val adjusted = level + testPointOffset
                                if (adjusted < target - tolerance || adjusted > target + tolerance) {
                                    issues.add("Nivel fuera de rango en canal $channel.")
                                }
                            }
                        }
                    } else {
                        val row = rows.firstOrNull { it.channel == channel }
                        val level = row?.levelDbmv
                        if (level == null) {
                            issues.add("No se encontró nivel para canal $channel.")
                        } else {
                            val target = rule.optDouble("target", Double.NaN)
                            val tolerance = rule.optDouble("tolerance", Double.NaN)
                            if (!target.isNaN() && !tolerance.isNaN()) {
                                val adjusted = level + testPointOffset
                                if (adjusted < target - tolerance || adjusted > target + tolerance) {
                                    issues.add("Nivel fuera de rango en canal $channel.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return issues
}

private fun isWithinRange(value: Double, min: Double?, max: Double?): Boolean {
    if (min != null && value < min) return false
    if (max != null && value > max) return false
    return true
}

private fun formatDbmv(value: Double?): String =
    if (value == null) "—" else String.format(Locale.getDefault(), "%.1f", value)

private fun formatMHz(value: Double?): String =
    if (value == null) "—" else String.format(Locale.getDefault(), "%.1f", value)

private data class MeasurementVerificationResult(
    val docsisExpert: Int,
    val channelExpert: Int,
    val docsisNames: List<String>,
    val channelNames: List<String>,
    val measurementEntries: List<MeasurementEntry>,
    val invalidTypeCount: Int,
    val invalidTypeNames: List<String>,
    val parseErrorCount: Int,
    val parseErrorNames: List<String>,
    val duplicateFileCount: Int,
    val duplicateFileNames: List<String>,
    val duplicateEntryCount: Int,
    val duplicateEntryNames: List<String>,
    val validationIssueNames: List<String>
)

private data class MeasurementEntry(
    val label: String,
    val type: String,
    val fromZip: Boolean,
    val isDiscarded: Boolean,
    val docsisMeta: Map<Double, ChannelMeta>,
    val docsisLevels: Map<Double, Double>,
    val docsisLevelOk: Map<Double, Boolean>,
    val pilotMeta: Map<Int, ChannelMeta>,
    val pilotLevels: Map<Int, Double>,
    val pilotLevelOk: Map<Int, Boolean>,
    val digitalRows: List<DigitalChannelRow>
)

private data class ChannelMeta(
    val channel: Int?,
    val frequencyMHz: Double?
)

private data class DigitalChannelRow(
    val channel: Int,
    val frequencyMHz: Double?,
    val levelDbmv: Double?,
    val levelOk: Boolean?,
    val mer: Double?,
    val berPre: Double?,
    val berPost: Double?,
    val icfr: Double?,
    val merOk: Boolean?,
    val berPreOk: Boolean?,
    val berPostOk: Boolean?,
    val icfrOk: Boolean?
)

private data class MeasurementVerificationSummary(
    val expectedDocsis: Int,
    val expectedChannel: Int,
    val result: MeasurementVerificationResult,
    val warnings: List<String>
)

private suspend fun verifyMeasurementFiles(
    context: Context,
    files: List<File>,
    asset: Asset,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository,
    discardedLabels: Set<String>,
    expectedDocsisOverride: Int? = null,
    expectedChannelOverride: Int? = null
): MeasurementVerificationSummary {
    val assetType = asset.type
    val expectedDocsis = expectedDocsisOverride ?: when (assetType) {
        AssetType.NODE -> 0
        AssetType.AMPLIFIER -> 4
        else -> 0
    }
    val expectedChannel = expectedChannelOverride ?: when (assetType) {
        AssetType.NODE -> 5
        AssetType.AMPLIFIER -> 4
        else -> 0
    }

    val seenIds = mutableSetOf<String>()
    var docsisCount = 0
    var channelCount = 0
    val docsisNames = linkedSetOf<String>()
    val channelNames = linkedSetOf<String>()
    val measurementEntries = mutableListOf<MeasurementEntry>()
    var invalidTypeCount = 0
    val invalidTypeNames = linkedSetOf<String>()
    var parseErrorCount = 0
    val parseErrorNames = linkedSetOf<String>()
    var duplicateFileCount = 0
    val duplicateFileNames = linkedSetOf<String>()
    var duplicateEntryCount = 0
    val duplicateEntryNames = linkedSetOf<String>()
    val validationIssueNames = linkedSetOf<String>()
    val rules = loadMeasurementRules(context)
    val equipmentKey = equipmentKeyFor(asset)
    val amplifierTargets = if (assetType == AssetType.AMPLIFIER) {
        val adjustment = repository.getAmplifierAdjustmentOne(asset.id)
        val calculated = adjustment?.let { CiscoHfcAmpCalculator.nivelesSalidaCalculados(it) }
        calculated?.let {
            mapOf(
                50 to it["CH50"],
                70 to it["CH70"],
                110 to it["CH110"],
                116 to it["CH116"],
                136 to it["CH136"]
            ).filterValues { value -> value != null }.mapValues { it.value!! }
        }
    } else {
        null
    }
    val nodeTxType = if (assetType == AssetType.NODE) {
        repository.getNodeAdjustmentOne(asset.id)?.let { adjustment ->
            when {
                adjustment.tx1310Confirmed -> "1310"
                adjustment.tx1550Confirmed -> "1550"
                else -> null
            }
        }
    } else {
        null
    }

    val dedupedFiles = buildList {
        val seenNames = mutableSetOf<String>()
        files.forEach { file ->
            val key = file.name.lowercase(Locale.getDefault())
            if (seenNames.add(key)) {
                add(file)
            } else {
                if (file.exists() && file.delete()) {
                    duplicateFileCount += 1
                    duplicateFileNames.add(file.name)
                } else {
                    parseErrorCount += 1
                    parseErrorNames.add(file.name)
                }
            }
        }
    }

    fun hashId(type: String, testTime: String, testDurationMs: String, geoLocation: String): String {
        val input = listOf(type, testTime, testDurationMs, geoLocation).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun handleJsonBytes(bytes: ByteArray, sourceFile: File?, sourceLabel: String) {
        val isDiscarded = discardedLabels.contains(sourceLabel)
        val jsonText = runCatching { String(bytes) }.getOrNull()
        if (jsonText.isNullOrBlank()) {
            if (!isDiscarded) {
                parseErrorCount += 1
                parseErrorNames.add(sourceLabel)
            }
            return
        }
        val json = runCatching { JSONObject(jsonText) }.getOrNull()
        val tests = json?.optJSONArray("tests")
        if (tests == null) {
            if (!isDiscarded) {
                parseErrorCount += 1
                parseErrorNames.add(sourceLabel)
            }
            return
        }
        var fileHasDuplicate = false
        for (i in 0 until tests.length()) {
            val test = tests.optJSONObject(i) ?: continue
            val type = test.optString("type").trim()
            val results = test.optJSONObject("results")
            val testTime = results?.optString("testTime").orEmpty()
            val testDurationMs = results?.optString("testDurationMs").orEmpty()
            val geoLocation = results?.optString("geoLocation").orEmpty()
            val normalizedType = type.lowercase(Locale.getDefault())
            val id = hashId(type, testTime, testDurationMs, geoLocation)
            if (!seenIds.add(id)) {
                if (!isDiscarded) {
                    if (sourceFile != null) {
                        fileHasDuplicate = true
                    } else {
                        duplicateEntryCount += 1
                        duplicateEntryNames.add(sourceLabel)
                    }
                }
                continue
            }
            when (normalizedType) {
                "docsisexpert" -> {
                    if (!isDiscarded) {
                        docsisCount += 1
                        docsisNames.add(sourceLabel)
                    }
                }
                "channelexpert" -> {
                    if (!isDiscarded) {
                        channelCount += 1
                        channelNames.add(sourceLabel)
                    }
                }
                else -> {
                    if (!isDiscarded) {
                        invalidTypeCount += 1
                        invalidTypeNames.add(sourceLabel)
                    }
                }
            }
            if (normalizedType == "docsisexpert" || normalizedType == "channelexpert") {
                val testPointOffset = parseTestPointOffset(test)
                val rows = collectChannelRows(results)
                val docsisFrequencies = rows.mapNotNull { it.frequencyMHz }.distinct().sorted()
                val pilotChannels = listOf(50, 70, 110, 116, 136)

                val docsisMeta = docsisFrequencies.associateWith { freq ->
                    val row = rows.firstOrNull { it.frequencyMHz != null && kotlin.math.abs(it.frequencyMHz - freq) <= 0.5 }
                    ChannelMeta(channel = row?.channel, frequencyMHz = row?.frequencyMHz)
                }

                val pilotMeta = pilotChannels.associateWith { channel ->
                    val row = rows.firstOrNull { it.channel == channel }
                    ChannelMeta(channel = row?.channel, frequencyMHz = row?.frequencyMHz)
                }

                val docsisLevels = docsisFrequencies.associateWith { freq ->
                    rows.firstOrNull { it.frequencyMHz != null && kotlin.math.abs(it.frequencyMHz - freq) <= 0.5 }
                        ?.levelDbmv
                }.filterValues { it != null }.mapValues { it.value!! }

                val pilotLevels = pilotChannels.associateWith { channel ->
                    rows.firstOrNull { it.channel == channel }?.levelDbmv
                }.filterValues { it != null }.mapValues { it.value!! }

                val docsisOk = mutableMapOf<Double, Boolean>()
                val pilotOk = mutableMapOf<Int, Boolean>()

                if (rules != null) {
                    val assetKey = if (assetType == AssetType.NODE) "node" else "amplifier"
                    val docsisRules = rules.optJSONObject("docsisexpert")
                        ?.optJSONObject(assetKey)
                        ?.optJSONObject(equipmentKey)
                    docsisLevels.forEach { (freq, level) ->
                        val rule = docsisRules?.optJSONObject(freq.toString())
                        val min = rule?.optDouble("min", Double.NaN)?.takeIf { !it.isNaN() }
                        val max = rule?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                        val adjusted = level + testPointOffset
                        docsisOk[freq] = isWithinRange(adjusted, min, max)
                    }

                    val channelRules = rules.optJSONObject("channelexpert")
                        ?.optJSONObject(assetKey)
                        ?.optJSONObject(equipmentKey)
                        ?.optJSONObject("channels")
                    pilotLevels.forEach { (channel, level) ->
                        val rule = channelRules?.optJSONObject(channel.toString())
                        val adjusted = level + testPointOffset
                        if (rule?.has("source") == true) {
                            val target = amplifierTargets?.get(channel)
                            val tolerance = rule.optDouble("tolerance", 1.5)
                            pilotOk[channel] = target != null &&
                                adjusted >= target - tolerance &&
                                adjusted <= target + tolerance
                        } else {
                            val target = rule?.optDouble("target", Double.NaN)?.takeIf { !it.isNaN() }
                            val tolerance = rule?.optDouble("tolerance", Double.NaN)?.takeIf { !it.isNaN() }
                            pilotOk[channel] = if (target != null && tolerance != null) {
                                adjusted >= target - tolerance && adjusted <= target + tolerance
                            } else {
                                true
                            }
                        }
                    }
                }

                val assetKey = if (assetType == AssetType.NODE) "node" else "amplifier"
                val common = rules?.optJSONObject("channelexpert")?.optJSONObject("common")
                val merMin = common?.optJSONObject("mer")?.optDouble("min", Double.NaN)?.takeIf { !it.isNaN() }
                val berPreMax = common?.optJSONObject("berPre")?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                val berPostMax = common?.optJSONObject("berPost")?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                val icfrMax = common?.optJSONObject("icfr")?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                val txTargets = rules?.optJSONObject("channelexpert")
                    ?.optJSONObject(assetKey)
                    ?.optJSONObject(equipmentKey)
                    ?.optJSONObject("txTargets")
                val txConfig = if (assetType == AssetType.NODE && nodeTxType != null) {
                    txTargets?.optJSONObject(nodeTxType)
                } else {
                    null
                }
                val pilotTarget = txConfig?.optDouble("pilotTarget", Double.NaN)
                val digitalOffset = txTargets?.optDouble("digitalOffset", Double.NaN)
                val digitalTolerance = txTargets?.optDouble("digitalTolerance", Double.NaN)
                val digitalTarget = if (pilotTarget != null && !pilotTarget.isNaN() &&
                    digitalOffset != null && !digitalOffset.isNaN()
                ) {
                    pilotTarget + digitalOffset
                } else {
                    null
                }

                val digitalRows = rows.filter { it.channel != null && it.channel in 14..115 }
                    .mapNotNull { row ->
                        val channel = row.channel ?: return@mapNotNull null
                        val levelOk = if (digitalTarget != null && digitalTolerance != null && !digitalTolerance.isNaN()) {
                            val adjusted = (row.levelDbmv ?: return@mapNotNull null) + testPointOffset
                            adjusted >= digitalTarget - digitalTolerance && adjusted <= digitalTarget + digitalTolerance
                        } else {
                            null
                        }
                        DigitalChannelRow(
                            channel = channel,
                            frequencyMHz = row.frequencyMHz,
                            levelDbmv = row.levelDbmv,
                            levelOk = levelOk,
                            mer = row.merDb,
                            berPre = row.berPre,
                            berPost = row.berPost,
                            icfr = row.icfrDb,
                            merOk = row.merDb?.let { merMin == null || it >= merMin },
                            berPreOk = row.berPre?.let { berPreMax == null || it <= berPreMax },
                            berPostOk = row.berPost?.let { berPostMax == null || it <= berPostMax },
                            icfrOk = row.icfrDb?.let { icfrMax == null || it <= icfrMax }
                        )
                    }

                measurementEntries.add(
                    MeasurementEntry(
                        label = sourceLabel,
                        type = normalizedType,
                        fromZip = sourceFile == null,
                        isDiscarded = isDiscarded,
                        docsisMeta = docsisMeta,
                        docsisLevels = docsisLevels,
                        docsisLevelOk = docsisOk,
                        pilotMeta = pilotMeta,
                        pilotLevels = pilotLevels,
                        pilotLevelOk = pilotOk,
                        digitalRows = digitalRows
                    )
                )

                if (!isDiscarded) {
                    val issues = validateMeasurementValues(
                        rules = rules,
                        test = test,
                        type = normalizedType,
                        equipmentKey = equipmentKey,
                        assetType = assetType,
                        amplifierTargets = amplifierTargets,
                        nodeTxType = nodeTxType
                    )
                    issues.forEach { issue ->
                        validationIssueNames.add("$sourceLabel: $issue")
                    }
                }
            }
        }

        if (!isDiscarded && fileHasDuplicate && sourceFile != null) {
            if (sourceFile.exists() && sourceFile.delete()) {
                duplicateFileCount += 1
                duplicateFileNames.add(sourceLabel)
            } else {
                parseErrorCount += 1
                parseErrorNames.add(sourceLabel)
            }
        }
    }

    fun isJsonLike(name: String): Boolean {
        val lower = name.lowercase(Locale.getDefault())
        val jsonNumbered = Regex(".*\\.json\\d+$")
        val jsonDotNumbered = Regex(".*\\.json\\.\\d+$")
        val jsonHyphenNumbered = Regex(".*\\.json-\\d+$")
        return lower.endsWith(".json") ||
            jsonNumbered.matches(lower) ||
            jsonDotNumbered.matches(lower) ||
            jsonHyphenNumbered.matches(lower)
    }

    fun isZipBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()
    }

    class MeasurementFileHandlers {
        fun handleGzipBytes(bytes: ByteArray, sourceFile: File?, sourceLabel: String) {
            val decompressed = runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            }.getOrNull()
            if (decompressed == null) {
                parseErrorCount += 1
                parseErrorNames.add(sourceLabel)
                return
            }
            if (isZipBytes(decompressed)) {
                runCatching {
                    ZipInputStream(ByteArrayInputStream(decompressed)).use { nested ->
                        handleZipInputStream(nested, sourceFile = null)
                    }
                }.onFailure {
                    parseErrorCount += 1
                    parseErrorNames.add(sourceLabel)
                }
                return
            }
            val jsonLabel = sourceLabel.removeSuffix(".gz")
            handleJsonBytes(decompressed, sourceFile = sourceFile, sourceLabel = jsonLabel)
        }

        fun handleZipInputStream(inputStream: ZipInputStream, sourceFile: File?) {
            var entry = inputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name.lowercase(Locale.getDefault())
                    val bytes = runCatching { inputStream.readBytes() }.getOrNull()
                    if (bytes == null) {
                        parseErrorCount += 1
                        parseErrorNames.add(entry.name)
                    } else if (entryName.endsWith(".zip")) {
                        runCatching {
                            ZipInputStream(ByteArrayInputStream(bytes)).use { nested ->
                                handleZipInputStream(nested, sourceFile = null)
                            }
                        }.onFailure {
                            parseErrorCount += 1
                            parseErrorNames.add(entry.name)
                        }
                    } else if (entryName.endsWith(".gz")) {
                        handleGzipBytes(bytes, sourceFile = sourceFile, sourceLabel = entry.name)
                    } else if (isJsonLike(entryName)) {
                        handleJsonBytes(bytes, sourceFile = sourceFile, sourceLabel = entry.name)
                    }
                }
                entry = inputStream.nextEntry
            }
        }
    }

    val handlers = MeasurementFileHandlers()

    dedupedFiles.forEach { file ->
        val name = file.name.lowercase(Locale.getDefault())
        when {
            isJsonLike(name) -> {
                runCatching { handleJsonBytes(file.readBytes(), sourceFile = file, sourceLabel = file.name) }
                    .onFailure {
                        parseErrorCount += 1
                        parseErrorNames.add(file.name)
                    }
            }
            name.endsWith(".zip") -> {
                runCatching {
                    ZipInputStream(file.inputStream()).use { zip ->
                        handlers.handleZipInputStream(zip, sourceFile = null)
                    }
                }.onFailure {
                    parseErrorCount += 1
                    parseErrorNames.add(file.name)
                }
            }
            name.endsWith(".gz") -> {
                runCatching {
                    handlers.handleGzipBytes(file.readBytes(), sourceFile = file, sourceLabel = file.name)
                }.onFailure {
                    parseErrorCount += 1
                    parseErrorNames.add(file.name)
                }
            }
        }
    }

    val warnings = buildList {
        if (expectedDocsis > 0) {
            if (docsisCount < expectedDocsis) {
                add("Faltan mediciones DocsisExpert (${docsisCount}/$expectedDocsis).")
            } else if (docsisCount > expectedDocsis) {
                add("Sobran mediciones DocsisExpert (${docsisCount}/$expectedDocsis). Elimine una.")
            }
        }
        if (channelCount < expectedChannel) {
            add("Faltan mediciones ChannelExpert (${channelCount}/$expectedChannel).")
        } else if (channelCount > expectedChannel) {
            add("Sobran mediciones ChannelExpert (${channelCount}/$expectedChannel). Elimine una.")
        }
        if (invalidTypeCount > 0) {
            add("Se encontraron mediciones con tipo inválido ($invalidTypeCount). Elimine las que no correspondan.")
        }
        if (duplicateFileCount > 0) {
            add("Se detectaron duplicados y se eliminaron $duplicateFileCount archivo(s).")
        }
        if (duplicateEntryCount > 0) {
            add("Se detectaron duplicados en ZIP ($duplicateEntryCount).")
        }
        if (parseErrorCount > 0) {
            add("No se pudieron leer $parseErrorCount archivo(s) o entradas.")
        }
        if (validationIssueNames.isNotEmpty()) {
            add("Se encontraron mediciones fuera de rango (${validationIssueNames.size}).")
        }
    }

    return MeasurementVerificationSummary(
        expectedDocsis = expectedDocsis,
        expectedChannel = expectedChannel,
        result = MeasurementVerificationResult(
            docsisExpert = docsisCount,
            channelExpert = channelCount,
            docsisNames = docsisNames.toList(),
            channelNames = channelNames.toList(),
            measurementEntries = measurementEntries.toList(),
            invalidTypeCount = invalidTypeCount,
            invalidTypeNames = invalidTypeNames.toList(),
            parseErrorCount = parseErrorCount,
            duplicateFileCount = duplicateFileCount,
            parseErrorNames = parseErrorNames.toList(),
            duplicateFileNames = duplicateFileNames.toList(),
            duplicateEntryCount = duplicateEntryCount,
            duplicateEntryNames = duplicateEntryNames.toList(),
            validationIssueNames = validationIssueNames.toList()
        ),
        warnings = warnings
    )
}

@Composable
private fun AssetFileSection(
    context: Context,
    navController: NavController,
    repository: com.example.fieldmaintenance.data.repository.MaintenanceRepository,
    reportFolder: String,
    onInteraction: () -> Unit,
    asset: Asset
) {
    data class DeleteTarget(val file: File, val isModule: Boolean)

    val isNodeAsset = asset.type == AssetType.NODE
    val rxAssetDir = remember(reportFolder, asset) {
        MaintenanceStorage.ensureAssetDir(context, reportFolder, asset)
    }
    val moduleAsset = if (isNodeAsset) {
        asset.copy(type = AssetType.AMPLIFIER)
    } else {
        asset
    }
    val moduleAssetDir = remember(reportFolder, moduleAsset) {
        MaintenanceStorage.ensureAssetDir(context, reportFolder, moduleAsset)
    }
    var rxFiles by remember(rxAssetDir) { mutableStateOf(rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }
    var moduleFiles by remember(moduleAssetDir) { mutableStateOf(moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }
    var fileToDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    val scope = rememberCoroutineScope()
    val rxDiscardedFile = remember(rxAssetDir) { File(rxAssetDir, ".discarded_measurements.txt") }
    var rxDiscardedLabels by remember(rxAssetDir) { mutableStateOf(loadDiscardedLabels(rxDiscardedFile)) }
    val moduleDiscardedFile = remember(moduleAssetDir) { File(moduleAssetDir, ".discarded_measurements.txt") }
    var moduleDiscardedLabels by remember(moduleAssetDir) { mutableStateOf(loadDiscardedLabels(moduleDiscardedFile)) }

    val viaviIntent = remember {
        context.packageManager.getLaunchIntentForPackage("com.viavisolutions.mobiletech")
    }

    var isExpanded by remember { mutableStateOf(true) }
    var rxExpanded by remember { mutableStateOf(true) }
    var moduleExpanded by remember { mutableStateOf(true) }
    var verificationSummaryRx by remember { mutableStateOf<MeasurementVerificationSummary?>(null) }
    var verificationSummaryModule by remember { mutableStateOf<MeasurementVerificationSummary?>(null) }

    data class RequiredCounts(
        val expectedDocsis: Int,
        val expectedChannel: Int,
        val maxDocsisTable: Int,
        val maxChannelTable: Int
    )

    fun requiredCounts(assetType: AssetType, isModule: Boolean): RequiredCounts {
        return when (assetType) {
            AssetType.NODE -> {
                if (isModule) {
                    RequiredCounts(expectedDocsis = 4, expectedChannel = 4, maxDocsisTable = 4, maxChannelTable = 4)
                } else {
                    RequiredCounts(expectedDocsis = 0, expectedChannel = 1, maxDocsisTable = 0, maxChannelTable = 1)
                }
            }
            AssetType.AMPLIFIER -> {
                RequiredCounts(expectedDocsis = 3, expectedChannel = 4, maxDocsisTable = 3, maxChannelTable = 4)
            }
        }
    }

    fun meetsRequired(summary: MeasurementVerificationSummary?, required: RequiredCounts): Boolean {
        if (summary == null) return false
        return summary.result.docsisExpert >= required.expectedDocsis &&
            summary.result.channelExpert >= required.expectedChannel
    }

    fun displayLabel(entry: MeasurementEntry): String {
        return if (entry.isDiscarded && !entry.label.contains("DESCARTADA", ignoreCase = true)) {
            "${entry.label} DESCARTADA"
        } else {
            entry.label
        }
    }

    fun toggleDiscardRx(entry: MeasurementEntry) {
        if (!entry.fromZip) return
        val updated = rxDiscardedLabels.toMutableSet()
        if (entry.isDiscarded) {
            updated.remove(entry.label)
        } else {
            updated.add(entry.label)
        }
        rxDiscardedLabels = updated
        saveDiscardedLabels(rxDiscardedFile, updated)
        val rxRequired = requiredCounts(asset.type, isModule = false)
        scope.launch {
            verificationSummaryRx = verifyMeasurementFiles(
                context,
                rxFiles,
                asset,
                repository,
                updated,
                expectedDocsisOverride = rxRequired.expectedDocsis,
                expectedChannelOverride = rxRequired.expectedChannel
            )
        }
    }

    fun toggleDiscardModule(entry: MeasurementEntry) {
        if (!entry.fromZip) return
        val updated = moduleDiscardedLabels.toMutableSet()
        if (entry.isDiscarded) {
            updated.remove(entry.label)
        } else {
            updated.add(entry.label)
        }
        moduleDiscardedLabels = updated
        saveDiscardedLabels(moduleDiscardedFile, updated)
        val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
        scope.launch {
            verificationSummaryModule = verifyMeasurementFiles(
                context,
                moduleFiles,
                moduleAsset,
                repository,
                updated,
                expectedDocsisOverride = moduleRequired.expectedDocsis,
                expectedChannelOverride = moduleRequired.expectedChannel
            )
        }
    }

    LaunchedEffect(rxFiles, rxDiscardedLabels) {
        val rxRequired = requiredCounts(asset.type, isModule = false)
        if (rxFiles.isNotEmpty()) {
            val summary = verifyMeasurementFiles(
                context,
                rxFiles,
                asset,
                repository,
                rxDiscardedLabels,
                expectedDocsisOverride = rxRequired.expectedDocsis,
                expectedChannelOverride = rxRequired.expectedChannel
            )
            verificationSummaryRx = summary
            if (summary.result.duplicateFileCount > 0) {
                rxFiles = rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            }
        } else {
            verificationSummaryRx = null
        }
    }

    LaunchedEffect(moduleFiles, moduleDiscardedLabels) {
        if (!isNodeAsset) return@LaunchedEffect
        val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
        if (moduleFiles.isNotEmpty()) {
            val summary = verifyMeasurementFiles(
                context,
                moduleFiles,
                moduleAsset,
                repository,
                moduleDiscardedLabels,
                expectedDocsisOverride = moduleRequired.expectedDocsis,
                expectedChannelOverride = moduleRequired.expectedChannel
            )
            verificationSummaryModule = summary
            if (summary.result.duplicateFileCount > 0) {
                moduleFiles = moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            }
        } else {
            verificationSummaryModule = null
        }
    }

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
                val rxRequired = requiredCounts(asset.type, isModule = false)
                val moduleRequired = requiredCounts(moduleAsset.type, isModule = true)
                val canRefresh = if (isNodeAsset) {
                    meetsRequired(verificationSummaryRx, rxRequired) &&
                        meetsRequired(verificationSummaryModule, moduleRequired)
                } else {
                    meetsRequired(verificationSummaryRx, rxRequired)
                }
                Text(
                    "Carga de Mediciones",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        onInteraction()
                        scope.launch {
                            val updatedRxFiles = rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            rxFiles = updatedRxFiles
                            if (updatedRxFiles.isNotEmpty()) {
                                verificationSummaryRx = verifyMeasurementFiles(
                                    context,
                                    updatedRxFiles,
                                    asset,
                                    repository,
                                    rxDiscardedLabels,
                                    expectedDocsisOverride = rxRequired.expectedDocsis,
                                    expectedChannelOverride = rxRequired.expectedChannel
                                )
                            } else {
                                verificationSummaryRx = null
                            }

                            if (isNodeAsset) {
                                val updatedModuleFiles = moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                                moduleFiles = updatedModuleFiles
                                if (updatedModuleFiles.isNotEmpty()) {
                                    verificationSummaryModule = verifyMeasurementFiles(
                                        context,
                                        updatedModuleFiles,
                                        moduleAsset,
                                        repository,
                                        moduleDiscardedLabels,
                                        expectedDocsisOverride = moduleRequired.expectedDocsis,
                                        expectedChannelOverride = moduleRequired.expectedChannel
                                    )
                                } else {
                                    verificationSummaryModule = null
                                }
                            }
                        }
                    },
                    enabled = asset.type in setOf(AssetType.NODE, AssetType.AMPLIFIER) && canRefresh
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refrescar verificación")
                }
                IconButton(onClick = {
                    onInteraction()
                    isExpanded = !isExpanded
                }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                    )
                }
            }
            if (isExpanded) {
                @Composable
                fun VerificationSummaryView(
                    summary: MeasurementVerificationSummary,
                    assetForDisplay: Asset,
                    onToggleDiscard: (MeasurementEntry) -> Unit,
                    isModule: Boolean
                ) {
                    val smallTextStyle = MaterialTheme.typography.bodySmall
                    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val warningColor = MaterialTheme.colorScheme.error
                    val pendingColor = MaterialTheme.colorScheme.tertiary
                    val docsisEntries = summary.result.measurementEntries.filter { it.type == "docsisexpert" }
                    val channelEntries = summary.result.measurementEntries.filter { it.type == "channelexpert" }
                    val required = requiredCounts(assetForDisplay.type, isModule)
                    val canRenderTables = meetsRequired(summary, required)

                    val docsisListEntries = docsisEntries.filterNot { it.isDiscarded }
                    val channelListEntries = channelEntries.filterNot { it.isDiscarded }
                    val channelDisplayEntries = if (assetForDisplay.type == AssetType.NODE && !isModule) {
                        channelListEntries.take(required.maxChannelTable)
                    } else {
                        channelListEntries
                    }
                    val docsisTableEntries = docsisListEntries.take(required.maxDocsisTable)
                    val channelTableEntries = channelListEntries.take(required.maxChannelTable)

                    @Composable
                    fun MeasurementHeaderCell(entry: MeasurementEntry, index: Int) {
                        val modifier = if (entry.fromZip) {
                            Modifier
                                .weight(1f)
                                .clickable { onToggleDiscard(entry) }
                        } else {
                            Modifier.weight(1f)
                        }
                        Column(modifier = modifier) {
                            Text(
                                "M${index + 1}",
                                style = smallTextStyle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    @Composable
                    fun MeasurementValueCell(value: String, ok: Boolean, discarded: Boolean) {
                        val textColor = if (ok || discarded) mutedColor else warningColor
                        val fontWeight = if (!ok && !discarded) FontWeight.SemiBold else FontWeight.Normal
                        Text(
                            value,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            style = smallTextStyle,
                            color = textColor,
                            fontWeight = fontWeight
                        )
                    }

                    fun docsisChannel(freq: Double): String {
                        val channel = docsisTableEntries.mapNotNull { it.docsisMeta[freq]?.channel }.firstOrNull()
                        return channel?.toString() ?: "—"
                    }

                    fun docsisFrequency(freq: Double): String {
                        val frequency = docsisTableEntries.mapNotNull { it.docsisMeta[freq]?.frequencyMHz }.firstOrNull()
                        return formatMHz(frequency ?: freq)
                    }

                    fun pilotFrequency(channel: Int): String {
                        val frequency = channelTableEntries.mapNotNull { it.pilotMeta[channel]?.frequencyMHz }.firstOrNull()
                        return formatMHz(frequency)
                    }

                    val docsisDiscardedEntries = docsisEntries.filter { it.isDiscarded }
                    val channelDiscardedEntries = channelEntries.filter { it.isDiscarded }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (assetForDisplay.type != AssetType.NODE) {
                            Text(
                                "Mediciones docsisexpert ${summary.result.docsisExpert}/${summary.expectedDocsis}",
                                fontWeight = FontWeight.SemiBold
                            )
                            docsisListEntries.forEachIndexed { index, entry ->
                                val modifier = if (entry.fromZip) {
                                    Modifier.clickable { onToggleDiscard(entry) }
                                } else {
                                    Modifier
                                }
                                Text(
                                    "M${index + 1}: ${displayLabel(entry)}",
                                    style = smallTextStyle,
                                    color = mutedColor,
                                    modifier = modifier
                                )
                            }
                            if (docsisDiscardedEntries.isNotEmpty()) {
                                Text("Descartadas:", fontWeight = FontWeight.SemiBold)
                                docsisDiscardedEntries.forEach { entry ->
                                    Text(
                                        "• ${displayLabel(entry)}",
                                        style = smallTextStyle,
                                        color = mutedColor,
                                        modifier = Modifier.clickable { onToggleDiscard(entry) }
                                    )
                                }
                            }
                        }
                        Text(
                            "Mediciones channelexpert ${summary.result.channelExpert}/${summary.expectedChannel}",
                            fontWeight = FontWeight.SemiBold
                        )
                        channelDisplayEntries.forEachIndexed { index, entry ->
                            val modifier = if (entry.fromZip) {
                                Modifier.clickable { onToggleDiscard(entry) }
                            } else {
                                Modifier
                            }
                            Text(
                                "M${index + 1}: ${displayLabel(entry)}",
                                style = smallTextStyle,
                                color = mutedColor,
                                modifier = modifier
                            )
                        }
                        if (channelDiscardedEntries.isNotEmpty()) {
                            Text("Descartadas:", fontWeight = FontWeight.SemiBold)
                            channelDiscardedEntries.forEach { entry ->
                                Text(
                                    "• ${displayLabel(entry)}",
                                    style = smallTextStyle,
                                    color = mutedColor,
                                    modifier = Modifier.clickable { onToggleDiscard(entry) }
                                )
                            }
                        }
                        if (!canRenderTables) {
                            Text(
                                "Agregue las mediciones faltantes.",
                                style = smallTextStyle,
                                fontWeight = FontWeight.SemiBold,
                                color = warningColor
                            )
                        }
                        summary.warnings.forEach { warning ->
                            Text(
                                warning,
                                color = MaterialTheme.colorScheme.error,
                                style = smallTextStyle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (summary.result.duplicateFileNames.isNotEmpty()) {
                            Text("Archivos eliminados:", fontWeight = FontWeight.SemiBold)
                            summary.result.duplicateFileNames.forEach { name ->
                                Text("• $name", style = smallTextStyle, color = mutedColor)
                            }
                        }
                        if (summary.result.invalidTypeNames.isNotEmpty()) {
                            Text("Mediciones inválidas:", fontWeight = FontWeight.SemiBold)
                            summary.result.invalidTypeNames.forEach { name ->
                                Text("• $name", style = smallTextStyle, color = mutedColor)
                            }
                        }
                        if (summary.result.parseErrorNames.isNotEmpty()) {
                            Text("No se pudieron leer:", fontWeight = FontWeight.SemiBold)
                            summary.result.parseErrorNames.forEach { name ->
                                Text("• $name", style = smallTextStyle, color = mutedColor)
                            }
                        }
                        if (summary.result.duplicateEntryNames.isNotEmpty()) {
                            Text("Duplicados en ZIP:", fontWeight = FontWeight.SemiBold)
                            summary.result.duplicateEntryNames.forEach { name ->
                                Text("• $name", style = smallTextStyle, color = mutedColor)
                            }
                        }
                        if (summary.result.validationIssueNames.isNotEmpty()) {
                            Text(
                                "Validación de valores: ${summary.result.validationIssueNames.size} observaciones.",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (canRenderTables && docsisEntries.isNotEmpty() && assetForDisplay.type != AssetType.NODE) {
                            Text("DocsisExpert (niveles):", fontWeight = FontWeight.SemiBold)
                            if (docsisListEntries.size > required.maxDocsisTable) {
                                Text(
                                    "Solo se muestran ${required.maxDocsisTable} mediciones (M1 a M${required.maxDocsisTable}).",
                                    style = smallTextStyle,
                                    color = mutedColor
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Canal", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                Text("Freq", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                docsisTableEntries.forEachIndexed { index, entry ->
                                    MeasurementHeaderCell(entry, index)
                                }
                            }
                            val docsisFrequencies = docsisTableEntries
                                .flatMap { it.docsisLevels.keys }
                                .distinct()
                                .sorted()
                            docsisFrequencies.forEach { freq ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(docsisChannel(freq), modifier = Modifier.weight(1f), style = smallTextStyle)
                                    Text("${docsisFrequency(freq)} MHz", modifier = Modifier.weight(1f), style = smallTextStyle)
                                    docsisTableEntries.forEach { entry ->
                                        val value = entry.docsisLevels[freq]
                                        val ok = entry.docsisLevelOk[freq] ?: true
                                        MeasurementValueCell(
                                            value = formatDbmv(value),
                                            ok = ok,
                                            discarded = entry.isDiscarded
                                        )
                                    }
                                }
                            }
                        }

                        if (canRenderTables && channelEntries.isNotEmpty()) {
                            Text("Canales piloto (ChannelExpert):", fontWeight = FontWeight.SemiBold)
                            if (channelListEntries.size > required.maxChannelTable) {
                                Text(
                                    "Solo se muestran ${required.maxChannelTable} mediciones (M1 a M${required.maxChannelTable}).",
                                    style = smallTextStyle,
                                    color = mutedColor
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Canal", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                Text("Freq", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                channelTableEntries.forEachIndexed { index, entry ->
                                    MeasurementHeaderCell(entry, index)
                                }
                            }
                            val pilotChannels = listOf(50, 70, 110, 116, 136)
                            pilotChannels.forEach { channel ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("$channel", modifier = Modifier.weight(1f), style = smallTextStyle)
                                    Text("${pilotFrequency(channel)} MHz", modifier = Modifier.weight(1f), style = smallTextStyle)
                                    channelTableEntries.forEach { entry ->
                                        val value = entry.pilotLevels[channel]
                                        val ok = entry.pilotLevelOk[channel] ?: true
                                        MeasurementValueCell(
                                            value = formatDbmv(value),
                                            ok = ok,
                                            discarded = entry.isDiscarded
                                        )
                                    }
                                }
                            }
                        }

                        if (canRenderTables && channelEntries.isNotEmpty()) {
                            Text("ChannelExpert (canales digitales):", fontWeight = FontWeight.SemiBold)
                            channelTableEntries.forEachIndexed { index, entry ->
                                val label = "M${index + 1}"
                                val hasIssues = entry.digitalRows.any { row ->
                                    (row.levelOk == false) ||
                                        (row.merOk == false) ||
                                        (row.berPreOk == false) ||
                                        (row.berPostOk == false) ||
                                        (row.icfrOk == false)
                                }
                                var expanded by remember(entry.label) { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasIssues) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = pendingColor
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("$label - ${displayLabel(entry)}", style = smallTextStyle, modifier = Modifier.weight(1f))
                                    Icon(
                                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                                if (expanded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Canal", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                        Text("Freq", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                        Text("Nivel", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                        Text("MER", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                        Text("BER pre", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                        Text("BER post", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                        Text("ICFR", modifier = Modifier.weight(1f), style = smallTextStyle, fontWeight = FontWeight.SemiBold)
                                    }
                                    entry.digitalRows.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("${row.channel}", modifier = Modifier.weight(1f), style = smallTextStyle)
                                            Text(formatDbmv(row.frequencyMHz), modifier = Modifier.weight(1f), style = smallTextStyle)
                                            MeasurementValueCell(
                                                value = formatDbmv(row.levelDbmv),
                                                ok = row.levelOk != false,
                                                discarded = entry.isDiscarded
                                            )
                                            MeasurementValueCell(
                                                value = formatDbmv(row.mer),
                                                ok = row.merOk != false,
                                                discarded = entry.isDiscarded
                                            )
                                            MeasurementValueCell(
                                                value = row.berPre?.toString() ?: "—",
                                                ok = row.berPreOk != false,
                                                discarded = entry.isDiscarded
                                            )
                                            MeasurementValueCell(
                                                value = row.berPost?.toString() ?: "—",
                                                ok = row.berPostOk != false,
                                                discarded = entry.isDiscarded
                                            )
                                            MeasurementValueCell(
                                                value = formatDbmv(row.icfr),
                                                ok = row.icfrOk != false,
                                                discarded = entry.isDiscarded
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (asset.type == AssetType.NODE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rxExpanded = !rxExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mediciones RX", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(
                                if (rxExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (rxExpanded) {
                            Button(
                                onClick = {
                                    onInteraction()
                                    if (viaviIntent != null) {
                                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                                            set(PendingMeasurementReportIdKey, asset.reportId)
                                            set(PendingMeasurementAssetIdKey, asset.id)
                                            set(PendingMeasurementAssetTypeKey, AssetType.NODE.name)
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
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar Medición RX")
                            }
                            verificationSummaryRx?.let { summary ->
                                VerificationSummaryView(summary, asset, ::toggleDiscardRx, isModule = false)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { moduleExpanded = !moduleExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mediciones Modulo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(
                                if (moduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (moduleExpanded) {
                            Button(
                                onClick = {
                                    onInteraction()
                                    if (viaviIntent != null) {
                                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                                            set(PendingMeasurementReportIdKey, asset.reportId)
                                            set(PendingMeasurementAssetIdKey, asset.id)
                                            set(PendingMeasurementAssetTypeKey, AssetType.AMPLIFIER.name)
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
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar Medición Modulo")
                            }
                            verificationSummaryModule?.let { summary ->
                                VerificationSummaryView(summary, moduleAsset, ::toggleDiscardModule, isModule = true)
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onInteraction()
                            if (viaviIntent != null) {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(PendingMeasurementReportIdKey, asset.reportId)
                                    set(PendingMeasurementAssetIdKey, asset.id)
                                    set(PendingMeasurementAssetTypeKey, asset.type.name)
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
                    verificationSummaryRx?.let { summary ->
                        VerificationSummaryView(summary, asset, ::toggleDiscardRx, isModule = false)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Archivos agregados",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onInteraction()
                                navController.navigate(Screen.MeasurementsTrash.route)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Abrir papelera de mediciones")
                        }
                    }
                    @Composable
                    fun FileList(title: String?, entries: List<File>, isModule: Boolean) {
                        if (!title.isNullOrBlank()) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                        }
                        if (entries.isEmpty()) {
                            Text(
                                "No hay mediciones agregadas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            entries.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        file.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    IconButton(onClick = { fileToDelete = DeleteTarget(file, isModule) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Mover a papelera")
                                    }
                                }
                            }
                        }
                    }

                    if (isNodeAsset) {
                        FileList(title = "RX", entries = rxFiles, isModule = false)
                        Spacer(modifier = Modifier.height(4.dp))
                        FileList(title = "Modulo", entries = moduleFiles, isModule = true)
                    } else if (rxFiles.isEmpty()) {
                        Text(
                            "No hay mediciones agregadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FileList(title = null, entries = rxFiles, isModule = false)
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
                            MaintenanceStorage.moveMeasurementFileToTrash(context, target.file)
                            val updated = if (target.isModule) {
                                moduleAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            } else {
                                rxAssetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            }
                            withContext(Dispatchers.Main) {
                                if (target.isModule) {
                                    moduleFiles = updated
                                } else {
                                    rxFiles = updated
                                }
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

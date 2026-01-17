@file:Suppress("DEPRECATION")

package com.example.fieldmaintenance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fieldmaintenance.data.model.AmplifierAdjustment
import com.example.fieldmaintenance.data.model.AmplifierMode
import com.example.fieldmaintenance.data.model.Frequency
import com.example.fieldmaintenance.data.model.label
import com.example.fieldmaintenance.util.CiscoHfcAmpCalculator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AmplifierAdjustmentCard(
    assetId: String,
    bandwidth: Frequency?,
    amplifierMode: AmplifierMode?,
    initial: AmplifierAdjustment?,
    showRequiredErrors: Boolean,
    collapseSignal: Int,
    onCurrentChange: (AmplifierAdjustment) -> Unit,
    onPersist: suspend (AmplifierAdjustment) -> Unit
) {
    // Global collapse for the whole module (user can collapse everything)
    var moduleExpanded by rememberSaveable(assetId) { mutableStateOf(true) }

    // Individual section collapse states (all open by default)
    var entradaExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var salidaPlanoExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var compareExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var recoExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var entradaAlert by remember(assetId) { mutableStateOf<EntradaAlert?>(null) }

    var dirty by rememberSaveable(assetId) { mutableStateOf(false) }

    LaunchedEffect(collapseSignal) {
        moduleExpanded = false
    }

    fun parseDbmv(text: String): Double? =
        text.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()

    fun fmt(text: Double?): String = text?.let { CiscoHfcAmpCalculator.format1(it) } ?: ""

    // Inputs (strings to preserve comma/typing)
    var inCh50 by rememberSaveable { mutableStateOf("") }
    var inHigh by rememberSaveable { mutableStateOf("") }
    var inHighFreq by rememberSaveable { mutableStateOf<Int?>(750) } // 750 (CH116) or 870 (CH136)
    var inPlanCh50 by rememberSaveable { mutableStateOf("") }
    var inPlanHigh by rememberSaveable { mutableStateOf("") }

    var planLowFreq by rememberSaveable { mutableStateOf<Int?>(54) }
    var planLowDbmv by rememberSaveable { mutableStateOf("") }
    var planHighFreq by rememberSaveable { mutableStateOf<Int?>(750) }
    var planHighDbmv by rememberSaveable { mutableStateOf("") }

    var outCh50 by rememberSaveable { mutableStateOf("") }
    var outCh70 by rememberSaveable { mutableStateOf("") }
    var outCh110 by rememberSaveable { mutableStateOf("") }
    var outCh116 by rememberSaveable { mutableStateOf("") }
    var outCh136 by rememberSaveable { mutableStateOf("") }

    // Initialize from DB whenever it arrives, but don't overwrite user edits (dirty=true)
    LaunchedEffect(assetId, initial?.updatedAt) {
        if (assetId.isBlank()) return@LaunchedEffect
        if (dirty) return@LaunchedEffect
        if (initial == null) return@LaunchedEffect

        inCh50 = fmt(initial.inputCh50Dbmv)
        inHigh = fmt(initial.inputCh116Dbmv)
        inHighFreq = initial.inputHighFreqMHz ?: 750
        inPlanCh50 = fmt(initial.inputPlanCh50Dbmv)
        inPlanHigh = fmt(initial.inputPlanHighDbmv)
        planLowFreq = initial.planLowFreqMHz ?: 54
        planLowDbmv = fmt(initial.planLowDbmv)
        planHighFreq = initial.planHighFreqMHz ?: 750
        planHighDbmv = fmt(initial.planHighDbmv)
        outCh50 = fmt(initial.outCh50Dbmv)
        outCh70 = fmt(initial.outCh70Dbmv)
        outCh110 = fmt(initial.outCh110Dbmv)
        outCh116 = fmt(initial.outCh116Dbmv)
        outCh136 = fmt(initial.outCh136Dbmv)
    }

    fun buildAdjustment(): AmplifierAdjustment {
        return AmplifierAdjustment(
            assetId = assetId,
            inputCh50Dbmv = parseDbmv(inCh50),
            inputCh116Dbmv = parseDbmv(inHigh),
            inputHighFreqMHz = inHighFreq,
            inputPlanCh50Dbmv = parseDbmv(inPlanCh50),
            inputPlanHighDbmv = parseDbmv(inPlanHigh),
            planLowFreqMHz = planLowFreq,
            planLowDbmv = parseDbmv(planLowDbmv),
            planHighFreqMHz = planHighFreq,
            planHighDbmv = parseDbmv(planHighDbmv),
            outCh50Dbmv = parseDbmv(outCh50),
            outCh70Dbmv = parseDbmv(outCh70),
            outCh110Dbmv = parseDbmv(outCh110),
            outCh116Dbmv = parseDbmv(outCh116),
            outCh136Dbmv = parseDbmv(outCh136),
        )
    }

    // Auto persist (debounced) – only if we have an assetId
    LaunchedEffect(assetId) {
        if (assetId.isBlank()) return@LaunchedEffect
        // Manual debounce (avoid FlowPreview debounce()).
        var job: Job? = null
        // Use a stable key for distinctUntilChanged (avoid updatedAt constantly changing)
        snapshotFlow { buildAdjustment().copy(updatedAt = 0L) }
            .distinctUntilChanged()
            .collect { stable ->
                onCurrentChange(stable.copy(assetId = assetId))
                job?.cancel()
                job = launch {
                    delay(350)
                    onPersist(stable.copy(updatedAt = System.currentTimeMillis()))
                }
            }
    }

    val adj = buildAdjustment()
    val entradaCalc = if (
        adj.inputCh50Dbmv != null &&
        adj.inputCh116Dbmv != null &&
        adj.inputPlanCh50Dbmv != null &&
        adj.inputPlanHighDbmv != null
    ) {
        CiscoHfcAmpCalculator.nivelesEntradaCalculados(adj)
    } else {
        null
    }
    val salidaCalc = CiscoHfcAmpCalculator.nivelesSalidaCalculados(adj)
    val tilt = CiscoHfcAmpCalculator.fwdInEqTilt(adj, bandwidth)
    val pad = CiscoHfcAmpCalculator.fwdInPad(adj, bandwidth, amplifierMode)
    val agc = CiscoHfcAmpCalculator.agcPad(adj, bandwidth, amplifierMode)
    val entradaValid = run {
        val ch50Med = parseDbmv(inCh50)
        val ch50Plan = parseDbmv(inPlanCh50)
        val highMed = parseDbmv(inHigh)
        val highPlan = parseDbmv(inPlanHigh)
        val ch50Ok = ch50Med != null && ch50Plan != null && ch50Med >= 15.0 && kotlin.math.abs(ch50Med - ch50Plan) < 4.0
        val highOk = highMed != null && highPlan != null && highMed >= 15.0 && kotlin.math.abs(highMed - highPlan) < 4.0
        ch50Ok && highOk
    }

    fun isWeirdDbmv(v: Double?): Boolean = v != null && (v < -20.0 || v > 80.0)
    fun maybeTriggerEntradaAlert(
        canal: String,
        med: Double?,
        plan: Double? = null,
        calc: Double? = null
    ) {
        if (med == null) return
        val reference = plan ?: calc
        val delta = if (reference != null) kotlin.math.abs(med - reference) else null
        val needsAlert = med < 15.0 || (delta != null && delta >= 4.0)
        if (!needsAlert) return
        val planLabel = reference?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"
        val diffLabel = delta?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"
        val message = buildString {
            append("EL nivel medido esta desviado ")
            append(diffLabel)
            append(" con respecto a los niveles del plano ")
            append(planLabel)
            append(". Revise posibles problemas en la entrada para continuar con los ajustes.")
        }
        val key = "$canal:${med}:${plan}"
        if (entradaAlert?.key != key) {
            entradaAlert = EntradaAlert(
                key = key,
                title = "Nivel fuera de rango",
                message = message
            )
        }
    }

    // Outer "frame" for the whole module (visual border)
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row (collapses the whole module)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { moduleExpanded = !moduleExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ajuste de Amplificador",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val bwText = bandwidth?.mhz?.let { "$it MHz" } ?: "—"
                    val tipoText = amplifierMode?.label ?: "—"
                    Text(
                        "Ancho de banda: $bwText  ·  Tipo: $tipoText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = if (moduleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = moduleExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                CollapsibleSection(
                    titleBold = "Niveles ENTRADA",
                    titleLight = "",
                    expanded = entradaExpanded,
                    onToggle = { entradaExpanded = !entradaExpanded }
                ) {
                    // Header row like the reference (CANAL / FRECUENCIA / AMPLITUD / PLANO / DIF)
                    EntradaHeaderRow()
                    EntradaRowPlan(
                        canal = "CH50",
                        freqText = "379 MHz",
                        medidoValue = inCh50,
                        planValue = inPlanCh50,
                        isError = showRequiredErrors && parseDbmv(inCh50) == null,
                        onMedidoChange = { dirty = true; inCh50 = it },
                        onPlanChange = { dirty = true; inPlanCh50 = it }
                    )
                    LaunchedEffect(inCh50, entradaCalc) {
                        maybeTriggerEntradaAlert(
                            canal = "CH50",
                            med = parseDbmv(inCh50),
                            calc = entradaCalc?.get("CH50")
                        )
                    }
                    val highFreq = inHighFreq ?: 750
                    val highCanal = if (highFreq == 870) "CH136" else "CH116"
                    EntradaRowWithFreqSelector(
                        canal = highCanal,
                        freqMHz = highFreq,
                        optionsMHz = listOf(750, 870),
                        onFreqChange = { dirty = true; inHighFreq = it },
                        medidoValue = inHigh,
                        planValue = inPlanHigh,
                        isError = showRequiredErrors && parseDbmv(inHigh) == null,
                        onMedidoChange = { dirty = true; inHigh = it },
                        onPlanChange = { dirty = true; inPlanHigh = it }
                    )
                    LaunchedEffect(inHigh, inHighFreq, entradaCalc) {
                        val canalKey = if (inHighFreq == 870) "CH136" else "CH116"
                        maybeTriggerEntradaAlert(
                            canal = canalKey,
                            med = parseDbmv(inHigh),
                            calc = entradaCalc?.get(canalKey)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    // Calculated list (no extra title; CALC column already indicates)
                    SimpleCalcList(
                        rows = listOf(
                            CalcRowData("L 54", "54 MHz", entradaCalc?.get("L 54")),
                            CalcRowData("L102", "102 MHz", entradaCalc?.get("L102")),
                            CalcRowData("CH3", "61 MHz", entradaCalc?.get("CH3")),
                            CalcRowData("CH50", "379 MHz", entradaCalc?.get("CH50")),
                            CalcRowData("CH70", "495 MHz", entradaCalc?.get("CH70")),
                            CalcRowData("CH116", "750 MHz", entradaCalc?.get("CH116")),
                            CalcRowData("CH136", "870 MHz", entradaCalc?.get("CH136")),
                            CalcRowData("CH158", "1000 MHz", entradaCalc?.get("CH158")),
                        )
                    )
                }

                CollapsibleSection(
                    titleBold = "Niveles SALIDA",
                    titleLight = "",
                    expanded = salidaPlanoExpanded,
                    onToggle = { salidaPlanoExpanded = !salidaPlanoExpanded }
                ) {
                    if (!entradaValid) {
                        Text(
                            "Complete mediciones de entrada válidas para continuar. La diferencia entre el nivel de entrada y medido aceptable es menor a 4. Nivel minimo de entrada permitido es 15 dBmV si esta indicado por plano.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        return@CollapsibleSection
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FreqDropdown(
                            label = "L output Plano",
                            options = listOf(54, 102),
                            value = planLowFreq,
                            onChange = { dirty = true; planLowFreq = it },
                            width = 150.dp,
                            labelBold = true
                        )
                        DbmvField(
                            label = "dBmV",
                            value = planLowDbmv,
                            modifier = Modifier.width(92.dp),
                            isError = (showRequiredErrors && parseDbmv(planLowDbmv) == null) || isWeirdDbmv(parseDbmv(planLowDbmv)),
                            compact = true,
                            onChange = { dirty = true; planLowDbmv = it }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FreqDropdown(
                            label = "H output Plano",
                            options = listOf(750, 870, 1000),
                            value = planHighFreq,
                            onChange = { dirty = true; planHighFreq = it },
                            width = 150.dp,
                            labelBold = true
                        )
                        DbmvField(
                            label = "dBmV",
                            value = planHighDbmv,
                            modifier = Modifier.width(92.dp),
                            isError = (showRequiredErrors && parseDbmv(planHighDbmv) == null) || isWeirdDbmv(parseDbmv(planHighDbmv)),
                            compact = true,
                            onChange = { dirty = true; planHighDbmv = it }
                        )
                    }
                    if (isWeirdDbmv(parseDbmv(planLowDbmv)) || isWeirdDbmv(parseDbmv(planHighDbmv))) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "Revisa amplitudes (-20 a 80 dBmV).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Calculated list (same module; no extra title needed)
                    SimpleCalcList(
                        rows = listOf(
                            CalcRowData("L54", "54 MHz", salidaCalc?.get("L54")),
                            CalcRowData("L102", "102 MHz", salidaCalc?.get("L102")),
                            CalcRowData("CH3", "61 MHz", salidaCalc?.get("CH3")),
                            CalcRowData("CH50", "379 MHz", salidaCalc?.get("CH50")),
                            CalcRowData("CH70", "495 MHz", salidaCalc?.get("CH70")),
                            CalcRowData("CH110", "711 MHz", salidaCalc?.get("CH110")),
                            CalcRowData("CH116", "750 MHz", salidaCalc?.get("CH116")),
                            CalcRowData("CH136", "870 MHz", salidaCalc?.get("CH136")),
                            CalcRowData("CH158", "1000 MHz", salidaCalc?.get("CH158")),
                        )
                    )
                }

                CollapsibleSection(
                    titleBold = "Niveles SALIDA",
                    titleLight = "calculado vs medido",
                    expanded = compareExpanded,
                    onToggle = { compareExpanded = !compareExpanded }
                ) {
                    if (!entradaValid) {
                        Text(
                            "Complete mediciones de entrada válidas para continuar. La diferencia entre el nivel de entrada y medido aceptable es menor a 4. Nivel minimo de entrada permitido es 15 dBmV si esta indicado por plano.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        return@CollapsibleSection
                    }
                    CompareHeaderRow()
                    CompareRow(
                        "CH50",
                        "379 MHz",
                        salidaCalc?.get("CH50"),
                        outCh50,
                        showRequiredErrors && parseDbmv(outCh50) == null,
                        onMedidoChange = { dirty = true; outCh50 = it }
                    )
                    CompareRow(
                        "CH70",
                        "495 MHz",
                        salidaCalc?.get("CH70"),
                        outCh70,
                        showRequiredErrors && parseDbmv(outCh70) == null,
                        onMedidoChange = { dirty = true; outCh70 = it }
                    )
                    CompareRow(
                        "CH110",
                        "711 MHz",
                        salidaCalc?.get("CH110"),
                        outCh110,
                        showRequiredErrors && parseDbmv(outCh110) == null,
                        onMedidoChange = { dirty = true; outCh110 = it }
                    )
                    CompareRow(
                        "CH116",
                        "750 MHz",
                        salidaCalc?.get("CH116"),
                        outCh116,
                        showRequiredErrors && parseDbmv(outCh116) == null,
                        onMedidoChange = { dirty = true; outCh116 = it }
                    )
                    CompareRow(
                        "CH136",
                        "870 MHz",
                        salidaCalc?.get("CH136"),
                        outCh136,
                        showRequiredErrors && parseDbmv(outCh136) == null,
                        onMedidoChange = { dirty = true; outCh136 = it }
                    )
                }

                CollapsibleSection(
                    titleBold = "",
                    titleLight = "FWD IN PAD/ EQ /AGC PAD a colocar",
                    expanded = recoExpanded,
                    onToggle = { recoExpanded = !recoExpanded }
                ) {
                    if (!entradaValid) {
                        Text(
                            "Complete mediciones de entrada válidas para continuar. La diferencia entre el nivel de entrada y medido aceptable es menor a 4. Nivel minimo de entrada permitido es 15 dBmV si esta indicado por plano.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        return@CollapsibleSection
                    }
                    RecommendationLine("FWD IN PAD", pad)
                    RecommendationLine(
                        label = if (tilt != null && tilt > 0) "FWD IN invEQ (TILT)" else "FWD IN EQ (TILT)",
                        value = tilt
                    )
                    RecommendationLine("AGC IN PAD", agc)
                }
                }
            }
        }
    }
    entradaAlert?.let { alert ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { entradaAlert = null },
            title = { Text(alert.title) },
            text = { Text(alert.message) },
            confirmButton = {
                TextButton(onClick = { entradaAlert = null }) { Text("Aceptar") }
            }
        )
    }
}

private data class EntradaAlert(
    val key: String,
    val title: String,
    val message: String
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DbmvField(
    label: String,
    value: String,
    modifier: Modifier,
    isError: Boolean = false,
    compact: Boolean = false,
    compactHeight: Dp = 36.dp,
    textColor: Color? = null,
    onChange: (String) -> Unit
) {
    var wasFocused by remember { mutableStateOf(false) }
    if (!compact) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = modifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = isError,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor ?: MaterialTheme.colorScheme.onSurface)
        )
        return
    }

    // Compact field: avoids text clipping and forces visible text color.
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(2.dp))
        }
        Surface(
            modifier = Modifier.height(compactHeight),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, borderColor),
            shape = MaterialTheme.shapes.small
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(compactHeight)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor ?: MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FreqDropdown(
    label: String,
    options: List<Int>,
    value: Int?,
    onChange: (Int) -> Unit,
    width: androidx.compose.ui.unit.Dp,
    labelBold: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(width)) {
        OutlinedTextField(
            value = value?.let { "$it" } ?: "Sel",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = if (labelBold) FontWeight.SemiBold else FontWeight.Normal) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            },
            singleLine = true,
        )
        // Overlay to ensure clicks open the menu (TextField can consume clicks on some versions).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { f ->
                DropdownMenuItem(
                    text = { Text("$f MHz") },
                    onClick = { onChange(f); expanded = false }
                )
            }
        }
    }
}

private data class CalcRowData(val canal: String, val freqText: String, val calc: Double?)

@Composable
private fun CollapsibleSection(
    titleBold: String,
    titleLight: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titleBold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(" $titleLight", style = MaterialTheme.typography.titleSmall)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun EntradaRowPlan(
    canal: String,
    freqText: String,
    medidoValue: String,
    planValue: String,
    isError: Boolean,
    onMedidoChange: (String) -> Unit,
    onPlanChange: (String) -> Unit
) {
    val med = medidoValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val plan = planValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val absDiff = if (med != null && plan != null) kotlin.math.abs(med - plan) else null
    val needsAttention = (absDiff != null && absDiff >= 2.0) || (med != null && med < 15.0)
    val medColor = if (needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)
        Text(freqText, modifier = Modifier.width(90.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        DbmvField(
            label = "",
            value = medidoValue,
            modifier = Modifier.width(88.dp),
            compact = true,
            isError = isError,
            textColor = medColor,
            onChange = onMedidoChange
        )
        Spacer(Modifier.width(8.dp))
        DbmvField(
            label = "",
            value = planValue,
            modifier = Modifier.width(88.dp),
            compact = true,
            isError = false,
            textColor = MaterialTheme.colorScheme.onSurface,
            onChange = onPlanChange
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun EntradaRowWithFreqSelector(
    canal: String,
    freqMHz: Int,
    optionsMHz: List<Int>,
    onFreqChange: (Int) -> Unit,
    medidoValue: String,
    planValue: String,
    isError: Boolean,
    onMedidoChange: (String) -> Unit,
    onPlanChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val med = medidoValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val plan = planValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val absDiff = if (med != null && plan != null) kotlin.math.abs(med - plan) else null
    val needsAttention = (absDiff != null && absDiff >= 2.0) || (med != null && med < 15.0)
    val medColor = if (needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)

        Box(modifier = Modifier.width(90.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$freqMHz MHz",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Cambiar frecuencia",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                optionsMHz.forEach { f ->
                    DropdownMenuItem(
                        text = { Text("$f MHz") },
                        onClick = { onFreqChange(f); expanded = false }
                    )
                }
            }
        }

        DbmvField(
            label = "",
            value = medidoValue,
            modifier = Modifier.width(88.dp),
            compact = true,
            isError = isError,
            textColor = medColor,
            onChange = onMedidoChange
        )
        Spacer(Modifier.width(8.dp))
        DbmvField(
            label = "",
            value = planValue,
            modifier = Modifier.width(88.dp),
            compact = true,
            isError = false,
            textColor = MaterialTheme.colorScheme.onSurface,
            onChange = onPlanChange
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SimpleCalcList(rows: List<CalcRowData>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("CANAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("FREQ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("CALC (dBmV)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    rows.forEachIndexed { idx, r ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(r.canal, modifier = Modifier.width(70.dp), fontWeight = FontWeight.SemiBold)
            Text(r.freqText, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Text(
                r.calc?.let { "${CiscoHfcAmpCalculator.format1(it)}" } ?: "—",
                modifier = Modifier.width(80.dp),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (idx != rows.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun CompareRow(
    canal: String,
    freqText: String,
    calc: Double?,
    medidoText: String,
    isRequiredError: Boolean,
    onMedidoChange: (String) -> Unit
) {
    val med = medidoText.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val delta = if (calc != null && med != null) med - calc else null
    val absDelta = delta?.let { kotlin.math.abs(it) }
    val isBad = absDelta != null && absDelta > 1.2
    val isOk = absDelta != null && absDelta <= 0.5
    val deltaLabel = delta?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(54.dp), fontWeight = FontWeight.SemiBold)
        Text(freqText, modifier = Modifier.width(78.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Text(
            calc?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—",
            modifier = Modifier.width(54.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        DbmvField(
            label = "",
            value = medidoText,
            modifier = Modifier.width(70.dp),
            compact = true,
            compactHeight = 36.dp,
            isError = isRequiredError,
            onChange = onMedidoChange
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${if (delta != null && delta >= 0) "+" else ""}$deltaLabel",
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            color = if (delta == null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            else if (isOk) Color(0xFF2E7D32) else if (isBad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun RecommendationLine(label: String, value: Double?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(
            value?.let { "${CiscoHfcAmpCalculator.format1(it)} dB" } ?: "—",
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun HeaderRow(c1: String = "CANAL", c2: String = "FRECUENCIA", c3: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(c1, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(c2, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(c3, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("", modifier = Modifier.width(44.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EntradaHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("CANAL", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("FREQ", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("Medido (dBmV)", modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("Plano (dBmV)", modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CompareHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("CANAL", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("FREQ", modifier = Modifier.width(78.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("CALC", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        Spacer(Modifier.width(6.dp))
        Text("medido", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("DIF", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
}

@file:Suppress("DEPRECATION")

package com.example.fieldmaintenance.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.sp
import com.example.fieldmaintenance.data.model.AmplifierAdjustment
import com.example.fieldmaintenance.data.model.AmplifierMode
import com.example.fieldmaintenance.data.model.Frequency
import com.example.fieldmaintenance.data.model.label
import com.example.fieldmaintenance.ui.theme.OutlookDarkSuccess
import com.example.fieldmaintenance.ui.theme.OutlookLightSuccess
import com.example.fieldmaintenance.util.CiscoHfcAmpCalculator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AmpColors(
    val card: Color,
    val stroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val error: Color,
    val success: Color
)

@Composable
private fun amplifierColors(): AmpColors {
    val scheme = MaterialTheme.colorScheme
    val success = if (isSystemInDarkTheme()) OutlookDarkSuccess else OutlookLightSuccess
    return AmpColors(
        card = scheme.surface,
        stroke = scheme.outline,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        error = scheme.error,
        success = success
    )
}

@Composable
fun AmplifierAdjustmentCard(
    assetId: String,
    bandwidth: Frequency?,
    amplifierMode: AmplifierMode?,
    initial: AmplifierAdjustment?,
    showRequiredErrors: Boolean,
    onCurrentChange: (AmplifierAdjustment) -> Unit,
    onPersist: suspend (AmplifierAdjustment) -> Unit
) {
    val colors = amplifierColors()
    var entradaAlert by remember(assetId) { mutableStateOf<EntradaAlert?>(null) }

    var dirty by rememberSaveable(assetId) { mutableStateOf(false) }

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

    }

    // Outer "frame" for the whole module (visual border)
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.stroke),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                "Ajuste de Amplificador",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            SectionCard(
                titleBold = "Niveles ENTRADA",
                titleLight = ""
            ) {
                    Text(
                        "FWD IN PAD: ${pad?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Text(
                        "${if (tilt != null && tilt > 0) "FWD IN invEQ" else "FWD IN EQ"}: ${tilt?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    // Header row like the reference (CANAL / FRECUENCIA / AMPLITUD / PLANO / DIF)
                    EntradaHeaderRow()
                    EntradaRowPlan(
                        canal = "CH50",
                        freqText = "379",
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
                            CalcRowData("L 54", "54", entradaCalc?.get("L 54")),
                            CalcRowData("L102", "102", entradaCalc?.get("L102")),
                            CalcRowData("CH3", "61", entradaCalc?.get("CH3")),
                            CalcRowData("CH50", "379", entradaCalc?.get("CH50")),
                            CalcRowData("CH70", "495", entradaCalc?.get("CH70")),
                            CalcRowData("CH116", "750", entradaCalc?.get("CH116")),
                            CalcRowData("CH136", "870", entradaCalc?.get("CH136")),
                            CalcRowData("CH158", "1000", entradaCalc?.get("CH158")),
                        )
                    )
            }

            SectionCard(
                titleBold = "Niveles SALIDA",
                titleLight = ""
            ) {
                    Text(
                        "AGC IN PAD: ${agc?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    if (!entradaValid) {
                        Text(
                            "Complete mediciones de entrada válidas para continuar. La diferencia entre el nivel de entrada y medido aceptable es menor a 4. Nivel minimo de entrada permitido es 15 dBmV si esta indicado por plano.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error
                        )
                        return@SectionCard
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
                            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error)
                            Text(
                                "Revisa amplitudes (-20 a 80 dBmV).",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SalidaHeaderRow()
                    SalidaCompareRow(
                        canal = "L54",
                        freqText = "54",
                        calc = salidaCalc?.get("L54")
                    )
                    SalidaCompareRow(
                        canal = "L102",
                        freqText = "102",
                        calc = salidaCalc?.get("L102")
                    )
                    SalidaCompareRow(
                        canal = "CH3",
                        freqText = "61",
                        calc = salidaCalc?.get("CH3")
                    )
                    SalidaCompareRow(
                        canal = "CH50",
                        freqText = "379",
                        calc = salidaCalc?.get("CH50"),
                        medidoText = outCh50,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh50) == null,
                        onMedidoChange = { dirty = true; outCh50 = it }
                    )
                    SalidaCompareRow(
                        canal = "CH70",
                        freqText = "495",
                        calc = salidaCalc?.get("CH70"),
                        medidoText = outCh70,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh70) == null,
                        onMedidoChange = { dirty = true; outCh70 = it }
                    )
                    SalidaCompareRow(
                        canal = "CH110",
                        freqText = "711",
                        calc = salidaCalc?.get("CH110"),
                        medidoText = outCh110,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh110) == null,
                        onMedidoChange = { dirty = true; outCh110 = it }
                    )
                    SalidaCompareRow(
                        canal = "CH116",
                        freqText = "750",
                        calc = salidaCalc?.get("CH116"),
                        medidoText = outCh116,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh116) == null,
                        onMedidoChange = { dirty = true; outCh116 = it }
                    )
                    SalidaCompareRow(
                        canal = "CH136",
                        freqText = "870",
                        calc = salidaCalc?.get("CH136"),
                        medidoText = outCh136,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh136) == null,
                        onMedidoChange = { dirty = true; outCh136 = it }
                    )
                    SalidaCompareRow(
                        canal = "CH158",
                        freqText = "1000",
                        calc = salidaCalc?.get("CH158")
                    )
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
    val colors = amplifierColors()
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
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor ?: colors.textPrimary)
        )
        return
    }

    // Compact field: avoids text clipping and forces visible text color.
    val borderColor = if (isError) colors.error else colors.stroke
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Spacer(Modifier.height(2.dp))
        }
        Surface(
            modifier = Modifier.height(compactHeight),
            color = colors.card,
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
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor ?: colors.textPrimary),
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
private fun SectionCard(
    titleBold: String,
    titleLight: String,
    content: @Composable () -> Unit
) {
    val colors = amplifierColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.stroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (titleBold.isNotBlank()) {
                    Text(
                        titleBold,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                }
                if (titleLight.isNotBlank()) {
                    Text(" $titleLight", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                }
            }
            if (titleBold.isNotBlank() || titleLight.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
            }
            content()
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
    val colors = amplifierColors()
    val med = medidoValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val plan = planValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val absDiff = if (med != null && plan != null) kotlin.math.abs(med - plan) else null
    val needsAttention = (absDiff != null && absDiff >= 2.0) || (med != null && med < 15.0)
    val medColor = if (needsAttention) colors.error else colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)
        Text(freqText, modifier = Modifier.width(90.dp), color = colors.textSecondary, fontSize = 12.sp)
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
            textColor = colors.textPrimary,
            onChange = onPlanChange
        )
    }
    HorizontalDivider(color = colors.stroke, thickness = 1.dp)
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
    val colors = amplifierColors()
    var expanded by remember { mutableStateOf(false) }
    val med = medidoValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val plan = planValue.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val absDiff = if (med != null && plan != null) kotlin.math.abs(med - plan) else null
    val needsAttention = (absDiff != null && absDiff >= 2.0) || (med != null && med < 15.0)
    val medColor = if (needsAttention) colors.error else colors.textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)

        Box(modifier = Modifier.width(90.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$freqMHz",
                    modifier = Modifier.weight(1f),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Cambiar frecuencia",
                    tint = colors.textSecondary
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
            textColor = colors.textPrimary,
            onChange = onPlanChange
        )
    }
    HorizontalDivider(color = colors.stroke, thickness = 1.dp)
}

@Composable
private fun SimpleCalcList(rows: List<CalcRowData>) {
    val colors = amplifierColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("CANAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Text("FREQ (MHz)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Text("CALC (dBmV)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
    rows.forEachIndexed { idx, r ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(r.canal, modifier = Modifier.width(70.dp), fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)
            Text(r.freqText, modifier = Modifier.weight(1f), color = colors.textSecondary, fontSize = 12.sp)
            Text(
                r.calc?.let { "${CiscoHfcAmpCalculator.format1(it)}" } ?: "—",
                modifier = Modifier.width(80.dp),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                fontSize = 12.sp
            )
        }
        if (idx != rows.lastIndex) {
            HorizontalDivider(color = colors.stroke, thickness = 1.dp)
        }
    }
}

@Composable
private fun SalidaCompareRow(
    canal: String,
    freqText: String,
    calc: Double?,
    medidoText: String? = null,
    isRequiredError: Boolean = false,
    onMedidoChange: ((String) -> Unit)? = null
) {
    val colors = amplifierColors()
    val hasInput = medidoText != null && onMedidoChange != null
    val med = medidoText?.trim()?.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
    val delta = if (hasInput && calc != null && med != null) med - calc else null
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
        Text(canal, modifier = Modifier.width(54.dp), fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = 12.sp)
        Text(freqText, modifier = Modifier.width(78.dp), color = colors.textSecondary, fontSize = 12.sp)
        Text(
            calc?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—",
            modifier = Modifier.width(54.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            fontSize = 12.sp
        )
        Spacer(Modifier.width(6.dp))
        if (hasInput) {
            DbmvField(
                label = "",
                value = medidoText.orEmpty(),
                modifier = Modifier.width(70.dp),
                compact = true,
                compactHeight = 36.dp,
                isError = isRequiredError,
                onChange = { onMedidoChange?.invoke(it) }
            )
        } else {
            Text(
                "—",
                modifier = Modifier.width(70.dp),
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${if (delta != null && delta >= 0) "+" else ""}$deltaLabel",
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            color = if (delta == null) colors.textSecondary
            else if (isOk) colors.success else if (isBad) colors.error else colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
    HorizontalDivider(color = colors.stroke, thickness = 1.dp)
}

@Composable
private fun RecommendationLine(label: String, value: Double?) {
    val colors = amplifierColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        Text(
            value?.let { "${CiscoHfcAmpCalculator.format1(it)} dB" } ?: "—",
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
    HorizontalDivider(color = colors.stroke, thickness = 1.dp)
}

@Composable
private fun HeaderRow(c1: String = "CANAL", c2: String = "FRECUENCIA", c3: String) {
    val colors = amplifierColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(c1, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
        Text(c2, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
        Text(c3, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
        Text("", modifier = Modifier.width(44.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EntradaHeaderRow() {
    val colors = amplifierColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("CANAL", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Text("FREQ (MHz)", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Text("Medido (dBmV)", modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Text("Plano (dBmV)", modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SalidaHeaderRow() {
    val colors = amplifierColors()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("CANAL", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Text("FREQ (MHz)", modifier = Modifier.width(78.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Text("CALC (dBmV)", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text("medido (dBmV)", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Text("DIF", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = colors.textSecondary, fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
}

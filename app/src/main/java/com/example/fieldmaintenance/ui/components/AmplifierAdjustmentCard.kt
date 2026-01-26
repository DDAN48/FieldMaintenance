@file:Suppress("DEPRECATION")

package com.example.fieldmaintenance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
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
import com.example.fieldmaintenance.util.CiscoHfcAmpCalculator
import com.example.fieldmaintenance.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun ampCardColor(): Color = MaterialTheme.colorScheme.surface

@Composable
private fun ampHeaderColor(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
private fun ampStrokeColor(): Color = MaterialTheme.colorScheme.outline

@Composable
private fun ampDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
private fun ampTextPrimary(): Color = MaterialTheme.colorScheme.onSurface

@Composable
private fun ampTextSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun ampErrorColor(): Color = MaterialTheme.colorScheme.error

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
    var entradaAlert by remember(assetId) { mutableStateOf<EntradaAlert?>(null) }

    var dirty by rememberSaveable(assetId) { mutableStateOf(false) }

    fun parseDbmv(text: String): Double? =
        text.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()

    fun fmt(text: Double?): String = text?.let { CiscoHfcAmpCalculator.format1(it) } ?: ""

    // Inputs (strings to preserve comma/typing)
    var inCh50 by rememberSaveable { mutableStateOf("") }
    var inHigh by rememberSaveable { mutableStateOf("") }
    var inLowFreq by rememberSaveable { mutableStateOf<Int?>(379) } // 379 (CH50) or 61 (CH3)
    var inHighFreq by rememberSaveable { mutableStateOf<Int?>(870) } // 750 (CH116), 870 (CH136) or 1000 (CH158)
    var inPlanCh50 by rememberSaveable { mutableStateOf("") }
    var inPlanHigh by rememberSaveable { mutableStateOf("") }
    var inPlanLowFreq by rememberSaveable { mutableStateOf<Int?>(379) }
    var inPlanHighFreq by rememberSaveable { mutableStateOf<Int?>(870) }

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
        inLowFreq = initial.inputLowFreqMHz ?: 379
        inHighFreq = initial.inputHighFreqMHz ?: 870
        inPlanCh50 = fmt(initial.inputPlanCh50Dbmv)
        inPlanHigh = fmt(initial.inputPlanHighDbmv)
        inPlanLowFreq = initial.inputPlanLowFreqMHz ?: 379
        inPlanHighFreq = initial.inputPlanHighFreqMHz ?: 870
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
            inputLowFreqMHz = inLowFreq,
            inputHighFreqMHz = inHighFreq,
            inputPlanCh50Dbmv = parseDbmv(inPlanCh50),
            inputPlanHighDbmv = parseDbmv(inPlanHigh),
            inputPlanLowFreqMHz = inPlanLowFreq,
            inputPlanHighFreqMHz = inPlanHighFreq,
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
        adj.inputCh116Dbmv != null
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
        val lowMed = parseDbmv(inCh50)
        val highMed = parseDbmv(inHigh)
        val lowPlan = parseDbmv(inPlanCh50)
        val highPlan = parseDbmv(inPlanHigh)
        val lowCalc = CiscoHfcAmpCalculator.entradaCalcValueForFreq(adj, inPlanLowFreq ?: inLowFreq)
        val highCalc = CiscoHfcAmpCalculator.entradaCalcValueForFreq(adj, inPlanHighFreq ?: inHighFreq)
        val lowOk = lowMed != null && lowMed >= 15.0 && lowPlan != null && lowCalc != null &&
            kotlin.math.abs(lowCalc - lowPlan) < 4.0
        val highOk = highMed != null && highMed >= 15.0 && highPlan != null && highCalc != null &&
            kotlin.math.abs(highCalc - highPlan) < 4.0
        lowOk && highOk
    }

    fun isWeirdDbmv(v: Double?): Boolean = v != null && (v < -20.0 || v > 80.0)
    fun maybeTriggerEntradaAlert(
        canal: String,
        med: Double?,
        plan: Double? = null,
        calc: Double? = null
    ) {
        if (med == null) return
        val delta = if (plan != null && calc != null) kotlin.math.abs(calc - plan) else null
        val needsAlert = med < 15.0 || (delta != null && delta >= 4.0)
        if (!needsAlert) return
        val planLabel = plan?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"
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
        border = BorderStroke(1.dp, ampStrokeColor()),
        colors = CardDefaults.cardColors(containerColor = ampCardColor()),
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
                color = ampTextPrimary()
            )
            SectionCard(
                titleBold = "Niveles ENTRADA",
                titleLight = ""
            ) {
                    Text(
                        "FWD IN PAD: ${pad?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ampTextSecondary()
                    )
                    Text(
                        "${if (tilt != null && tilt > 0) "FWD IN invEQ" else "FWD IN EQ"}: ${tilt?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ampTextSecondary()
                    )
                    Spacer(Modifier.height(6.dp))
                    EntradaHeaderRow(title = "Medido (dBmV)")
                    val lowMeasuredFreq = inLowFreq ?: 379
                    val lowMeasuredCanal = CiscoHfcAmpCalculator.inputChannelLabelForFreq(lowMeasuredFreq)
                    val lowMedValue = parseDbmv(inCh50)
                    EntradaRowSingleValueWithFreqSelector(
                        canal = lowMeasuredCanal,
                        freqMHz = lowMeasuredFreq,
                        optionsMHz = listOf(61, 379),
                        onFreqChange = { dirty = true; inLowFreq = it },
                        value = inCh50,
                        isError = showRequiredErrors && parseDbmv(inCh50) == null,
                        valueColor = if (lowMedValue != null && lowMedValue < 15.0) ampErrorColor() else ampTextPrimary(),
                        onValueChange = { dirty = true; inCh50 = it }
                    )
                    val highMeasuredFreq = inHighFreq ?: 870
                    val highMeasuredCanal = CiscoHfcAmpCalculator.inputChannelLabelForFreq(highMeasuredFreq)
                    val highMedValue = parseDbmv(inHigh)
                    EntradaRowSingleValueWithFreqSelector(
                        canal = highMeasuredCanal,
                        freqMHz = highMeasuredFreq,
                        optionsMHz = listOf(750, 870, 1000),
                        onFreqChange = { dirty = true; inHighFreq = it },
                        value = inHigh,
                        isError = showRequiredErrors && parseDbmv(inHigh) == null,
                        valueColor = if (highMedValue != null && highMedValue < 15.0) ampErrorColor() else ampTextPrimary(),
                        onValueChange = { dirty = true; inHigh = it }
                    )

                    EntradaHeaderRow(title = "Plano (dBmV)")
                    val lowPlanFreq = inPlanLowFreq ?: lowMeasuredFreq
                    val lowPlanCanal = CiscoHfcAmpCalculator.inputChannelLabelForFreq(lowPlanFreq)
                    val lowPlanCalc = CiscoHfcAmpCalculator.entradaCalcValueForFreq(adj, lowPlanFreq)
                    val lowPlanValue = parseDbmv(inPlanCh50)
                    val lowPlanDiff = if (lowPlanCalc != null && lowPlanValue != null) kotlin.math.abs(lowPlanCalc - lowPlanValue) else null
                    EntradaRowSingleValueWithFreqSelector(
                        canal = lowPlanCanal,
                        freqMHz = lowPlanFreq,
                        optionsMHz = listOf(61, 379),
                        onFreqChange = { dirty = true; inPlanLowFreq = it },
                        value = inPlanCh50,
                        isError = showRequiredErrors && lowPlanValue == null,
                        valueColor = if (lowPlanDiff != null && lowPlanDiff >= 2.0) ampErrorColor() else ampTextPrimary(),
                        onValueChange = { dirty = true; inPlanCh50 = it }
                    )
                    LaunchedEffect(inPlanCh50, inPlanLowFreq, entradaCalc) {
                        maybeTriggerEntradaAlert(
                            canal = lowPlanCanal,
                            med = parseDbmv(inCh50),
                            plan = lowPlanValue,
                            calc = lowPlanCalc
                        )
                    }

                    val highPlanFreq = inPlanHighFreq ?: highMeasuredFreq
                    val highPlanCanal = CiscoHfcAmpCalculator.inputChannelLabelForFreq(highPlanFreq)
                    val highPlanCalc = CiscoHfcAmpCalculator.entradaCalcValueForFreq(adj, highPlanFreq)
                    val highPlanValue = parseDbmv(inPlanHigh)
                    val highPlanDiff = if (highPlanCalc != null && highPlanValue != null) kotlin.math.abs(highPlanCalc - highPlanValue) else null
                    EntradaRowSingleValueWithFreqSelector(
                        canal = highPlanCanal,
                        freqMHz = highPlanFreq,
                        optionsMHz = listOf(750, 870, 1000),
                        onFreqChange = { dirty = true; inPlanHighFreq = it },
                        value = inPlanHigh,
                        isError = showRequiredErrors && highPlanValue == null,
                        valueColor = if (highPlanDiff != null && highPlanDiff >= 2.0) ampErrorColor() else ampTextPrimary(),
                        onValueChange = { dirty = true; inPlanHigh = it }
                    )
                    LaunchedEffect(inPlanHigh, inPlanHighFreq, entradaCalc) {
                        maybeTriggerEntradaAlert(
                            canal = highPlanCanal,
                            med = parseDbmv(inHigh),
                            plan = highPlanValue,
                            calc = highPlanCalc
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
                        color = ampTextSecondary()
                    )
                    Spacer(Modifier.height(6.dp))
                    if (!entradaValid) {
                        Text(
                            "Complete mediciones de entrada válidas para continuar. La diferencia entre el nivel calculado de entrada y el plano aceptable es menor a 4. Nivel minimo de entrada permitido es 15 dBmV si esta indicado por plano.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ampErrorColor()
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
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ampErrorColor())
                            Text(
                                "Revisa amplitudes (-20 a 80 dBmV).",
                                style = MaterialTheme.typography.bodySmall,
                                color = ampErrorColor()
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
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor ?: ampTextPrimary())
        )
        return
    }

    // Compact field: avoids text clipping and forces visible text color.
    val borderColor = if (isError) ampErrorColor() else ampStrokeColor()
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ampTextSecondary())
            Spacer(Modifier.height(2.dp))
        }
        Surface(
            modifier = Modifier.height(compactHeight),
            color = ampCardColor(),
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
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor ?: ampTextPrimary()),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ampCardColor()),
        border = BorderStroke(1.dp, ampStrokeColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (titleBold.isNotBlank()) {
                    Text(
                        titleBold,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = ampTextPrimary()
                    )
                }
                if (titleLight.isNotBlank()) {
                    Text(" $titleLight", style = MaterialTheme.typography.titleSmall, color = ampTextPrimary())
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
private fun EntradaRowSingleValueWithFreqSelector(
    canal: String,
    freqMHz: Int,
    optionsMHz: List<Int>,
    onFreqChange: (Int) -> Unit,
    value: String,
    isError: Boolean,
    valueColor: Color,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold, color = ampTextPrimary(), fontSize = 12.sp)

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
                    color = ampTextSecondary(),
                    fontSize = 12.sp
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Cambiar frecuencia",
                    tint = ampTextSecondary()
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
            value = value,
            modifier = Modifier.width(120.dp),
            compact = true,
            isError = isError,
            textColor = valueColor,
            onChange = onValueChange
        )
    }
    HorizontalDivider(color = ampDividerColor(), thickness = 1.dp)
}

@Composable
private fun SimpleCalcList(rows: List<CalcRowData>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ampHeaderColor())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("CANAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Text("FREQ (MHz)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Text("CALC (dBmV)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
    rows.forEachIndexed { idx, r ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(r.canal, modifier = Modifier.width(70.dp), fontWeight = FontWeight.SemiBold, color = ampTextPrimary(), fontSize = 12.sp)
            Text(r.freqText, modifier = Modifier.weight(1f), color = ampTextSecondary(), fontSize = 12.sp)
            Text(
                r.calc?.let { "${CiscoHfcAmpCalculator.format1(it)}" } ?: "—",
                modifier = Modifier.width(80.dp),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold,
                color = ampTextPrimary(),
                fontSize = 12.sp
            )
        }
        if (idx != rows.lastIndex) {
            HorizontalDivider(color = ampDividerColor(), thickness = 1.dp)
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
        Text(canal, modifier = Modifier.width(54.dp), fontWeight = FontWeight.SemiBold, color = ampTextPrimary(), fontSize = 12.sp)
        Text(freqText, modifier = Modifier.width(78.dp), color = ampTextSecondary(), fontSize = 12.sp)
        Text(
            calc?.let { CiscoHfcAmpCalculator.format1(it) } ?: "—",
            modifier = Modifier.width(54.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
            color = ampTextPrimary(),
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
                color = ampTextSecondary(),
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${if (delta != null && delta >= 0) "+" else ""}$deltaLabel",
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            color = if (delta == null) ampTextSecondary()
            else if (isOk) SuccessGreen else if (isBad) ampErrorColor() else ampTextSecondary(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
    HorizontalDivider(color = ampDividerColor(), thickness = 1.dp)
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
        Text(label, fontWeight = FontWeight.SemiBold, color = ampTextPrimary())
        Text(
            value?.let { "${CiscoHfcAmpCalculator.format1(it)} dB" } ?: "—",
            fontWeight = FontWeight.SemiBold,
            color = ampTextPrimary()
        )
    }
    HorizontalDivider(color = ampDividerColor(), thickness = 1.dp)
}

@Composable
private fun HeaderRow(c1: String = "CANAL", c2: String = "FRECUENCIA", c3: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ampHeaderColor())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(c1, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary())
        Text(c2, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary())
        Text(c3, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary())
        Text("", modifier = Modifier.width(44.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EntradaHeaderRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ampHeaderColor())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("CANAL", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Text("FREQ (MHz)", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Text(title, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SalidaHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ampHeaderColor())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("CANAL", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Text("FREQ (MHz)", modifier = Modifier.width(78.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Text("CALC (dBmV)", modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = ampTextSecondary(), fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text("medido (dBmV)", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ampTextSecondary(), fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Text("DIF", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = ampTextSecondary(), fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
}

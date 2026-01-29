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
import androidx.compose.ui.text.TextStyle
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
    onCurrentChange: (AmplifierAdjustment) -> Unit,
    onPersist: suspend (AmplifierAdjustment) -> Unit
) {
    // Global collapse for the whole module (user can collapse everything)
    var moduleExpanded by rememberSaveable(assetId) { mutableStateOf(true) }

    // Individual section collapse states (all open by default)
    var entradaExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var salidaPlanoExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var salidaCalcExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var compareExpanded by rememberSaveable(assetId) { mutableStateOf(true) }
    var recoExpanded by rememberSaveable(assetId) { mutableStateOf(true) }

    var dirty by rememberSaveable(assetId) { mutableStateOf(false) }

    fun parseDbmv(text: String): Double? =
        text.trim().takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()

    fun fmt(text: Double?): String = text?.let { CiscoHfcAmpCalculator.format1(it) } ?: ""

    // Niveles ENTRADA table: selectable 2 cells per column.
    // Medido: only CH50/CH116/CH136 are eligible for selection/edit.
    // Plano: CH3/CH50/CH116/CH136/CH158 are eligible for selection/edit.
    val entradaRows = remember {
        listOf(
            EntradaRow("L 54", 54),
            EntradaRow("L102", 102),
            EntradaRow("CH3", 61),
            EntradaRow("CH50", 379),
            EntradaRow("CH70", 495),
            EntradaRow("CH116", 750),
            EntradaRow("CH136", 870),
            EntradaRow("CH158", 1000),
        )
    }
    val medidoEligibleFreq = remember { setOf(379, 750, 870) }
    val planoEligibleFreq = remember { setOf(61, 379, 750, 870, 1000) }

    // Keep as Strings for typing (commas etc). We store only the 2 selected points per column.
    var medSelOrder by rememberSaveable { mutableStateOf(listOf<Int>()) } // freq MHz in selection order (max 2)
    var planSelOrder by rememberSaveable { mutableStateOf(listOf<Int>()) } // freq MHz in selection order (max 2)

    // Store values per eligible frequency to avoid shifting bugs when selection order changes.
    var medVal379 by rememberSaveable { mutableStateOf("") }
    var medVal750 by rememberSaveable { mutableStateOf("") }
    var medVal870 by rememberSaveable { mutableStateOf("") }

    var planVal61 by rememberSaveable { mutableStateOf("") }
    var planVal379 by rememberSaveable { mutableStateOf("") }
    var planVal750 by rememberSaveable { mutableStateOf("") }
    var planVal870 by rememberSaveable { mutableStateOf("") }
    var planVal1000 by rememberSaveable { mutableStateOf("") }

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

        // New fields first
        val m1f = initial.inMedidoP1FreqMHz
        val m2f = initial.inMedidoP2FreqMHz
        val p1f = initial.inPlanoP1FreqMHz
        val p2f = initial.inPlanoP2FreqMHz

        fun setMedValue(freq: Int, value: String) {
            when (freq) {
                379 -> medVal379 = value
                750 -> medVal750 = value
                870 -> medVal870 = value
            }
        }
        fun setPlanValue(freq: Int, value: String) {
            when (freq) {
                61 -> planVal61 = value
                379 -> planVal379 = value
                750 -> planVal750 = value
                870 -> planVal870 = value
                1000 -> planVal1000 = value
            }
        }

        // If new fields exist, use them; else migrate from legacy (CH50 + high)
        if (m1f != null) setMedValue(m1f, fmt(initial.inMedidoP1Dbmv))
        if (m2f != null) setMedValue(m2f, fmt(initial.inMedidoP2Dbmv))
        if (m1f != null && m2f != null) {
            medSelOrder = listOf(m1f, m2f).distinct().filter { medidoEligibleFreq.contains(it) }.take(2)
        } else {
            val legacyHighFreq = initial.inputHighFreqMHz ?: 750
            medSelOrder = listOf(379, legacyHighFreq).distinct().filter { medidoEligibleFreq.contains(it) }.take(2)
            setMedValue(379, fmt(initial.inputCh50Dbmv))
            setMedValue(legacyHighFreq, fmt(initial.inputCh116Dbmv))
        }

        if (p1f != null) setPlanValue(p1f, fmt(initial.inPlanoP1Dbmv))
        if (p2f != null) setPlanValue(p2f, fmt(initial.inPlanoP2Dbmv))
        if (p1f != null && p2f != null) {
            planSelOrder = listOf(p1f, p2f).distinct().filter { planoEligibleFreq.contains(it) }.take(2)
        } else {
            // Leave Plano empty for old DBs
            planSelOrder = emptyList()
            planVal61 = ""
            planVal379 = ""
            planVal750 = ""
            planVal870 = ""
            planVal1000 = ""
        }

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

    fun getMedValue(freq: Int): String = when (freq) {
        379 -> medVal379
        750 -> medVal750
        870 -> medVal870
        else -> ""
    }
    fun setMedValue(freq: Int, value: String) {
        when (freq) {
            379 -> medVal379 = value
            750 -> medVal750 = value
            870 -> medVal870 = value
        }
    }

    fun getPlanValue(freq: Int): String = when (freq) {
        61 -> planVal61
        379 -> planVal379
        750 -> planVal750
        870 -> planVal870
        1000 -> planVal1000
        else -> ""
    }
    fun setPlanValue(freq: Int, value: String) {
        when (freq) {
            61 -> planVal61 = value
            379 -> planVal379 = value
            750 -> planVal750 = value
            870 -> planVal870 = value
            1000 -> planVal1000 = value
        }
    }

    fun updateSelection(order: List<Int>, freq: Int): List<Int> {
        return if (order.contains(freq)) {
            order.filterNot { it == freq }
        } else {
            if (order.size < 2) order + freq else (order.drop(1) + freq)
        }
    }

    fun buildAdjustment(): AmplifierAdjustment {
        val medF1 = medSelOrder.getOrNull(0)
        val medF2 = medSelOrder.getOrNull(1)
        val plF1 = planSelOrder.getOrNull(0)
        val plF2 = planSelOrder.getOrNull(1)

        return AmplifierAdjustment(
            assetId = assetId,
            inMedidoP1FreqMHz = medF1,
            inMedidoP1Dbmv = medF1?.let { parseDbmv(getMedValue(it)) },
            inMedidoP2FreqMHz = medF2,
            inMedidoP2Dbmv = medF2?.let { parseDbmv(getMedValue(it)) },

            inPlanoP1FreqMHz = plF1,
            inPlanoP1Dbmv = plF1?.let { parseDbmv(getPlanValue(it)) },
            inPlanoP2FreqMHz = plF2,
            inPlanoP2Dbmv = plF2?.let { parseDbmv(getPlanValue(it)) },

            // Legacy fields: fill only if selection matches old schema (CH50 + high)
            inputCh50Dbmv = if (medF1 == 379 || medF2 == 379) parseDbmv(medVal379) else null,
            inputCh116Dbmv = when {
                medF1 == 750 || medF2 == 750 -> parseDbmv(medVal750)
                medF1 == 870 || medF2 == 870 -> parseDbmv(medVal870)
                else -> null
            },
            inputHighFreqMHz = when {
                medF1 == 750 || medF1 == 870 -> medF1
                medF2 == 750 || medF2 == 870 -> medF2
                else -> null
            },
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
    val entradaCalc = CiscoHfcAmpCalculator.nivelesEntradaCalculados(adj)
    val entradaPlanoCalc = CiscoHfcAmpCalculator.nivelesEntradaPlanoCalculados(adj)
    val salidaCalc = CiscoHfcAmpCalculator.nivelesSalidaCalculados(adj)
    val tilt = CiscoHfcAmpCalculator.fwdInEqTilt(adj, bandwidth)
    val pad = CiscoHfcAmpCalculator.fwdInPad(adj, bandwidth, amplifierMode)
    val agc = CiscoHfcAmpCalculator.agcPad(adj, bandwidth, amplifierMode)

    fun isWeirdDbmv(v: Double?): Boolean = v != null && (v < -20.0 || v > 80.0)

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
                    EntradaTableHeader()
                    entradaRows.forEach { r ->
                        val medSelected = medSelOrder.contains(r.freqMHz) && medidoEligibleFreq.contains(r.freqMHz)
                        val planSelected = planSelOrder.contains(r.freqMHz) && planoEligibleFreq.contains(r.freqMHz)
                        val medEditable = medSelected
                        val planEditable = planSelected

                        val medValueText = if (medSelected) getMedValue(r.freqMHz)
                        else entradaCalc?.get(r.canal)?.let { CiscoHfcAmpCalculator.format1(it) } ?: ""

                        val planValueText = if (planSelected) getPlanValue(r.freqMHz)
                        else entradaPlanoCalc?.get(r.canal)?.let { CiscoHfcAmpCalculator.format1(it) } ?: ""

                        EntradaTableRow(
                            canal = r.canal,
                            freqText = "${r.freqMHz}",
                            medidoText = medValueText,
                            planoText = planValueText,
                            medidoEditable = medEditable,
                            planoEditable = planEditable,
                            medidoSelected = medSelected,
                            planoSelected = planSelected,
                            onMedidoClick = {
                                if (!medidoEligibleFreq.contains(r.freqMHz)) return@EntradaTableRow
                                dirty = true
                                val next = updateSelection(medSelOrder, r.freqMHz)
                                // Drop any non-eligible selection just in case (requirement)
                                medSelOrder = next.filter { medidoEligibleFreq.contains(it) }.take(2)
                            },
                            onPlanoClick = {
                                if (!planoEligibleFreq.contains(r.freqMHz)) return@EntradaTableRow
                                dirty = true
                                planSelOrder = updateSelection(planSelOrder, r.freqMHz).filter { planoEligibleFreq.contains(it) }.take(2)
                            },
                            onMedidoChange = { txt ->
                                dirty = true
                                setMedValue(r.freqMHz, txt)
                            },
                            onPlanoChange = { txt ->
                                dirty = true
                                setPlanValue(r.freqMHz, txt)
                            },
                            showRequiredErrors = showRequiredErrors,
                            medidoError = medEditable && showRequiredErrors && parseDbmv(medValueText) == null,
                            planoError = planEditable && showRequiredErrors && parseDbmv(planValueText) == null,
                        )
                    }
                }

                CollapsibleSection(
                    titleBold = "Niveles SALIDA",
                    titleLight = "",
                    expanded = salidaPlanoExpanded,
                    onToggle = { salidaPlanoExpanded = !salidaPlanoExpanded }
                ) {
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
                    CompareHeaderRow()
                    CompareRow(
                        canal = "CH50",
                        freqText = "379 MHz",
                        calc = salidaCalc?.get("CH50"),
                        medidoText = outCh50,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh50) == null,
                        onMedidoChange = { dirty = true; outCh50 = it }
                    )
                    CompareRow(
                        canal = "CH70",
                        freqText = "495 MHz",
                        calc = salidaCalc?.get("CH70"),
                        medidoText = outCh70,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh70) == null,
                        onMedidoChange = { dirty = true; outCh70 = it }
                    )
                    CompareRow(
                        canal = "CH110",
                        freqText = "711 MHz",
                        calc = salidaCalc?.get("CH110"),
                        medidoText = outCh110,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh110) == null,
                        onMedidoChange = { dirty = true; outCh110 = it }
                    )
                    CompareRow(
                        canal = "CH116",
                        freqText = "750 MHz",
                        calc = salidaCalc?.get("CH116"),
                        medidoText = outCh116,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh116) == null,
                        onMedidoChange = { dirty = true; outCh116 = it }
                    )
                    CompareRow(
                        canal = "CH136",
                        freqText = "870 MHz",
                        calc = salidaCalc?.get("CH136"),
                        medidoText = outCh136,
                        isRequiredError = showRequiredErrors && parseDbmv(outCh136) == null,
                        onMedidoChange = { dirty = true; outCh136 = it }
                    )
                }

                CollapsibleSection(
                    titleBold = "",
                    titleLight = "FWD IN PAD/ EQ /AGC PAD a colocar",
                    expanded = recoExpanded,
                    onToggle = { recoExpanded = !recoExpanded }
                ) {
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
}

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
    onChange: (String) -> Unit
) {
    if (!compact) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = modifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = isError,
            textStyle = MaterialTheme.typography.bodyLarge
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
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
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
private fun MedidoRow(
    canal: String,
    freqText: String,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit
) {
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
            value = value,
            modifier = Modifier.width(92.dp),
            compact = true,
            isError = isError,
            onChange = onChange
        )
        Text("dBmV", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun MedidoRowWithFreqSelector(
    canal: String,
    freqMHz: Int,
    optionsMHz: List<Int>,
    onFreqChange: (Int) -> Unit,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
            value = value,
            modifier = Modifier.width(92.dp),
            compact = true,
            isError = isError,
            onChange = onChange
        )
        Text("dBmV", modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
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

private data class EntradaRow(val canal: String, val freqMHz: Int)

@Composable
private fun EntradaTableHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("CANAL", modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("FREQ\n(MHz)", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("Medido\n(dBmV)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.width(10.dp))
        Text("Plano\n(dBmV)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EntradaTableRow(
    canal: String,
    freqText: String,
    medidoText: String,
    planoText: String,
    medidoEditable: Boolean,
    planoEditable: Boolean,
    medidoSelected: Boolean,
    planoSelected: Boolean,
    onMedidoClick: () -> Unit,
    onPlanoClick: () -> Unit,
    onMedidoChange: (String) -> Unit,
    onPlanoChange: (String) -> Unit,
    showRequiredErrors: Boolean,
    medidoError: Boolean,
    planoError: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(canal, modifier = Modifier.width(72.dp), fontWeight = FontWeight.SemiBold)
        Text(freqText, modifier = Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), textAlign = TextAlign.Center)

        SelectableDbmvCell(
            text = medidoText,
            selected = medidoSelected,
            editable = medidoEditable,
            isError = showRequiredErrors && medidoError,
            onClick = onMedidoClick,
            onChange = onMedidoChange,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(10.dp))

        SelectableDbmvCell(
            text = planoText,
            selected = planoSelected,
            editable = planoEditable,
            isError = showRequiredErrors && planoError,
            onClick = onPlanoClick,
            onChange = onPlanoChange,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SelectableDbmvCell(
    text: String,
    selected: Boolean,
    editable: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        selected -> Color(0xFFFFC107) // yellow selection border
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }
    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.small
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (editable) {
                BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = if (text.isBlank()) "—" else text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (text.isBlank()) 0.55f else 1f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}



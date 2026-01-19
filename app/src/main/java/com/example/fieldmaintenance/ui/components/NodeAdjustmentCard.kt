@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.fieldmaintenance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldmaintenance.data.model.Frequency
import com.example.fieldmaintenance.data.model.NodeAdjustment
import com.example.fieldmaintenance.util.PlanRow
import java.util.Locale

@Composable
fun NodeAdjustmentCard(
    assetId: String,
    reportId: String,
    nodeName: String,
    frequency: Frequency?,
    technology: String?,
    planRow: PlanRow?,
    adjustment: NodeAdjustment?,
    showRequiredErrors: Boolean,
    collapseSignal: Int,
    onPersist: (NodeAdjustment) -> Unit
) {
    var expanded by remember(assetId) { mutableStateOf(true) }
    LaunchedEffect(collapseSignal) {
        expanded = false
    }

    // Determine if we have plan data
    val hasPlanData = planRow != null
    // Use technology from asset, fallback to plan, default to Legacy
    val tech = technology?.trim() ?: planRow?.technology?.trim() ?: "Legacy"
    val normalizedTech = tech.lowercase(Locale.getDefault())
    val isLegacy = normalizedTech == "legacy"
    val isRphy = normalizedTech == "rphy"
    val isVccap = normalizedTech == "vccap"

    fun normalize(v: String): String = v.trim().lowercase(Locale.getDefault())
    fun parsePo(v: String): Double? =
        Regex("[-+]?[0-9]*\\.?[0-9]+").find(v)?.value?.toDoubleOrNull()
    
    // Validate PO ranges for RPHY/VCCAP based on SFP distance
    fun isPoInRange(poValue: Double?, sfpDistance: Int?): Boolean {
        if (poValue == null || sfpDistance == null) return false
        return when (sfpDistance) {
            20 -> poValue >= -14.0 && poValue <= -1.0
            40 -> poValue >= -16.0 && poValue <= -7.0
            80 -> poValue >= -21.0 && poValue <= -7.0
            else -> false
        }
    }
    
    fun getPoRangeText(sfpDistance: Int?): String {
        return when (sfpDistance) {
            20 -> "-1 a -14 dBm"
            40 -> "-7 a -16 dBm"
            80 -> "-7 a -21 dBm"
            else -> "Selecciona SFP"
        }
    }

    fun isComplete(adj: NodeAdjustment): Boolean {
        return when {
            isRphy -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed
            }
            isVccap -> {
                adj.sfpDistance != null && adj.poDirectaConfirmed && adj.poRetornoConfirmed && 
                adj.spectrumConfirmed && adj.docsisConfirmed && frequency != null
            }
            !isLegacy -> {
                adj.nonLegacyConfirmed
            }
            else -> {
                val rxOk = adj.tx1310Confirmed || adj.tx1550Confirmed  // Pads RX (renamed from TX)
                val poOk = adj.poConfirmed
                val txOk = !adj.rxPadSelection.isNullOrBlank()  // Pads TX (renamed from RX)
                val measOk = adj.measurementConfirmed
                val specOk = adj.spectrumConfirmed
                rxOk && poOk && txOk && measOk && specOk && frequency != null
            }
        }
    }

    val defaultAdj = NodeAdjustment(assetId = assetId, reportId = reportId)
    var local by remember(assetId) { mutableStateOf(adjustment ?: defaultAdj) }
    LaunchedEffect(adjustment) {
        local = adjustment ?: defaultAdj
    }

    val completeNow = isComplete(local)

    fun persist(newAdj: NodeAdjustment) {
        // Persist snapshot of Plan values + confirmations (only if plan data is available).
        val withTs = newAdj.copy(
            planNode = planRow?.nodeCmts?.takeIf { it.isNotBlank() },
            planContractor = planRow?.contractor?.takeIf { it.isNotBlank() },
            planTechnology = planRow?.technology?.takeIf { it.isNotBlank() },
            planPoDirecta = planRow?.poDirecta?.takeIf { it.isNotBlank() },
            planPoRetorno = planRow?.poRetorno?.takeIf { it.isNotBlank() },
            planDistanciaSfp = planRow?.distanciaSfp?.takeIf { it.isNotBlank() },
            updatedAt = System.currentTimeMillis()
        )
        local = withTs
        onPersist(withTs)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (showRequiredErrors && !completeNow) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error
        ) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ajuste de Nodo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (completeNow) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (completeNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Colapsar" else "Expandir"
                    )
                }
            }

            // Plan values (only visible if plan data is available)
            if (hasPlanData) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Nodo: ${planRow.nodeCmts}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    if (planRow.contractor.isNotBlank()) {
                        Text("Contratista: ${planRow.contractor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                    if (planRow.technology.isNotBlank()) {
                        Text("Tecnología: ${planRow.technology}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                    if (planRow.poDirecta.isNotBlank()) {
                        Text("PO Directa: ${planRow.poDirecta}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                    if (planRow.poRetorno.isNotBlank()) {
                        Text("PO Retorno: ${planRow.poRetorno}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                    if (planRow.distanciaSfp.isNotBlank()) {
                        Text("Distancia SFP: ${planRow.distanciaSfp}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                }
            } else {
                // Show warning when plan data is not available
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.padding(end = 4.dp))
                        Text(
                            "Comunicación con Plan fallida o Nodo no encontrado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                    if (nodeName.isNotBlank()) {
                        Text(
                            "Nodo: $nodeName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        isRphy -> {
                            // RPHY: SFP + PO Directa + PO Retorno
                            var expandedSfp by remember { mutableStateOf(false) }
                            val sfpOptions = listOf(20, 40, 80)
                            val currentSfp = local.sfpDistance ?: (planRow?.let {
                                parsePo(it.distanciaSfp)?.toInt()?.takeIf { dist -> dist in sfpOptions }
                            })
                            
                            Text("SFP", fontWeight = FontWeight.Medium)
                            ExposedDropdownMenuBox(
                                expanded = expandedSfp,
                                onExpandedChange = { expandedSfp = !expandedSfp },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = currentSfp?.let { "$it km" } ?: "Seleccionar",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Distancia SFP") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSfp) }
                                )
                                ExposedDropdownMenu(expanded = expandedSfp, onDismissRequest = { expandedSfp = false }) {
                                    sfpOptions.forEach { km ->
                                        DropdownMenuItem(
                                            text = { Text("$km km") },
                                            onClick = {
                                                persist(local.copy(sfpDistance = km))
                                                expandedSfp = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(4.dp))
                            Text("PO Directa (llegando a Nodo)", fontWeight = FontWeight.Medium)
                            val poDirectaVal = planRow?.let { parsePo(it.poDirecta) }
                            val poDirectaInRange = if (currentSfp != null && poDirectaVal != null) {
                                isPoInRange(poDirectaVal, currentSfp)
                            } else null
                            planRow?.let {
                                Text(
                                    "PO Directa (Plan): ${it.poDirecta.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                            Text(
                                when {
                                    currentSfp == null -> "Selecciona SFP para ver rango esperado"
                                    poDirectaInRange == null -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    poDirectaInRange -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    else -> "Fuera de rango (${getPoRangeText(currentSfp)}): confirme si fue reparado."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (poDirectaInRange == false) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            ConfirmRow(
                                text = "Confirmo PO Directa",
                                confirmed = local.poDirectaConfirmed,
                                onToggle = { persist(local.copy(poDirectaConfirmed = !local.poDirectaConfirmed)) }
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            Text("PO Retorno (llegando a HUB)", fontWeight = FontWeight.Medium)
                            val poRetornoVal = planRow?.let { parsePo(it.poRetorno) }
                            val poRetornoInRange = if (currentSfp != null && poRetornoVal != null) {
                                isPoInRange(poRetornoVal, currentSfp)
                            } else null
                            planRow?.let {
                                Text(
                                    "PO Retorno (Plan): ${it.poRetorno.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                            Text(
                                when {
                                    currentSfp == null -> "Selecciona SFP para ver rango esperado"
                                    poRetornoInRange == null -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    poRetornoInRange -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    else -> "Fuera de rango (${getPoRangeText(currentSfp)}): confirme si fue reparado."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (poRetornoInRange == false) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            ConfirmRow(
                                text = "Confirmo PO Retorno",
                                confirmed = local.poRetornoConfirmed,
                                onToggle = { persist(local.copy(poRetornoConfirmed = !local.poRetornoConfirmed)) }
                            )
                        }
                        isVccap -> {
                            // VCCAP: SFP + PO Directa + PO Retorno + Espectro + DOCSIS
                            var expandedSfp by remember { mutableStateOf(false) }
                            val sfpOptions = listOf(20, 40, 80)
                            val currentSfp = local.sfpDistance ?: (planRow?.let {
                                parsePo(it.distanciaSfp)?.toInt()?.takeIf { dist -> dist in sfpOptions }
                            })
                            
                            Text("SFP", fontWeight = FontWeight.Medium)
                            ExposedDropdownMenuBox(
                                expanded = expandedSfp,
                                onExpandedChange = { expandedSfp = !expandedSfp },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = currentSfp?.let { "$it km" } ?: "Seleccionar",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Distancia SFP") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSfp) }
                                )
                                ExposedDropdownMenu(expanded = expandedSfp, onDismissRequest = { expandedSfp = false }) {
                                    sfpOptions.forEach { km ->
                                        DropdownMenuItem(
                                            text = { Text("$km km") },
                                            onClick = {
                                                persist(local.copy(sfpDistance = km))
                                                expandedSfp = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(4.dp))
                            Text("PO Directa (llegando a Nodo)", fontWeight = FontWeight.Medium)
                            val poDirectaVal = planRow?.let { parsePo(it.poDirecta) }
                            val poDirectaInRange = if (currentSfp != null && poDirectaVal != null) {
                                isPoInRange(poDirectaVal, currentSfp)
                            } else null
                            planRow?.let {
                                Text(
                                    "PO Directa (Plan): ${it.poDirecta.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                            Text(
                                when {
                                    currentSfp == null -> "Selecciona SFP para ver rango esperado"
                                    poDirectaInRange == null -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    poDirectaInRange -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    else -> "Fuera de rango (${getPoRangeText(currentSfp)}): confirme si fue reparado."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (poDirectaInRange == false) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            ConfirmRow(
                                text = "Confirmo PO Directa",
                                confirmed = local.poDirectaConfirmed,
                                onToggle = { persist(local.copy(poDirectaConfirmed = !local.poDirectaConfirmed)) }
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            Text("PO Retorno (llegando a HUB)", fontWeight = FontWeight.Medium)
                            val poRetornoVal = planRow?.let { parsePo(it.poRetorno) }
                            val poRetornoInRange = if (currentSfp != null && poRetornoVal != null) {
                                isPoInRange(poRetornoVal, currentSfp)
                            } else null
                            planRow?.let {
                                Text(
                                    "PO Retorno (Plan): ${it.poRetorno.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                            Text(
                                when {
                                    currentSfp == null -> "Selecciona SFP para ver rango esperado"
                                    poRetornoInRange == null -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    poRetornoInRange -> "Rango esperado: ${getPoRangeText(currentSfp)}"
                                    else -> "Fuera de rango (${getPoRangeText(currentSfp)}): confirme si fue reparado."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (poRetornoInRange == false) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            ConfirmRow(
                                text = "Confirmo PO Retorno",
                                confirmed = local.poRetornoConfirmed,
                                onToggle = { persist(local.copy(poRetornoConfirmed = !local.poRetornoConfirmed)) }
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            Text("Espectro", fontWeight = FontWeight.Medium)
                            val spectrumText = when (frequency) {
                                Frequency.MHz_42 ->
                                    "En nodos 42Mhz, en Pathtrack se espera portadoras 20dBmv +/-1dB en 10Mhz y 42Mhz. Piso de ruido -35dB +/-2dB plano entre 0 y 42Mhz."
                                Frequency.MHz_85 ->
                                    "En nodos 85Mhz, en Pathtrack se espera portadoras 15dBmv +/-1dB en 10Mhz, 42Mhz y 84,5Mhz. Piso de ruido -35dB +/-2dB plano entre 0 y 85Mhz."
                                else -> "Selecciona la Frecuencia para ver instrucciones de Espectro."
                            }
                            ConfirmRow(
                                text = spectrumText,
                                confirmed = local.spectrumConfirmed,
                                onToggle = { persist(local.copy(spectrumConfirmed = !local.spectrumConfirmed)) },
                                enabled = frequency != null
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            Text("DOCSIS", fontWeight = FontWeight.Medium)
                            val docsisText = when (frequency) {
                                Frequency.MHz_42 -> "DOCSIS en el equipo debe estar entre (29 a 34) dBmV ± 1dB"
                                Frequency.MHz_85 -> "DOCSIS en el equipo debe estar entre (30 a 35) dBmV ± 1dB"
                                else -> "Selecciona la Frecuencia para ver instrucciones de DOCSIS."
                            }
                            ConfirmRow(
                                text = docsisText,
                                confirmed = local.docsisConfirmed,
                                onToggle = { persist(local.copy(docsisConfirmed = !local.docsisConfirmed)) },
                                enabled = frequency != null
                            )
                        }
                        !isLegacy -> {
                            // Other non-Legacy technologies
                            Text(
                                "Asegure con HUB y operador que los parámetros estén correctos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                            ConfirmRow(
                                text = "Confirmo revisión de parámetros (no Legacy)",
                                confirmed = local.nonLegacyConfirmed,
                                onToggle = { persist(local.copy(nonLegacyConfirmed = !local.nonLegacyConfirmed)) }
                            )
                        }
                        else -> {
                            // Legacy
                        Text("DIRECTA", fontWeight = FontWeight.SemiBold)

                        Text("Pads RX", fontWeight = FontWeight.Medium)
                        ConfirmRow(
                            text = "Con Tx de 1310nm instalados en HUB colocar Pad de 9 en receptora de nodo.",
                            confirmed = local.tx1310Confirmed,
                            enabled = !local.tx1550Confirmed || local.tx1310Confirmed,
                            onToggle = {
                                val next = !local.tx1310Confirmed
                                persist(local.copy(tx1310Confirmed = next, tx1550Confirmed = if (next) false else local.tx1550Confirmed))
                            }
                        )
                        ConfirmRow(
                            text = "Con Tx de 1550nm instalados en HUB colocar Pad de 10 en receptora de nodo.",
                            confirmed = local.tx1550Confirmed,
                            enabled = !local.tx1310Confirmed || local.tx1550Confirmed,
                            onToggle = {
                                val next = !local.tx1550Confirmed
                                persist(local.copy(tx1550Confirmed = next, tx1310Confirmed = if (next) false else local.tx1310Confirmed))
                            }
                        )

                        Spacer(Modifier.height(2.dp))
                        Text("PO", fontWeight = FontWeight.Medium)

                        if (hasPlanData) {
                            val poVal = parsePo(planRow.poDirecta)
                            val outOfRange = poVal != null && (poVal < 0.8 || poVal > 1.2)
                            Text(
                                "PO (Plan): ${planRow.poDirecta.ifBlank { "N/A" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            Text(
                                if (outOfRange) "Fuera de rango (0.8–1.2): confirme si fue reparado."
                                else "Rango esperado: 0.8–1.2",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        } else {
                            Text(
                                "Rango esperado: 0.8–1.2",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                        ConfirmRow(
                            text = "Confirmo PO",
                            confirmed = local.poConfirmed,
                            onToggle = { persist(local.copy(poConfirmed = !local.poConfirmed)) }
                        )

                        Spacer(Modifier.height(6.dp))
                        Text("RETORNO", fontWeight = FontWeight.SemiBold)
                        Text("Pads TX", fontWeight = FontWeight.Medium)

                        when (frequency) {
                            Frequency.MHz_85 -> {
                                RxOptionRow(
                                    selected = local.rxPadSelection,
                                    optionA = "TP_BLACK",
                                    labelA = "Con FRECUENCIA de 85Mhz con test point y cartucho negro colocar pad de 8 en transmisora de nodo.",
                                    optionB = "TP_NO_BLACK",
                                    labelB = "Con FRECUENCIA de 85Mhz con test point y SIN cartucho negro colocar pad de 9 en transmisora de nodo.",
                                    onSelect = { persist(local.copy(rxPadSelection = it)) }
                                )
                            }
                            Frequency.MHz_42 -> {
                                RxOptionRow(
                                    selected = local.rxPadSelection,
                                    optionA = "TP_BLACK",
                                    labelA = "Con FRECUENCIA de 42Mhz con test point y cartucho negro colocar pad de 10 en transmisora de nodo.",
                                    optionB = "TP_NO_BLACK",
                                    labelB = "Con FRECUENCIA de 42Mhz con test point y SIN cartucho negro colocar pad de 10 en transmisora de nodo.",
                                    onSelect = { persist(local.copy(rxPadSelection = it)) }
                                )
                            }
                            else -> {
                                Text(
                                    "Selecciona la Frecuencia para ver instrucciones de TX.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Medición", fontWeight = FontWeight.Medium)
                        ConfirmRow(
                            text = "Confirmar en el test point de la RX que no hay errores en los canales digitales y que estén 6dB por debajo de los pilotos analógicos.",
                            confirmed = local.measurementConfirmed,
                            onToggle = { persist(local.copy(measurementConfirmed = !local.measurementConfirmed)) }
                        )

                        Spacer(Modifier.height(4.dp))
                        Text("Espectro", fontWeight = FontWeight.Medium)
                        val spectrumText = when (frequency) {
                            Frequency.MHz_42 ->
                                "En nodos 42Mhz, en Pathtrack se espera portadoras 20dBmv +/-1dB en 10Mhz y 42Mhz. Piso de ruido -35dB +/-2dB plano entre 0 y 42Mhz."
                            Frequency.MHz_85 ->
                                "En nodos 85Mhz, en Pathtrack se espera portadoras 15dBmv +/-1dB en 10Mhz, 42Mhz y 84,5Mhz. Piso de ruido -35dB +/-2dB plano entre 0 y 85Mhz."
                            else -> "Selecciona la Frecuencia para ver instrucciones de Espectro."
                        }
                        ConfirmRow(
                            text = spectrumText,
                            confirmed = local.spectrumConfirmed,
                            onToggle = { persist(local.copy(spectrumConfirmed = !local.spectrumConfirmed)) },
                            enabled = frequency != null
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(
    text: String,
    confirmed: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = onToggle,
            enabled = enabled
        ) {
            if (confirmed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Confirmado",
                    tint = Color(0xFF2E7D32)
                )
                Spacer(Modifier.width(6.dp))
                Text("Confirmado", color = Color(0xFF2E7D32))
            } else {
                Text("Confirmar")
            }
        }
    }
}

@Composable
private fun RxOptionRow(
    selected: String?,
    optionA: String,
    labelA: String,
    optionB: String,
    labelB: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected == optionA,
                onClick = { onSelect(optionA) }
            )
            Spacer(Modifier.width(6.dp))
            Text(labelA, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected == optionB,
                onClick = { onSelect(optionB) }
            )
            Spacer(Modifier.width(6.dp))
            Text(labelB, style = MaterialTheme.typography.bodySmall)
        }
    }
}

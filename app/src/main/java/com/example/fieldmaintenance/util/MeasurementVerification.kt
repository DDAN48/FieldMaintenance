package com.example.fieldmaintenance.util

import android.content.Context
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.AssetType
import com.example.fieldmaintenance.data.model.PhotoType
import com.example.fieldmaintenance.data.repository.MaintenanceRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

fun loadDiscardedLabels(file: File): Set<String> {
    if (!file.exists()) return emptySet()
    return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

fun saveDiscardedLabels(file: File, labels: Set<String>) {
    file.writeText(labels.joinToString(separator = "\n"))
}

data class RequiredCounts(
    val expectedDocsis: Int,
    val expectedChannel: Int,
    val maxDocsisTable: Int,
    val maxChannelTable: Int
)

fun requiredCounts(assetType: AssetType, isModule: Boolean): RequiredCounts {
    return if (isModule) {
        RequiredCounts(expectedDocsis = 4, expectedChannel = 4, maxDocsisTable = 4, maxChannelTable = 4)
    } else {
        when (assetType) {
            AssetType.NODE -> {
                RequiredCounts(expectedDocsis = 0, expectedChannel = 1, maxDocsisTable = 0, maxChannelTable = 1)
            }
            AssetType.AMPLIFIER -> {
                RequiredCounts(expectedDocsis = 3, expectedChannel = 4, maxDocsisTable = 3, maxChannelTable = 4)
            }
        }
    }
}

private fun switchOptionsFor(mode: com.example.fieldmaintenance.data.model.AmplifierMode?): List<String> {
    return when (mode) {
        com.example.fieldmaintenance.data.model.AmplifierMode.HGD -> listOf("IN", "MAIN", "AUX", "AUXDC")
        com.example.fieldmaintenance.data.model.AmplifierMode.HGDT -> listOf("IN", "MAIN", "AUX")
        else -> listOf("IN", "MAIN", "AUX")
    }
}

private fun inferSwitchSelection(label: String, options: List<String>): String? {
    val fileLabel = label.substringAfterLast('/')
    val normalized = fileLabel.uppercase(Locale.getDefault())
    val cleaned = normalized.replace(Regex("[^A-Z0-9]"), "_")
    val tokens = cleaned.split("_").filter { it.isNotBlank() }.toSet()
    val containsMain = cleaned.contains("MAIN") || cleaned.contains("PRINCIPAL")
    val containsAux = cleaned.contains("AUX") || cleaned.contains("AUXILIAR")
    val auxdcMatch = cleaned.contains("AUXDC") ||
        cleaned.contains("AUX_DC") ||
        cleaned.contains("AUXILIARDC") ||
        cleaned.contains("AUXILIAR_DC")
    return when {
        containsMain || tokens.contains("MAIN") || tokens.contains("PRINCIPAL") -> "MAIN"
        tokens.contains("IN") || tokens.contains("ENTRADA") -> "IN"
        auxdcMatch && options.contains("AUXDC") -> "AUXDC"
        containsAux || tokens.contains("AUX") || tokens.contains("AUXILIAR") -> "AUX"
        else -> null
    }
}

private fun adjustAmplifierTarget(target: Double?, switchSelection: String?): Double? {
    if (target == null) return null
    return when (switchSelection?.uppercase(Locale.getDefault())) {
        "AUX" -> target - 3.0
        "AUXDC" -> target - 10.0
        else -> target
    }
}

fun meetsRequired(summary: MeasurementVerificationSummary?, required: RequiredCounts): Boolean {
    if (summary == null) return false
    return summary.result.docsisExpert >= required.expectedDocsis &&
        summary.result.channelExpert >= required.expectedChannel
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
                val icfrValue = row.optJSONObject(3)?.optString("value")
                val channel = parseInt(channelValue)
                val frequency = parseNumber(frequencyValue)
                val level = parseNumber(levelValue)
                val icfr = parseNumber(icfrValue)
                if (channel != null || frequency != null || level != null || icfr != null) {
                    rows.add(
                        ChannelRow(
                            channel = channel,
                            frequencyMHz = frequency,
                            levelDbmv = level,
                            merDb = null,
                            berPre = null,
                            berPost = null,
                            icfrDb = icfr
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

private fun collectOfdmSeries(results: JSONObject?): OfdmSeries? {
    val view = results?.optJSONObject("docsisDownstreamLevelsView") ?: return null
    val failed = view.optJSONObject("Failed OFDM")
    val passed = view.optJSONObject("Passed OFDM")
    val target = failed ?: passed ?: return null
    val data = target.optJSONArray("data") ?: return null
    val points = mutableListOf<Pair<Double, Double>>()
    for (i in 0 until data.length()) {
        val pair = data.optJSONArray(i) ?: continue
        if (pair.length() < 2) continue
        val freq = pair.optDouble(0, Double.NaN)
        val level = pair.optDouble(1, Double.NaN)
        if (!freq.isNaN() && !level.isNaN()) {
            points.add(freq to level)
        }
    }
    if (points.isEmpty()) return null
    return OfdmSeries(points = points, isValid = failed == null)
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

private fun docsisRange(ruleTable: JSONObject?): Pair<Double?, Double?> {
    if (ruleTable == null) return null to null
    val directMin = ruleTable.optDouble("min", Double.NaN).takeIf { !it.isNaN() }
    val directMax = ruleTable.optDouble("max", Double.NaN).takeIf { !it.isNaN() }
    if (directMin != null || directMax != null) {
        return directMin to directMax
    }
    val keys = ruleTable.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val child = ruleTable.optJSONObject(key) ?: continue
        val childMin = child.optDouble("min", Double.NaN).takeIf { !it.isNaN() }
        val childMax = child.optDouble("max", Double.NaN).takeIf { !it.isNaN() }
        if (childMin != null || childMax != null) {
            return childMin to childMax
        }
    }
    return null to null
}

private data class LegacyTxTargets(
    val txType: String,
    val pilotTarget: Double,
    val pilotTolerance: Double,
    val digitalOffset: Double,
    val digitalTolerance: Double
)

private fun legacyNodeTxTargets(
    rules: JSONObject?,
    equipmentKey: String,
    nodeTxType: String?
): LegacyTxTargets? {
    if (rules == null) return null
    val txType = nodeTxType?.trim()?.takeIf { it == "1310" || it == "1550" } ?: return null
    val ruleTable = rules.optJSONObject("channelexpert")
        ?.optJSONObject("node")
        ?.optJSONObject(equipmentKey)
        ?.optJSONObject("txTargets")
        ?: return null
    val pilotRule = ruleTable.optJSONObject(txType) ?: return null
    val pilotTarget = pilotRule.optDouble("pilotTarget", Double.NaN).takeIf { !it.isNaN() } ?: return null
    val pilotTolerance = pilotRule.optDouble("tolerance", Double.NaN).takeIf { !it.isNaN() } ?: return null
    val digitalOffset = ruleTable.optDouble("digitalOffset", Double.NaN).takeIf { !it.isNaN() } ?: return null
    val digitalTolerance = ruleTable.optDouble("digitalTolerance", Double.NaN).takeIf { !it.isNaN() } ?: return null
    return LegacyTxTargets(
        txType = txType,
        pilotTarget = pilotTarget,
        pilotTolerance = pilotTolerance,
        digitalOffset = digitalOffset,
        digitalTolerance = digitalTolerance
    )
}

private fun validateMeasurementValues(
    rules: JSONObject?,
    test: JSONObject,
    type: String,
    equipmentKey: String,
    assetType: AssetType,
    amplifierTargets: Map<Int, Double>?,
    nodeTxType: String?,
    isLegacyNode: Boolean,
    skipChannelValidation: Boolean,
    toleranceOverride: Double?,
    switchSelection: String?
): List<String> {
    if (rules == null) return listOf("No se pudo cargar la tabla de validaci?n.")
    if (type == "channelexpert" && skipChannelValidation) return emptyList()
    val issues = mutableListOf<String>()
    val assetKey = when (assetType) {
        AssetType.NODE -> "node"
        AssetType.AMPLIFIER -> "amplifier"
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
        val (min, max) = docsisRange(ruleTable)
        rows.forEach { row ->
            val level = row.levelDbmv ?: return@forEach
            val adjusted = level + testPointOffset
            if (!isWithinRange(adjusted, min, max)) {
                issues.add("Nivel fuera de rango en DOCSIS.")
            }
        }
    }

    if (type == "channelexpert") {
        val ruleTable = rules.optJSONObject("channelexpert")
            ?.optJSONObject(assetKey)
            ?.optJSONObject(equipmentKey)
        if (ruleTable == null) {
            return listOf("No hay reglas de ChannelExpert para $assetKey/$equipmentKey.")
        }

        // Nodo Legacy (RX): validar niveles según TX (1310/1550) usando txTargets.
        // - CH110 se toma como PILOT (pilotTarget ± tolerance)
        // - CH50/70/116/136 se validan como "digital" (pilotTarget + digitalOffset ± digitalTolerance)
        val legacyTx = if (assetType == AssetType.NODE && isLegacyNode) legacyNodeTxTargets(rules, equipmentKey, nodeTxType) else null
        val legacyPilotChannels = setOf(50, 70, 116, 136)
        if (legacyTx != null) {
            val pilotRow = rows.firstOrNull { it.channel == 110 }
            val pilotLevel = pilotRow?.levelDbmv
            if (pilotLevel == null) {
                issues.add("No se encontr? nivel PILOT (CH110) para validar RX Legacy.")
            } else {
                val adjusted = pilotLevel + testPointOffset
                if (adjusted < legacyTx.pilotTarget - legacyTx.pilotTolerance || adjusted > legacyTx.pilotTarget + legacyTx.pilotTolerance) {
                    issues.add("PILOT fuera de rango (CH110) para TX ${legacyTx.txType}.")
                }
            }
            legacyPilotChannels.forEach { ch ->
                val row = rows.firstOrNull { it.channel == ch }
                val level = row?.levelDbmv
                if (level == null) {
                    issues.add("No se encontr? nivel para canal $ch (RX Legacy).")
                } else {
                    val adjusted = level + testPointOffset
                    val target = legacyTx.pilotTarget + legacyTx.digitalOffset
                    val tol = legacyTx.digitalTolerance
                    if (adjusted < target - tol || adjusted > target + tol) {
                        issues.add("Nivel fuera de rango en canal $ch (RX Legacy).")
                    }
                }
            }
        }

        val merRule = ruleTable.optJSONObject("mer") ?: rules.optJSONObject("channelexpert")
            ?.optJSONObject("common")
            ?.optJSONObject("mer")
        val merMin = merRule?.optDouble("min", Double.NaN)?.takeIf { !it.isNaN() }
        merPairs.forEach { (freq, mer) ->
            if (merMin != null && mer < merMin) {
                issues.add("MER fuera de rango en $freq MHz.")
            }
        }
        val channels = ruleTable.optJSONObject("channels") ?: return issues
        val keys = channels.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val channel = key.toIntOrNull() ?: continue
            // Node RX: CH110 no se valida para niveles (dBmV).
            if (assetType == AssetType.NODE && channel == 110) continue
            // Nodo Legacy (RX): si tenemos txTargets, los niveles de estos canales se validan por txTargets.
            if (assetType == AssetType.NODE && isLegacyNode && legacyTx != null && legacyPilotChannels.contains(channel)) continue
            val rule = channels.optJSONObject(key) ?: continue
            fun resolveTolerance(ruleTolerance: Double?, maxTolerance: Double?): Double? {
                return when {
                    maxTolerance == null -> ruleTolerance
                    ruleTolerance == null -> maxTolerance
                    else -> minOf(ruleTolerance, maxTolerance)
                }
            }
            if (rule.has("source")) {
                val target = adjustAmplifierTarget(amplifierTargets?.get(channel), switchSelection)
                if (target == null) {
                    if (assetType == AssetType.AMPLIFIER && amplifierTargets != null) {
                        issues.add("Falta tabla interna para canal $channel.")
                    }
                    continue
                }
                val row = rows.firstOrNull { it.channel == channel }
                val level = row?.levelDbmv
                if (level == null) {
                    issues.add("No se encontr? nivel para canal $channel.")
                } else {
                    val tolerance = resolveTolerance(rule.optDouble("tolerance", 1.5), toleranceOverride)
                    val adjusted = level + testPointOffset
                    if (tolerance != null && (adjusted < target - tolerance || adjusted > target + tolerance)) {
                        issues.add("Nivel fuera de rango en canal $channel.")
                    }
                }
            } else {
                val row = rows.firstOrNull { it.channel == channel }
                val level = row?.levelDbmv
                if (level == null) {
                    issues.add("No se encontr? nivel para canal $channel.")
                } else {
                    val target = rule.optDouble("target", Double.NaN)
                    val tolerance = resolveTolerance(
                        rule.optDouble("tolerance", Double.NaN).takeIf { !it.isNaN() },
                        toleranceOverride
                    )
                    if (!target.isNaN() && tolerance != null) {
                        val adjusted = level + testPointOffset
                        if (adjusted < target - tolerance || adjusted > target + tolerance) {
                            issues.add("Nivel fuera de rango en canal $channel.")
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

private data class GeoParseResult(
    val point: GeoPoint?,
    val hasGeoField: Boolean,
    val issue: String?,
    val rawLatitude: Double?,
    val rawLongitude: Double?
)

private fun parseGeoLocation(results: JSONObject?): GeoParseResult {
    val geo = results?.optJSONObject("geoLocation")
        ?: return GeoParseResult(point = null, hasGeoField = false, issue = "Georreferencia ausente", rawLatitude = null, rawLongitude = null)
    val lat = geo.optDouble("latitude", Double.NaN)
    val lon = geo.optDouble("longitude", Double.NaN)
    if (lat.isNaN() || lon.isNaN()) {
        return GeoParseResult(point = null, hasGeoField = true, issue = "Georreferencia inv?lida (valores vac?os)", rawLatitude = null, rawLongitude = null)
    }
    if (lat == 0.0 && lon == 0.0) {
        return GeoParseResult(point = null, hasGeoField = true, issue = "Georreferencia inv?lida (0,0)", rawLatitude = lat, rawLongitude = lon)
    }
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
        return GeoParseResult(point = null, hasGeoField = true, issue = "Georreferencia inv?lida (fuera de rango)", rawLatitude = lat, rawLongitude = lon)
    }
    return GeoParseResult(
        point = GeoPoint(latitude = lat, longitude = lon),
        hasGeoField = true,
        issue = null,
        rawLatitude = lat,
        rawLongitude = lon
    )
}

private fun bucketKey(point: GeoPoint, cellMeters: Double): Pair<Int, Int> {
    val latStep = cellMeters / 111_320.0
    val latBucket = kotlin.math.floor(point.latitude / latStep).toInt()
    val lonStep = cellMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(point.latitude)).coerceAtLeast(0.0001))
    val lonBucket = kotlin.math.floor(point.longitude / lonStep).toInt()
    return latBucket to lonBucket
}

data class MeasurementVerificationResult(
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

data class ObservationGroup(
    val type: String,
    val file: String,
    val count: Int
)

data class GeoIssueDetail(
    val type: String,
    val file: String,
    val detail: String
)

data class MeasurementEntry(
    val label: String,
    val type: String,
    val fromZip: Boolean,
    val isDiscarded: Boolean,
    val geoLocation: GeoPoint?,
    val docsisMeta: Map<Double, ChannelMeta>,
    val docsisLevels: Map<Double, Double>,
    val docsisIcfr: Map<Double, Double>,
    val docsisLevelOk: Map<Double, Boolean>,
    val pilotMeta: Map<Int, ChannelMeta>,
    val pilotLevels: Map<Int, Double>,
    val pilotLevelOk: Map<Int, Boolean>,
    val digitalRows: List<DigitalChannelRow>,
    val ofdmSeries: OfdmSeries?
)

data class OfdmSeries(
    val points: List<Pair<Double, Double>>,
    val isValid: Boolean
)

data class ChannelMeta(
    val channel: Int?,
    val frequencyMHz: Double?
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class DigitalChannelRow(
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

data class ValidationIssueDetail(
    val type: String,
    val file: String,
    val detail: String,
    val isRuleViolation: Boolean = true
)

data class MeasurementVerificationSummary(
    val expectedDocsis: Int,
    val expectedChannel: Int,
    val result: MeasurementVerificationResult,
    val warnings: List<String>,
    val geoLocation: GeoPoint?,
    val observationTotal: Int,
    val observationGroups: List<ObservationGroup>,
    val geoIssueDetails: List<GeoIssueDetail>,
    val validationIssueDetails: List<ValidationIssueDetail> = emptyList()
)

suspend fun verifyMeasurementFiles(
    context: Context,
    files: List<File>,
    asset: Asset,
    repository: MaintenanceRepository,
    discardedLabels: Set<String>,
    expectedDocsisOverride: Int? = null,
    expectedChannelOverride: Int? = null,
    extraGeoPoints: List<GeoPoint> = emptyList()
): MeasurementVerificationSummary {
    val assetType = asset.type
    val isLegacyNode = assetType == AssetType.NODE && asset.technology?.equals("Legacy", ignoreCase = true) == true
    val expectedDocsis = expectedDocsisOverride ?: when (assetType) {
        AssetType.NODE -> 0
        AssetType.AMPLIFIER -> 4
    }
    val expectedChannel = expectedChannelOverride ?: when (assetType) {
        AssetType.NODE -> 5
        AssetType.AMPLIFIER -> 4
    }

    val switchPrefs = context.getSharedPreferences("measurement_switch_positions", Context.MODE_PRIVATE)

    val seenIds = mutableSetOf<String>()
    var docsisCount = 0
    var channelCount = 0
    var channelSequenceIndex = 0
    var channelMainUsed = false
    var channelInUsed = false
    var channelAuxdcUsed = false
    var docsisMainUsed = false
    var docsisAuxdcUsed = false
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
    data class ValidationIssue(val type: String, val label: String, val message: String)
    val validationIssues = mutableListOf<ValidationIssue>()
    var geoMissingCount = 0
    var geoInvalidCount = 0
    val geoIssueDetails = mutableListOf<GeoIssueDetail>()
    val rules = loadMeasurementRules(context)
    val equipmentKey = equipmentKeyFor(asset)
    val amplifierAdjustment = if (assetType == AssetType.AMPLIFIER) {
        repository.getAmplifierAdjustmentOne(asset.id)
    } else {
        null
    }
    val amplifierTargets = if (assetType == AssetType.AMPLIFIER) {
        val calculated = amplifierAdjustment?.let { CiscoHfcAmpCalculator.nivelesSalidaCalculados(it) }
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
    val ampInputStatus = if (assetType == AssetType.AMPLIFIER) {
        val adj = amplifierAdjustment
        if (adj == null) {
            null
        } else {
            val lowPlanFreq = adj.inputPlanLowFreqMHz ?: adj.inputLowFreqMHz
            val highPlanFreq = adj.inputPlanHighFreqMHz ?: adj.inputHighFreqMHz
            val lowCalc = CiscoHfcAmpCalculator.entradaCalcValueForFreq(adj, lowPlanFreq)
            val highCalc = CiscoHfcAmpCalculator.entradaCalcValueForFreq(adj, highPlanFreq)
            val lowMed = adj.inputCh50Dbmv
            val lowPlan = adj.inputPlanCh50Dbmv
            val highMed = adj.inputCh116Dbmv
            val highPlan = adj.inputPlanHighDbmv
            val ch50Ok = lowMed != null && lowPlan != null && lowCalc != null &&
                lowMed >= 15.0 && kotlin.math.abs(lowCalc - lowPlan) < 4.0
            val highOk = highMed != null && highPlan != null && highCalc != null &&
                highMed >= 15.0 && kotlin.math.abs(highCalc - highPlan) < 4.0
            ch50Ok to highOk
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

    val dedupedFiles = buildList {
        val seenNames = mutableSetOf<String>()
        files.forEach { file ->
            val key = file.name.lowercase(Locale.getDefault())
            if (file.extension.equals("html", ignoreCase = true) || file.extension.equals("htm", ignoreCase = true)) {
                if (file.exists()) {
                    file.delete()
                }
                return@forEach
            }
            if (!isJsonLike(key) &&
                !key.endsWith(".zip") &&
                !key.endsWith(".gz") &&
                !key.endsWith(".txt")
            ) {
                if (file.exists()) {
                    file.delete()
                }
                return@forEach
            }
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

    fun geoKey(point: GeoPoint?): String {
        return point?.let { "${it.latitude},${it.longitude}" } ?: "unknown"
    }

    fun rawGeoKey(result: GeoParseResult): String? {
        val lat = result.rawLatitude
        val lon = result.rawLongitude
        return if (lat != null && lon != null) {
            "${lat},${lon}"
        } else {
            null
        }
    }

    fun hashId(type: String, testTime: String, testDurationMs: String, geoLocation: String): String {
        val input = listOf(type, testTime, testDurationMs, geoLocation).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun handleJsonBytes(bytes: ByteArray, sourceFile: File?, sourceLabel: String) {
        val isDiscarded = discardedLabels.contains(sourceLabel)
        if (isDiscarded) {
            if (sourceFile != null && sourceFile.exists()) {
                sourceFile.delete()
            }
            return
        }
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
            val geoResult = parseGeoLocation(results)
            val geoLocation = geoKey(geoResult.point)
            val normalizedType = type.lowercase(Locale.getDefault())
            val switchOptions = if (assetType == AssetType.AMPLIFIER) {
                switchOptionsFor(asset.amplifierMode)
            } else {
                emptyList()
            }
            val switchSelection = if (assetType == AssetType.AMPLIFIER) {
                val switchKey = "switch_${asset.id}_${sourceLabel}"
                val saved = switchPrefs.getString(switchKey, null)
                val inferred = if (saved == null) inferSwitchSelection(sourceLabel, switchOptions) else null
                if (saved == null && inferred != null) {
                    switchPrefs.edit().putString(switchKey, inferred).apply()
                }
                val initialSelection = when {
                    saved != null -> saved
                    inferred != null -> inferred
                    normalizedType == "channelexpert" && !isDiscarded -> {
                        val selection = when (channelSequenceIndex) {
                            0 -> "MAIN"
                            1 -> "IN"
                            else -> "AUX"
                        }
                        channelSequenceIndex += 1
                        selection
                    }
                    normalizedType == "docsisexpert" && !isDiscarded -> {
                        if (!docsisMainUsed) "MAIN" else "AUX"
                    }
                    else -> null
                }
                var normalizedSelection = initialSelection?.uppercase(Locale.getDefault())
                if (normalizedType == "docsisexpert") {
                    if (normalizedSelection == "IN") {
                        normalizedSelection = "AUX"
                    }
                    if (normalizedSelection == "MAIN") {
                        if (docsisMainUsed) {
                            normalizedSelection = "AUX"
                        } else {
                            docsisMainUsed = true
                        }
                    }
                    if (normalizedSelection == "AUXDC") {
                        if (!switchOptions.contains("AUXDC") || docsisAuxdcUsed) {
                            normalizedSelection = "AUX"
                        } else {
                            docsisAuxdcUsed = true
                        }
                    }
                }
                if (normalizedType == "channelexpert") {
                    if (normalizedSelection == "MAIN") {
                        if (channelMainUsed) {
                            normalizedSelection = "AUX"
                        } else {
                            channelMainUsed = true
                        }
                    }
                    if (normalizedSelection == "IN") {
                        if (channelInUsed) {
                            normalizedSelection = "AUX"
                        } else {
                            channelInUsed = true
                        }
                    }
                    if (normalizedSelection == "AUXDC") {
                        if (!switchOptions.contains("AUXDC") || channelAuxdcUsed) {
                            normalizedSelection = "AUX"
                        } else {
                            channelAuxdcUsed = true
                        }
                    }
                }
                normalizedSelection
            } else {
                null
            }
            val toleranceOverride = when (switchSelection) {
                "AUX" -> 8.0
                "AUXDC" -> 12.0
                else -> null
            }
            val useInputValidation = switchSelection == "IN" && ampInputStatus != null
            val id = hashId(type, testTime, testDurationMs, geoLocation)
            if (!seenIds.add(id)) {
                if (sourceFile != null) {
                    fileHasDuplicate = true
                } else {
                    duplicateEntryCount += 1
                    duplicateEntryNames.add(sourceLabel)
                }
                continue
            }
            val isSurplus = when (normalizedType) {
                "docsisexpert" -> docsisCount >= expectedDocsis
                "channelexpert" -> channelCount >= expectedChannel
                else -> false
            }
            if (isSurplus) {
                if (sourceFile != null && sourceFile.exists()) {
                    sourceFile.delete()
                }
                continue
            }
            when (normalizedType) {
                "docsisexpert" -> {
                    docsisCount += 1
                    docsisNames.add(sourceLabel)
                }
                "channelexpert" -> {
                    channelCount += 1
                    channelNames.add(sourceLabel)
                }
                else -> {
                    invalidTypeCount += 1
                    invalidTypeNames.add(sourceLabel)
                }
            }
            if (normalizedType == "docsisexpert" || normalizedType == "channelexpert") {
                if (geoResult.hasGeoField) {
                    if (geoResult.point == null) {
                        geoInvalidCount += 1
                        val coords = rawGeoKey(geoResult)
                        val detail = if (coords == null) {
                            geoResult.issue ?: "Georreferencia inválida"
                        } else {
                            "${geoResult.issue ?: "Georreferencia inválida"} ($coords)"
                        }
                        geoIssueDetails.add(
                            GeoIssueDetail(
                                type = normalizedType,
                                file = sourceLabel,
                                detail = detail
                            )
                        )
                    }
                } else {
                    geoMissingCount += 1
                    geoIssueDetails.add(
                        GeoIssueDetail(
                            type = normalizedType,
                            file = sourceLabel,
                            detail = geoResult.issue ?: "Georreferencia ausente"
                        )
                    )
                }
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
                val docsisIcfr = docsisFrequencies.associateWith { freq ->
                    rows.firstOrNull { it.frequencyMHz != null && kotlin.math.abs(it.frequencyMHz - freq) <= 0.5 }
                        ?.icfrDb
                }.filterValues { it != null }.mapValues { it.value!! }

                val pilotLevels = pilotChannels.associateWith { channel ->
                    rows.firstOrNull { it.channel == channel }?.levelDbmv
                }.filterValues { it != null }.mapValues { it.value!! }

                val docsisOk = mutableMapOf<Double, Boolean>()
                val pilotOk = mutableMapOf<Int, Boolean>()

                fun resolveTolerance(ruleTolerance: Double?, maxTolerance: Double?): Double? {
                    return when {
                        maxTolerance == null -> ruleTolerance
                        ruleTolerance == null -> maxTolerance
                        else -> minOf(ruleTolerance, maxTolerance)
                    }
                }
                fun applyPilotTolerance(
                    channel: Int,
                    adjusted: Double,
                    target: Double?,
                    ruleTolerance: Double?
                ): Boolean {
                    val tolerance = resolveTolerance(ruleTolerance, toleranceOverride)
                    if (target == null || tolerance == null) return true
                    return adjusted >= target - tolerance && adjusted <= target + tolerance
                }

                if (useInputValidation) {
                    val (ch50Ok, highOk) = ampInputStatus ?: (true to true)
                    pilotLevels.keys.forEach { channel ->
                        pilotOk[channel] = if (channel == 50) ch50Ok else highOk
                    }
                } else if (toleranceOverride != null) {
                    pilotLevels.forEach { (channel, level) ->
                        // Node RX: CH110 no se valida para niveles (dBmV).
                        if (assetType == AssetType.NODE && channel == 110) return@forEach
                        val adjusted = level + testPointOffset
                        val rule = rules
                            ?.optJSONObject("channelexpert")
                            ?.optJSONObject(if (assetType == AssetType.NODE) "node" else "amplifier")
                            ?.optJSONObject(equipmentKey)
                            ?.optJSONObject("channels")
                            ?.optJSONObject(channel.toString())
                        val ruleTolerance = rule?.optDouble("tolerance", Double.NaN)
                            ?.takeIf { !it.isNaN() }
                        val target = if (rule?.has("source") == true) {
                            adjustAmplifierTarget(amplifierTargets?.get(channel), switchSelection)
                        } else {
                            rule?.optDouble("target", Double.NaN)?.takeIf { !it.isNaN() }
                        }
                        pilotOk[channel] = applyPilotTolerance(channel, adjusted, target, ruleTolerance)
                    }
                } else if (rules != null) {
                    val assetKey = if (assetType == AssetType.NODE) "node" else "amplifier"
                    val docsisRules = rules.optJSONObject("docsisexpert")
                        ?.optJSONObject(assetKey)
                        ?.optJSONObject(equipmentKey)
                    val (min, max) = docsisRange(docsisRules)
                    docsisLevels.forEach { (freq, level) ->
                        val adjusted = level + testPointOffset
                        docsisOk[freq] = isWithinRange(adjusted, min, max)
                    }

                    val channelRules = rules.optJSONObject("channelexpert")
                        ?.optJSONObject(assetKey)
                        ?.optJSONObject(equipmentKey)
                        ?.optJSONObject("channels")
                    val legacyTx = if (assetType == AssetType.NODE && isLegacyNode) legacyNodeTxTargets(rules, equipmentKey, nodeTxType) else null
                    val legacyPilotChannels = setOf(50, 70, 116, 136)
                    pilotLevels.forEach { (channel, level) ->
                        // Node RX: CH110 no se valida para niveles (dBmV).
                        if (assetType == AssetType.NODE && channel == 110) {
                            if (legacyTx == null) return@forEach
                            val adjusted = level + testPointOffset
                            val ok = adjusted >= legacyTx.pilotTarget - legacyTx.pilotTolerance &&
                                adjusted <= legacyTx.pilotTarget + legacyTx.pilotTolerance
                            pilotOk[channel] = ok
                            return@forEach
                        }
                        if (assetType == AssetType.NODE && isLegacyNode && legacyTx != null && legacyPilotChannels.contains(channel)) {
                            val adjusted = level + testPointOffset
                            val target = legacyTx.pilotTarget + legacyTx.digitalOffset
                            val tol = legacyTx.digitalTolerance
                            pilotOk[channel] = adjusted >= target - tol && adjusted <= target + tol
                            return@forEach
                        }
                        val rule = channelRules?.optJSONObject(channel.toString())
                        val adjusted = level + testPointOffset
                        if (rule?.has("source") == true) {
                            val target = adjustAmplifierTarget(amplifierTargets?.get(channel), switchSelection)
                            val ruleTolerance = rule.optDouble("tolerance", 1.5)
                            pilotOk[channel] = applyPilotTolerance(channel, adjusted, target, ruleTolerance)
                        } else {
                            val target = rule?.optDouble("target", Double.NaN)?.takeIf { !it.isNaN() }
                            val ruleTolerance = rule?.optDouble("tolerance", Double.NaN)?.takeIf { !it.isNaN() }
                            pilotOk[channel] = applyPilotTolerance(channel, adjusted, target, ruleTolerance)
                        }
                    }
                }

                val common = rules?.optJSONObject("channelexpert")?.optJSONObject("common")
                val merMin = common?.optJSONObject("mer")?.optDouble("min", Double.NaN)?.takeIf { !it.isNaN() }
                val berPreMax = common?.optJSONObject("berPre")?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                val berPostMax = common?.optJSONObject("berPost")?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                val icfrMax = common?.optJSONObject("icfr")?.optDouble("max", Double.NaN)?.takeIf { !it.isNaN() }
                val digitalRows = rows.filter { it.channel != null && it.channel in 14..115 }
                    .mapNotNull { row ->
                        val channel = row.channel ?: return@mapNotNull null
                        DigitalChannelRow(
                            channel = channel,
                            frequencyMHz = row.frequencyMHz,
                            levelDbmv = row.levelDbmv,
                            levelOk = null,
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
                val ofdmSeries = collectOfdmSeries(results)

                measurementEntries.add(
                    MeasurementEntry(
                        label = sourceLabel,
                        type = normalizedType,
                        fromZip = sourceFile == null,
                        isDiscarded = isDiscarded,
                        geoLocation = geoResult.point,
                        docsisMeta = docsisMeta,
                        docsisLevels = docsisLevels,
                        docsisIcfr = docsisIcfr,
                        docsisLevelOk = docsisOk,
                        pilotMeta = pilotMeta,
                        pilotLevels = pilotLevels,
                        pilotLevelOk = pilotOk,
                        digitalRows = digitalRows,
                        ofdmSeries = ofdmSeries
                    )
                )

                val issues = validateMeasurementValues(
                    rules = rules,
                    test = test,
                    type = normalizedType,
                    equipmentKey = equipmentKey,
                    assetType = assetType,
                    amplifierTargets = amplifierTargets,
                    nodeTxType = nodeTxType,
                isLegacyNode = assetType == AssetType.NODE && asset.technology?.equals("Legacy", ignoreCase = true) == true,
                    skipChannelValidation = switchSelection == "IN",
                    toleranceOverride = toleranceOverride,
                    switchSelection = switchSelection
                )
                issues.forEach { issue ->
                    validationIssues.add(
                        ValidationIssue(
                            type = normalizedType,
                            label = sourceLabel,
                            message = issue
                        )
                    )
                }
            }
        }

        if (fileHasDuplicate && sourceFile != null) {
            if (sourceFile.exists() && sourceFile.delete()) {
                duplicateFileCount += 1
                duplicateFileNames.add(sourceLabel)
            } else {
                parseErrorCount += 1
                parseErrorNames.add(sourceLabel)
            }
        }
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
                if (file.exists()) {
                    file.delete()
                }
            }
            name.endsWith(".gz") -> {
                runCatching {
                    handlers.handleGzipBytes(file.readBytes(), sourceFile = file, sourceLabel = file.name)
                }.onFailure {
                    parseErrorCount += 1
                    parseErrorNames.add(file.name)
                }
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    val meetsMeasurementCounts = docsisCount >= expectedDocsis && channelCount >= expectedChannel
    var representativeGeo: GeoPoint? = null
    val geoWarnings = mutableListOf<String>()

    val photoGeoPoints = repository.getPhotosByAssetIdAndType(asset.id, PhotoType.MODULE)
        .mapNotNull { photo ->
            val latitude = photo.latitude
            val longitude = photo.longitude
            if (latitude != null && longitude != null) {
                GeoPoint(latitude = latitude, longitude = longitude)
            } else {
                null
            }
        }
    val geoPoints = buildList {
        addAll(
            measurementEntries
                .filter { !it.isDiscarded && (it.type == "docsisexpert" || it.type == "channelexpert") }
                .mapNotNull { it.geoLocation }
        )
        addAll(extraGeoPoints)
        addAll(photoGeoPoints)
    }

    if (geoPoints.isNotEmpty()) {
        val buckets = mutableMapOf<Pair<Int, Int>, MutableList<GeoPoint>>()
        geoPoints.forEach { point ->
            val key = bucketKey(point, cellMeters = 50.0)
            buckets.getOrPut(key) { mutableListOf() }.add(point)
        }
        val bestBucket = buckets.entries.maxByOrNull { it.value.size }
        if (buckets.size > 1) {
            geoWarnings.add("Las georreferencias no concuerdan (se detectaron ${buckets.size} ubicaciones distintas).")
        }
        bestBucket?.value?.let { points ->
            val avgLat = points.map { it.latitude }.average()
            val avgLon = points.map { it.longitude }.average()
            representativeGeo = GeoPoint(latitude = avgLat, longitude = avgLon)
        }
    } else if (meetsMeasurementCounts) {
        geoWarnings.add("No se encontraron georreferencias v?lidas para las mediciones.")
    }

    if (meetsMeasurementCounts) {
        if (geoMissingCount > 0) {
            geoWarnings.add("Faltan georreferencias en $geoMissingCount medici?n(es).")
        }
        if (geoInvalidCount > 0) {
            geoWarnings.add("Se detectaron $geoInvalidCount georreferencia(s) inv?lida(s).")
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
            add("Se encontraron mediciones con tipo inv?lido ($invalidTypeCount). Elimine las que no correspondan.")
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
        if (validationIssues.isNotEmpty()) {
            add("Se encontraron mediciones fuera de rango (${validationIssues.size}).")
        }
        if (geoWarnings.isNotEmpty()) {
            addAll(geoWarnings)
        }
    }
    val observationTotal = validationIssues.size + geoIssueDetails.size
    val observationGroups = validationIssues
        .groupBy { it.type to it.label }
        .map { (key, items) ->
            ObservationGroup(
                type = key.first,
                file = key.second,
                count = items.size
            )
        }
        .sortedWith(compareBy<ObservationGroup> { it.type }.thenBy { it.file })

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
            parseErrorNames = parseErrorNames.toList(),
            duplicateFileCount = duplicateFileCount,
            duplicateFileNames = duplicateFileNames.toList(),
            duplicateEntryCount = duplicateEntryCount,
            duplicateEntryNames = duplicateEntryNames.toList(),
            validationIssueNames = validationIssues.map { "${'$'}{it.label}: ${'$'}{it.message}" }
        ),
        warnings = warnings,
        geoLocation = representativeGeo,
        observationTotal = observationTotal,
        observationGroups = observationGroups,
        geoIssueDetails = geoIssueDetails,
        validationIssueDetails = validationIssues.map {
            ValidationIssueDetail(
                type = it.type,
                file = it.label,
                detail = it.message,
                isRuleViolation = true
            )
        }
    )
}

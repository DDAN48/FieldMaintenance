package com.example.fieldmaintenance.util

import com.example.fieldmaintenance.data.model.AssetType
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class MeasurementCounts(
    val docsisExpert: Int,
    val channelExpert: Int
)

data class RequiredCounts(
    val expectedDocsis: Int,
    val expectedChannel: Int
)

fun requiredCounts(assetType: AssetType, isModule: Boolean): RequiredCounts {
    return if (isModule) {
        RequiredCounts(expectedDocsis = 4, expectedChannel = 4)
    } else {
        when (assetType) {
            AssetType.NODE -> RequiredCounts(expectedDocsis = 0, expectedChannel = 1)
            AssetType.AMPLIFIER -> RequiredCounts(expectedDocsis = 3, expectedChannel = 4)
        }
    }
}

fun meetsRequired(counts: MeasurementCounts?, required: RequiredCounts): Boolean {
    if (counts == null) return false
    return counts.docsisExpert >= required.expectedDocsis &&
        counts.channelExpert >= required.expectedChannel
}

fun loadDiscardedLabels(file: File): Set<String> {
    if (!file.exists()) return emptySet()
    return runCatching { file.readLines().map { it.trim() }.filter { it.isNotBlank() }.toSet() }
        .getOrDefault(emptySet())
}

fun countMeasurements(files: List<File>, discardedLabels: Set<String>): MeasurementCounts {
    val seenIds = mutableSetOf<String>()
    var docsisCount = 0
    var channelCount = 0

    fun hashId(type: String, testTime: String, testDurationMs: String, geoLocation: String): String {
        val input = listOf(type, testTime, testDurationMs, geoLocation).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun handleJsonBytes(bytes: ByteArray, sourceLabel: String) {
        if (discardedLabels.contains(sourceLabel)) return
        val jsonText = runCatching { String(bytes) }.getOrNull().orEmpty()
        if (jsonText.isBlank()) return
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return
        val tests = json.optJSONArray("tests") ?: return
        for (i in 0 until tests.length()) {
            val test = tests.optJSONObject(i) ?: continue
            val type = test.optString("type").trim()
            val results = test.optJSONObject("results")
            val testTime = results?.optString("testTime").orEmpty()
            val testDurationMs = results?.optString("testDurationMs").orEmpty()
            val geoLocation = results?.optString("geoLocation").orEmpty()
            val id = hashId(type, testTime, testDurationMs, geoLocation)
            if (!seenIds.add(id)) continue
            when (type.lowercase(Locale.getDefault())) {
                "docsisexpert" -> docsisCount += 1
                "channelexpert" -> channelCount += 1
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

    fun handleGzipBytes(bytes: ByteArray, sourceLabel: String) {
        val decompressed = runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        }.getOrNull() ?: return
        if (isZipBytes(decompressed)) {
            runCatching {
                ZipInputStream(ByteArrayInputStream(decompressed)).use { nested: ZipInputStream ->
                    handleZipInputStream(nested)
                }
            }
            return
        }
        val jsonLabel = sourceLabel.removeSuffix(".gz")
        handleJsonBytes(decompressed, sourceLabel = jsonLabel)
    }

    fun handleZipInputStream(inputStream: ZipInputStream) {
        var entry = inputStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val entryName = entry.name.lowercase(Locale.getDefault())
                val bytes = runCatching { inputStream.readBytes() }.getOrNull()
                if (bytes != null) {
                    when {
                        entryName.endsWith(".zip") -> {
                            runCatching {
                                ZipInputStream(ByteArrayInputStream(bytes)).use { nested: ZipInputStream ->
                                    handleZipInputStream(nested)
                                }
                            }
                        }
                        entryName.endsWith(".gz") -> handleGzipBytes(bytes, sourceLabel = entry.name)
                        isJsonLike(entryName) -> handleJsonBytes(bytes, sourceLabel = entry.name)
                    }
                }
            }
            entry = inputStream.nextEntry
        }
    }

    files.forEach { file ->
        val name = file.name.lowercase(Locale.getDefault())
        when {
            isJsonLike(name) -> runCatching { handleJsonBytes(file.readBytes(), sourceLabel = file.name) }
            name.endsWith(".zip") -> runCatching {
                ZipInputStream(file.inputStream()).use { zip: ZipInputStream -> handleZipInputStream(zip) }
            }
            name.endsWith(".gz") -> runCatching { handleGzipBytes(file.readBytes(), sourceLabel = file.name) }
        }
    }

    return MeasurementCounts(docsisExpert = docsisCount, channelExpert = channelCount)
}

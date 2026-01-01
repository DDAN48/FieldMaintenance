package com.example.fieldmaintenance.util

import java.net.HttpURLConnection
import java.net.URL

object PlanSheetClient {
    /**
     * Construye URL CSV siempre hacia la primera hoja (gid=0).
     * Acepta URL tipo:
     * - https://docs.google.com/spreadsheets/d/<ID>/edit?usp=sharing
     */
    fun toCsvUrl(sheetUrl: String): String? {
        val trimmed = sheetUrl.trim()
        // If user already provided a direct CSV/published link, use it as-is.
        val lower = trimmed.lowercase()
        if (lower.contains("tqx=out:csv") || lower.contains("format=csv") || lower.contains("output=csv") || lower.endsWith(".csv")) {
            return trimmed
        }
        val id = extractSpreadsheetId(sheetUrl) ?: return null
        // gviz CSV es bastante tolerante y no requiere "export?format=csv"
        return "https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv&gid=0"
    }

    private fun extractSpreadsheetId(url: String): String? {
        val marker = "/spreadsheets/d/"
        val idx = url.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        val end = url.indexOf('/', start).let { if (it == -1) url.length else it }
        val id = url.substring(start, end).trim()
        return id.ifBlank { null }
    }

    fun downloadCsv(csvUrl: String, connectTimeoutMs: Int = 8000, readTimeoutMs: Int = 12000): String {
        val conn = (URL(csvUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "text/csv,*/*")
            // Some Google endpoints behave better with a User-Agent.
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) FieldMaintenance")
        }
        conn.connect()
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code downloading plan CSV")
        }
        return body
    }
}



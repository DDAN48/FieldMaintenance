package com.example.fieldmaintenance.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Reglas pedidas:
 * - Siempre leer primera hoja (gid=0)
 * - Refrescar al iniciar app
 * - Si el link no trae nada o falla, usar lo último guardado internamente
 */
class PlanRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore(appContext)
    private val cacheStore = PlanCacheStore(appContext)

    fun cacheFlow() = cacheStore.cache

    suspend fun refreshOnAppStart(): Boolean = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.first()
        refreshFromUrl(settings.sheetUrl)
    }

    /**
     * Intenta refrescar desde una URL (sheet). Si falla o no hay filas, NO modifica el cache.
     */
    suspend fun refreshFromUrl(sheetUrlRaw: String): Boolean = withContext(Dispatchers.IO) {
        refreshFromUrlDetailed(sheetUrlRaw).ok
    }

    data class RefreshResult(
        val ok: Boolean,
        val rowCount: Int = 0,
        val httpCode: Int? = null,
        val message: String = ""
    )

    /**
     * Igual que [refreshFromUrl], pero entrega detalles de error (ej: HTTP 403/404).
     */
    suspend fun refreshFromUrlDetailed(sheetUrlRaw: String): RefreshResult = withContext(Dispatchers.IO) {
        val sheetUrl = sheetUrlRaw.trim()
        if (sheetUrl.isBlank()) {
            return@withContext RefreshResult(
                ok = false,
                message = "URL vacía"
            )
        }

        val csvUrl = PlanSheetClient.toCsvUrl(sheetUrl)
            ?: return@withContext RefreshResult(ok = false, message = "URL inválida (no parece Google Sheets/CSV)")

        val csv = try {
            PlanSheetClient.downloadCsv(csvUrl)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val code = Regex("HTTP\\s+(\\d+)").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val hint = when (code) {
                401, 403 -> "Sin permisos: el Sheet no está público/publicado o requiere login."
                404 -> "No encontrado: revisa el link del Sheet."
                429 -> "Límite excedido: intenta más tarde."
                else -> ""
            }
            return@withContext RefreshResult(
                ok = false,
                httpCode = code,
                message = buildString {
                    if (code != null) append("HTTP $code. ")
                    if (hint.isNotBlank()) append(hint) else append("No se pudo descargar el CSV del Sheet.")
                }.trim()
            )
        }

        val rows = runCatching { PlanParser.parseRows(csv) }.getOrDefault(emptyList())
        if (rows.isEmpty()) {
            return@withContext RefreshResult(
                ok = false,
                message = "Sin filas válidas. Verifica que la primera hoja tenga datos y las columnas esperadas."
            )
        }

        cacheStore.save(
            PlanCache(
                fetchedAtEpochMs = System.currentTimeMillis(),
                sourceUrl = sheetUrl,
                rows = rows
            )
        )

        RefreshResult(ok = true, rowCount = rows.size, message = "OK")
    }

    suspend fun lookupByNode(nodeName: String): PlanRow? {
        val nodeKey = normalizeNode(nodeName)
        if (nodeKey.isBlank()) return null
        val cache = cacheStore.cache.first()
        return cache.rows.firstOrNull { normalizeNode(it.nodeCmts) == nodeKey }
    }

    private fun normalizeNode(v: String): String =
        v.trim().replace(Regex("\\s+"), " ").uppercase(Locale.getDefault())

    // parsing moved to PlanParser
}



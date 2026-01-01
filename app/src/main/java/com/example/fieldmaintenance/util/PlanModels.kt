package com.example.fieldmaintenance.util

/**
 * Representa una fila del "Plan" (Google Sheet).
 *
 * Columnas esperadas (tolerante a variaciones):
 * - Contratista
 * - Nodo CMTS
 * - Tecnología
 * - Ticket / Nombre del Evento (según como lo uses en el sheet)
 * - PO Directa
 * - PO Retorno
 * - Distancia SFP
 */
data class PlanRow(
    val contractor: String = "",
    val nodeCmts: String = "",
    val technology: String = "",
    val ticketOrEvent: String = "",
    val poDirecta: String = "",
    val poRetorno: String = "",
    val distanciaSfp: String = ""
)

data class PlanCache(
    val fetchedAtEpochMs: Long = 0L,
    val sourceUrl: String = "",
    val rows: List<PlanRow> = emptyList()
)



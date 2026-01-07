package com.example.fieldmaintenance.util

import java.util.Locale

object PlanParser {
    /**
     * Convierte el CSV de Google Sheets en filas tipadas.
     * Es tolerante al orden de columnas y a pequeñas variaciones de nombres.
     */
    fun parseRows(csv: String): List<PlanRow> {
        val table = PlanCsv.parse(csv).filter { it.any { c -> c.isNotBlank() } }
        if (table.isEmpty()) return emptyList()

        val header = table.first().map { it.trim() }
        val headerIndex = header.withIndex().associate { it.value.lowercase(Locale.getDefault()) to it.index }

        fun idxOf(vararg keys: String): Int? {
            for (k in keys) {
                val i = headerIndex[k.lowercase(Locale.getDefault())]
                if (i != null) return i
            }
            return null
        }

        val idxContractor = idxOf("contratista")
        val idxNode = idxOf("nombre del nodo", "nodo cmts", "nodo", "nodo_cmts")
        val idxTech = idxOf("tecnología", "tecnologia", "tecnologia ")
        val idxTicket = idxOf("nombre del evento", "ticket", "evento", "eventname")
        val idxPoDir = idxOf("po directa", "directa", "po_directa")
        val idxPoRet = idxOf("po retorno", "retorno", "po_retorno")
        val idxDist = idxOf("distancia sfp", "distancia", "distancia_sfp", "sfp")

        fun cell(row: List<String>, idx: Int?): String =
            if (idx == null) "" else row.getOrNull(idx)?.trim().orEmpty()

        return table.drop(1).mapNotNull { r ->
            val node = cell(r, idxNode)
            if (node.isBlank()) return@mapNotNull null
            PlanRow(
                contractor = cell(r, idxContractor),
                nodeCmts = node,
                technology = cell(r, idxTech),
                ticketOrEvent = cell(r, idxTicket),
                poDirecta = cell(r, idxPoDir),
                poRetorno = cell(r, idxPoRet),
                distanciaSfp = cell(r, idxDist)
            )
        }
    }
}



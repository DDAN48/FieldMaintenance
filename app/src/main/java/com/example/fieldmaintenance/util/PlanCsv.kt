package com.example.fieldmaintenance.util

/**
 * CSV mínimo (compatible con exports típicos de Google Sheets).
 * Maneja comillas dobles, comas dentro de comillas y saltos de línea dentro de comillas.
 */
object PlanCsv {
    fun parse(csv: String): List<List<String>> {
        if (csv.isBlank()) return emptyList()
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when (c) {
                '"' -> {
                    // Escaped quote inside quotes: ""
                    if (inQuotes && i + 1 < csv.length && csv[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) sb.append(c)
                    else {
                        row.add(sb.toString())
                        sb.setLength(0)
                    }
                }
                '\n' -> {
                    if (inQuotes) sb.append(c)
                    else {
                        row.add(sb.toString())
                        sb.setLength(0)
                        rows.add(row)
                        row = mutableListOf()
                    }
                }
                '\r' -> {
                    // ignore
                }
                else -> sb.append(c)
            }
            i++
        }
        // last cell
        row.add(sb.toString())
        // avoid adding trailing empty row when csv ends with newline
        if (!(row.size == 1 && row[0].isBlank() && rows.isNotEmpty())) {
            rows.add(row)
        }
        return rows
    }
}



package com.sentinel.recaudacion.csv

/** Parser/serializador CSV mínimo pero correcto (comillas, comas y saltos dentro de campos). */
object Csv {

    /** Parsea CSV (UTF-8). Quita BOM inicial. Devuelve filas de campos. Ignora filas vacías. */
    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        fun endField() { row.add(field.toString()); field = StringBuilder() }
        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> { /* ignora; \n cierra la fila */ }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        // última fila sin salto final
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Escapa un campo si contiene coma, comillas o salto de línea. */
    fun escape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val v = value.replace("\"", "\"\"")
        return if (needs) "\"$v\"" else v
    }

    /** Construye una línea CSV a partir de campos. */
    fun line(fields: List<String>): String = fields.joinToString(",") { escape(it) }
}

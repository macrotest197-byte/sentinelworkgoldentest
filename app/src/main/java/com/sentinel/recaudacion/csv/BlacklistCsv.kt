package com.sentinel.recaudacion.csv

import com.sentinel.recaudacion.data.model.BlacklistRule
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.data.model.Severity
import com.sentinel.recaudacion.detection.TextNormalizer

data class CsvRowError(val line: Int, val reason: String)
data class CsvRowParsed(val line: Int, val rule: BlacklistRule, val duplicateInFile: Boolean)

data class BlacklistImportPreview(
    val valid: List<CsvRowParsed>,
    val errors: List<CsvRowError>,
    val totalDataRows: Int,
) {
    val newCount: Int get() = valid.count { !it.duplicateInFile }
    val duplicateCount: Int get() = valid.count { it.duplicateInFile }
    val errorCount: Int get() = errors.size
}

object BlacklistCsv {

    val HEADER = listOf(
        "tipo",
        "valor",
        "descripcion",
        "severidad",
        "activo",
        "persona_id",
        "nombre_principal",
        "variantes",
        "escaneo",
        "excepto_en_dispositivos",
    )

    fun template(): String = buildString {
        appendLine(Csv.line(HEADER))
        appendLine(Csv.line(listOf("Alias", "patodm.nx", "Cuenta restringida", "Crítica", "si", "", "", "", "", "")))
        appendLine(Csv.line(listOf("CBU", "0000003100010000000001", "Ejemplo CBU", "Advertencia", "si", "", "", "", "", "")))
    }

    fun export(rules: List<BlacklistRule>): String = buildString {
        appendLine(Csv.line(HEADER))
        rules.forEach { r ->
            appendLine(
                Csv.line(
                    listOf(
                        r.type.name,
                        r.value,
                        r.description.orEmpty(),
                        r.severity.name,
                        if (r.active) "si" else "no",
                        r.personId.orEmpty(),
                        r.primaryName.orEmpty(),
                        r.knownNameVariants.joinToString("|"),
                        r.scanRoutes.orEmpty(),
                        r.exemptDevices.orEmpty(),
                    )
                )
            )
        }
    }

    /** Analiza el CSV y arma una vista previa con válidos, duplicados-en-archivo y errores. */
    fun preview(csvText: String): BlacklistImportPreview {
        val rows = Csv.parse(csvText)
        if (rows.isEmpty()) {
            return BlacklistImportPreview(emptyList(), listOf(CsvRowError(0, "El archivo está vacío")), 0)
        }
        val header = rows.first().map { normHeader(it) }
        val idxTipo = header.indexOf("tipo")
        val idxValor = header.indexOf("valor")
        val idxDesc = header.indexOf("descripcion")
        val idxSev = header.indexOf("severidad")
        val idxAct = header.indexOf("activo")
        val idxPersonId = header.indexOf("persona_id")
        val idxPrimaryName = header.indexOf("nombre_principal")
        val idxVariants = header.indexOf("variantes")
        val idxScan = header.indexOf("escaneo")
        val idxExempt = header.indexOf("excepto_en_dispositivos")
        if (idxTipo < 0 || idxValor < 0) {
            return BlacklistImportPreview(
                emptyList(),
                listOf(CsvRowError(1, "Faltan columnas obligatorias: se requieren al menos 'tipo' y 'valor'. Esperado: ${HEADER.joinToString(",")}")),
                0,
            )
        }

        val valid = mutableListOf<CsvRowParsed>()
        val errors = mutableListOf<CsvRowError>()
        val seen = HashSet<String>()
        val dataRows = rows.drop(1)

        dataRows.forEachIndexed { i, cols ->
            val lineNo = i + 2 // 1-based + encabezado
            fun cell(idx: Int) = if (idx in cols.indices) cols[idx].trim() else ""
            val valor = cell(idxValor)
            if (valor.isEmpty()) {
                errors.add(CsvRowError(lineNo, "El valor está vacío"))
                return@forEachIndexed
            }
            val type = mapType(cell(idxTipo))
            if (type == null) {
                errors.add(CsvRowError(lineNo, "Tipo no reconocido: '${cell(idxTipo)}'"))
                return@forEachIndexed
            }
            val severity = mapSeverity(cell(idxSev))
            val active = mapActive(cell(idxAct))
            val rule = BlacklistRule(
                type = type,
                value = valor,
                description = cell(idxDesc).ifEmpty { null },
                severity = severity,
                active = active,
                personId = cell(idxPersonId).ifEmpty { null },
                primaryName = cell(idxPrimaryName).ifEmpty { null },
                knownNameVariants = cell(idxVariants).split('|').map(String::trim).filter(String::isNotBlank),
                scanRoutes = cell(idxScan).ifEmpty { null },
                exemptDevices = cell(idxExempt).ifEmpty { null },
            )
            val dupKey = type.name + "|" + TextNormalizer.normalizeForType(type, valor)
            val isDup = !seen.add(dupKey)
            valid.add(CsvRowParsed(lineNo, rule, duplicateInFile = isDup))
        }
        return BlacklistImportPreview(valid, errors, dataRows.size)
    }

    private fun normHeader(s: String): String =
        TextNormalizer.normalizeText(s).replace("ó", "o")

    fun mapType(raw: String): RuleType? {
        val n = TextNormalizer.normalizeText(raw)
        if (n.isEmpty()) return null
        RuleType.entries.forEach {
            if (TextNormalizer.normalizeText(it.name) == n || TextNormalizer.normalizeText(it.label) == n) return it
        }
        return when (n) {
            "dni", "documento", "dni/documento" -> RuleType.DOCUMENTO
            "nombre", "nombre y apellido", "apellido" -> RuleType.NOMBRE
            "telefono", "tel", "celular", "cel", "movil" -> RuleType.TELEFONO
            "codigo", "codigo interno" -> RuleType.CODIGO
            "cuenta", "numero de cuenta", "nro cuenta" -> RuleType.CUENTA
            else -> null
        }
    }

    fun mapSeverity(raw: String): Severity {
        val n = TextNormalizer.normalizeText(raw)
        return when {
            n.startsWith("crit") -> Severity.CRITICA
            n.startsWith("adv") -> Severity.ADVERTENCIA
            else -> Severity.CRITICA
        }
    }

    fun mapActive(raw: String): Boolean {
        val n = TextNormalizer.normalizeText(raw)
        return when (n) {
            "no", "false", "0", "inactivo", "inactiva" -> false
            else -> true
        }
    }
}
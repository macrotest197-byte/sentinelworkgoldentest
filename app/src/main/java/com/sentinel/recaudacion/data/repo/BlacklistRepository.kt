package com.sentinel.recaudacion.data.repo

import com.sentinel.recaudacion.data.db.BlacklistDao
import com.sentinel.recaudacion.data.db.BlacklistEntity
import com.sentinel.recaudacion.data.model.BlacklistRule
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.Severity
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.PersonScopeFilter
import com.sentinel.recaudacion.detection.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/** Resultado de intentar guardar/importar una regla. */
sealed interface SaveResult {
    data class Ok(val id: Long) : SaveResult
    data object Duplicate : SaveResult
    data class Invalid(val reason: String) : SaveResult
}

class BlacklistRepository(private val dao: BlacklistDao) {

    fun observe(query: String): Flow<List<BlacklistRule>> {
        val source = if (query.isBlank()) dao.observeAll() else dao.search(query.trim())
        return source.map { list -> list.map { it.toDomain() } }
    }

    val count: Flow<Int> = dao.count()
    val countActive: Flow<Int> = dao.countActive()

    suspend fun getById(id: Long): BlacklistRule? = dao.getById(id)?.toDomain()

    suspend fun activeRules(): List<BlacklistRule> = dao.getActive().map { it.toDomain() }

    /** Active rules grouped into people when an administrator supplied the same personId. */
    suspend fun activePersons(): List<BlacklistPerson> = activeRules()
        .groupBy { it.personId?.takeIf(String::isNotBlank) ?: "rule-${it.id}" }
        .map { (personId, rules) ->
            val nameRule = rules.firstOrNull { it.type == RuleType.NOMBRE }
            val primary = rules.firstNotNullOfOrNull { it.primaryName?.takeIf(String::isNotBlank) }
                ?: nameRule?.value.orEmpty()
            val variants = (rules.flatMap { it.knownNameVariants } +
                    rules.filter { it.type == RuleType.NOMBRE }.map { it.value })
                .filter { it.isNotBlank() && it != primary }.distinct()
            // Las dos columnas son de persona, no de regla: se toma el primer valor no vacío.
            val scanRaw = rules.firstNotNullOfOrNull { it.scanRoutes?.takeIf(String::isNotBlank) }
            val exemptRaw = rules.firstNotNullOfOrNull { it.exemptDevices?.takeIf(String::isNotBlank) }
            BlacklistPerson(
                id = personId,
                primaryName = primary,
                knownNameVariants = variants,
                identifiers = rules.filter { it.type != RuleType.NOMBRE }
                    .map { PersonIdentifier(it.type, it.value) },
                severity = if (rules.any { it.severity == Severity.CRITICA }) Severity.CRITICA else Severity.ADVERTENCIA,
                scanRoutes = PersonScopeFilter.parseRoutes(scanRaw),
                exemptDevices = PersonScopeFilter.parseDevices(exemptRaw),
            )
        }

    suspend fun allRules(): List<BlacklistRule> = dao.getAll().map { it.toDomain() }

    suspend fun save(rule: BlacklistRule, allowDuplicate: Boolean = false): SaveResult {
        val value = rule.value.trim()
        if (value.isEmpty()) return SaveResult.Invalid("El valor no puede estar vacío")
        val normalized = TextNormalizer.normalizeForType(rule.type, value)
        if (normalized.isEmpty()) return SaveResult.Invalid("El valor no es válido")

        val now = System.currentTimeMillis()
        if (rule.id == 0L) {
            if (!allowDuplicate &&
                dao.existsByTypeAndValue(rule.type.name, normalized) > 0
            ) {
                return SaveResult.Duplicate
            }
            val id = dao.insert(
                BlacklistEntity(
                    type = rule.type.name,
                    value = value,
                    normalizedValue = normalized,
                    description = rule.description?.trim()?.ifEmpty { null },
                    severity = rule.severity.name,
                    exactMatch = rule.exactMatch,
                    active = rule.active,
                    createdAt = now,
                    updatedAt = now,
                    personId = rule.personId,
                    primaryName = rule.primaryName?.trim()?.ifEmpty { null },
                    knownNameVariants = JSONArray(rule.knownNameVariants.filter(String::isNotBlank).distinct()).toString(),
                    scanRoutes = rule.scanRoutes,
                    exemptDevices = rule.exemptDevices,
                )
            )
            return SaveResult.Ok(id)
        } else {
            val existing = dao.getById(rule.id) ?: return SaveResult.Invalid("La regla ya no existe")
            dao.update(
                existing.copy(
                    type = rule.type.name,
                    value = value,
                    normalizedValue = normalized,
                    description = rule.description?.trim()?.ifEmpty { null },
                    severity = rule.severity.name,
                    exactMatch = rule.exactMatch,
                    active = rule.active,
                    updatedAt = now,
                    personId = rule.personId,
                    primaryName = rule.primaryName?.trim()?.ifEmpty { null },
                    knownNameVariants = JSONArray(rule.knownNameVariants.filter(String::isNotBlank).distinct()).toString(),
                    scanRoutes = rule.scanRoutes,
                    exemptDevices = rule.exemptDevices,
                )
            )
            return SaveResult.Ok(rule.id)
        }
    }

    suspend fun setActive(rule: BlacklistRule, active: Boolean) {
        val existing = dao.getById(rule.id) ?: return
        dao.update(existing.copy(active = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(rule: BlacklistRule) = dao.deleteById(rule.id)

    /** Resultado de una importación CSV confirmada. */
    data class ImportResult(val inserted: Int, val skippedDuplicates: Int)
    data class ProvisionResult(val inserted: Int, val updated: Int)

    /** Validates the full payload first, then adds or updates rules without deleting omissions. */
    suspend fun applyProvisionedRules(rules: List<BlacklistRule>): ProvisionResult? {
        if (rules.isEmpty()) return null
        val keys = HashSet<String>()
        for (rule in rules) {
            val normalized = TextNormalizer.normalizeForType(rule.type, rule.value.trim())
            if (normalized.isEmpty()) return null
            if (!keys.add("${rule.type.name}|$normalized")) return null
        }
        var inserted = 0
        var updated = 0
        for (rule in rules) {
            val normalized = TextNormalizer.normalizeForType(rule.type, rule.value.trim())
            val existing = dao.getByTypeAndNormalizedValue(rule.type.name, normalized)
            val result = save(rule.copy(id = existing?.id ?: 0), allowDuplicate = false)
            if (result !is SaveResult.Ok) return null
            if (existing == null) inserted++ else updated++
        }
        return ProvisionResult(inserted, updated)
    }

    /**
     * Importa una lista de reglas. [skipDbDuplicates] = true omite las que ya existen
     * en la base (mismo tipo + valor normalizado). Devuelve el conteo real.
     */
    suspend fun importRules(rules: List<BlacklistRule>, skipDbDuplicates: Boolean = true): ImportResult {
        var inserted = 0
        var skipped = 0
        for (rule in rules) {
            when (save(rule.copy(id = 0), allowDuplicate = !skipDbDuplicates)) {
                is SaveResult.Ok -> inserted++
                SaveResult.Duplicate -> skipped++
                is SaveResult.Invalid -> skipped++
            }
        }
        return ImportResult(inserted, skipped)
    }

    private fun BlacklistEntity.toDomain() = BlacklistRule(
        id = id,
        type = RuleType.fromName(type),
        value = value,
        description = description,
        severity = Severity.fromName(severity),
        exactMatch = exactMatch,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
        personId = personId,
        primaryName = primaryName,
        knownNameVariants = runCatching {
            val array = JSONArray(knownNameVariants ?: "[]")
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList()),
        scanRoutes = scanRoutes,
        exemptDevices = exemptDevices,
    )
}
package com.sentinel.recaudacion.csv

import com.sentinel.recaudacion.data.db.IncidentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IncidentCsv {

    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    val HEADER = listOf(
        "fecha_hora", "dispositivo", "dispositivo_id", "persona", "persona_id",
        "coincidencia", "valor", "confianza", "severidad",
        "fuente", "app", "alarma", "telegram",
    )

    fun export(items: List<IncidentEntity>): String = buildString {
        appendLine(Csv.line(HEADER))
        items.forEach { e ->
            appendLine(
                Csv.line(
                    listOf(
                        fmt.format(Date(e.createdAt)),
                        e.device,
                        e.deviceId,
                        e.personName.orEmpty(),
                        e.personId.orEmpty(),
                        e.matchType,
                        e.matchedValue,
                        e.confidence?.toString().orEmpty(),
                        e.severity,
                        e.source,
                        e.app.orEmpty(),
                        e.alarmStatus,
                        e.telegramStatus,
                    )
                )
            )
        }
    }
}

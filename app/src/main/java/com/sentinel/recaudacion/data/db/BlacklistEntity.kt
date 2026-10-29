package com.sentinel.recaudacion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro de la lista negra. Cada valor a detectar es una fila independiente.
 *
 * [normalizedValue] se calcula al guardar y se usa para la comparación:
 * minúsculas y espacios colapsados para alias/nombres/códigos; para CBU/CVU/documento
 * se quitan separadores no numéricos sin alterar los dígitos.
 */
@Entity(
    tableName = "blacklist",
    indices = [Index(value = ["normalizedValue"]), Index(value = ["active"])]
)
data class BlacklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: String,
    val normalizedValue: String,
    val description: String? = null,
    val severity: String,
    val exactMatch: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val personId: String? = null,
    val primaryName: String? = null,
    val knownNameVariants: String? = null,
    val scanRoutes: String? = null,
    val exemptDevices: String? = null,
)

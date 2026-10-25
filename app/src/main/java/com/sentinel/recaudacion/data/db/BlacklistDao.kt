package com.sentinel.recaudacion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    @Query("SELECT * FROM blacklist ORDER BY active DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BlacklistEntity>>

    @Query(
        """
        SELECT * FROM blacklist
        WHERE value LIKE '%' || :q || '%'
           OR description LIKE '%' || :q || '%'
           OR primaryName LIKE '%' || :q || '%'
           OR knownNameVariants LIKE '%' || :q || '%'
           OR personId LIKE '%' || :q || '%'
           OR type LIKE '%' || :q || '%'
        ORDER BY active DESC, updatedAt DESC
        """
    )
    fun search(q: String): Flow<List<BlacklistEntity>>

    @Query("SELECT * FROM blacklist WHERE id = :id")
    suspend fun getById(id: Long): BlacklistEntity?

    /** Reglas activas en memoria para el motor de detección. */
    @Query("SELECT * FROM blacklist WHERE active = 1")
    suspend fun getActive(): List<BlacklistEntity>

    @Query("SELECT COUNT(*) FROM blacklist")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blacklist WHERE active = 1")
    fun countActive(): Flow<Int>

    /** Todas las reglas (activas e inactivas) para copia de seguridad / exportación. */
    @Query("SELECT * FROM blacklist ORDER BY type, value")
    suspend fun getAll(): List<BlacklistEntity>

    /** Para detectar duplicados en la importación: mismo tipo + mismo valor normalizado. */
    @Query("SELECT COUNT(*) FROM blacklist WHERE type = :type AND normalizedValue = :normalizedValue")
    suspend fun existsByTypeAndValue(type: String, normalizedValue: String): Int

    @Query("SELECT * FROM blacklist WHERE type = :type AND normalizedValue = :normalizedValue LIMIT 1")
    suspend fun getByTypeAndNormalizedValue(type: String, normalizedValue: String): BlacklistEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BlacklistEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<BlacklistEntity>): List<Long>

    @Update
    suspend fun update(entity: BlacklistEntity)

    @Delete
    suspend fun delete(entity: BlacklistEntity)

    @Query("DELETE FROM blacklist WHERE id = :id")
    suspend fun deleteById(id: Long)
}

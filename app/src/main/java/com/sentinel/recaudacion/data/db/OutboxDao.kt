package com.sentinel.recaudacion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(entity: OutboxEntity): Long

    @Update
    suspend fun update(entity: OutboxEntity)

    @Query("SELECT * FROM telegram_outbox WHERE status = 'PENDIENTE' ORDER BY createdAt ASC")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM telegram_outbox WHERE status = 'PENDIENTE'")
    fun countPending(): Flow<Int>

    @Query("SELECT * FROM telegram_outbox ORDER BY updatedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<OutboxEntity>>

    /** Para deduplicación: ¿hay un envío reciente con la misma clave (enviado o en cola)? */
    @Query(
        """
        SELECT COUNT(*) FROM telegram_outbox
        WHERE dedupKey = :dedupKey
          AND status IN ('PENDIENTE', 'ENVIADO')
          AND createdAt >= :sinceMillis
        """
    )
    suspend fun countRecentByDedup(dedupKey: String, sinceMillis: Long): Int

    @Query("DELETE FROM telegram_outbox WHERE status = 'ENVIADO' AND updatedAt < :beforeMillis")
    suspend fun purgeSentBefore(beforeMillis: Long)
}

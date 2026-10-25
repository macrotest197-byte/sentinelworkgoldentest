package com.sentinel.recaudacion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert
    suspend fun insert(entity: IncidentEntity): Long

    @Query(
        """
        SELECT * FROM incidents
        WHERE (:query = '' OR matchedValue LIKE '%' || :query || '%'
                         OR source LIKE '%' || :query || '%'
                         OR device LIKE '%' || :query || '%'
                         OR personName LIKE '%' || :query || '%'
                         OR personId LIKE '%' || :query || '%')
          AND (:severity = '' OR severity = :severity)
        ORDER BY createdAt DESC
        """
    )
    fun filter(query: String, severity: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents ORDER BY createdAt DESC")
    suspend fun all(): List<IncidentEntity>

    @Query("SELECT COUNT(*) FROM incidents")
    fun count(): Flow<Int>

    @Query("SELECT * FROM incidents ORDER BY createdAt DESC LIMIT 1")
    fun latest(): Flow<IncidentEntity?>
}

package com.egoflow.app.data.dao

import androidx.room.*
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvolutionBacklogDao {
    @Query("SELECT * FROM evolution_backlog ORDER BY captured_at DESC")
    fun getAll(): Flow<List<EvolutionBacklogEntity>>

    @Query("SELECT * FROM evolution_backlog WHERE source = :source ORDER BY captured_at DESC")
    fun getBySource(source: String): Flow<List<EvolutionBacklogEntity>>

    @Query("SELECT * FROM evolution_backlog WHERE status = 'PENDING' ORDER BY captured_at ASC")
    fun getPending(): Flow<List<EvolutionBacklogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EvolutionBacklogEntity)

    @Update
    suspend fun update(entry: EvolutionBacklogEntity)

    @Query("UPDATE evolution_backlog SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM evolution_backlog WHERE status != 'DEPRECATED' ORDER BY captured_at DESC")
    fun getAllNonDeprecated(): Flow<List<EvolutionBacklogEntity>>

    @Query("DELETE FROM evolution_backlog WHERE status = :status")
    suspend fun deleteAllByStatus(status: String)
}

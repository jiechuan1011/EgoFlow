package com.egoflow.app.data.repository

import com.egoflow.app.data.dao.EvolutionBacklogDao
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class EvolutionRepository(private val evolutionDao: EvolutionBacklogDao) {

    fun getAll(): Flow<List<EvolutionBacklogEntity>> = evolutionDao.getAll()

    fun getBySource(source: String): Flow<List<EvolutionBacklogEntity>> =
        evolutionDao.getBySource(source)

    fun getPending(): Flow<List<EvolutionBacklogEntity>> = evolutionDao.getPending()

    suspend fun addEntry(
        source: String,
        category: String,
        rawContent: String,
        aiRefinedSpec: String? = null
    ): EvolutionBacklogEntity {
        val entry = EvolutionBacklogEntity(
            id = UUID.randomUUID().toString(),
            source = source,
            category = category,
            rawContent = rawContent,
            aiRefinedSpec = aiRefinedSpec,
            capturedAt = System.currentTimeMillis(),
            status = "PENDING"
        )
        evolutionDao.insert(entry)
        return entry
    }

    suspend fun updateStatus(id: String, status: String) =
        evolutionDao.updateStatus(id, status)
}

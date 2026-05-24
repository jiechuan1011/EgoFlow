package com.egoflow.app.data.repository

import com.egoflow.app.data.dao.EvolutionBacklogDao
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class EvolutionRepository(private val evolutionDao: EvolutionBacklogDao) {

    /** 获取全部条目（含已废弃）—— 内部使用，不建议对外暴露 */
    fun getAll(): Flow<List<EvolutionBacklogEntity>> = evolutionDao.getAll()

    /** 获取非废弃条目（向 UI 展示的纯净数据源） */
    fun getAllNonDeprecated(): Flow<List<EvolutionBacklogEntity>> =
        evolutionDao.getAllNonDeprecated()

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

    /** 物理删除所有指定状态的条目 */
    suspend fun deleteAllByStatus(status: String) =
        evolutionDao.deleteAllByStatus(status)

    /** 将当前所有 PENDING 条目标记为 IMPLEMENTED */
    suspend fun markAllPendingAsImplemented() {
        evolutionDao.getAll().first().filter { it.status == "PENDING" }.forEach {
            evolutionDao.updateStatus(it.id, "IMPLEMENTED")
        }
    }
}

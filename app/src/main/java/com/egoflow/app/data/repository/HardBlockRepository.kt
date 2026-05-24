package com.egoflow.app.data.repository

import com.egoflow.app.data.dao.HardBlockDao
import com.egoflow.app.data.entity.HardBlockEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class HardBlockRepository(private val hardBlockDao: HardBlockDao) {

    fun getAll(): Flow<List<HardBlockEntity>> = hardBlockDao.getAll()

    fun getBlocksForDay(dayStart: Long, dayEnd: Long): Flow<List<HardBlockEntity>> =
        hardBlockDao.getBlocksForDay(dayStart, dayEnd)

    suspend fun getBlocksForDaySync(dayStart: Long, dayEnd: Long): List<HardBlockEntity> =
        hardBlockDao.getBlocksForDaySync(dayStart, dayEnd)

    suspend fun addBlock(
        subjectName: String,
        startTime: Long,
        endTime: Long
    ): HardBlockEntity {
        val block = HardBlockEntity(
            id = UUID.randomUUID().toString(),
            subjectName = subjectName,
            startTime = startTime,
            endTime = endTime
        )
        hardBlockDao.insert(block)
        return block
    }

    suspend fun deleteBlock(block: HardBlockEntity) = hardBlockDao.delete(block)

    /** 批量插入硬墙块 */
    suspend fun addBlocks(blocks: List<HardBlockEntity>) = hardBlockDao.insertAll(blocks)
}

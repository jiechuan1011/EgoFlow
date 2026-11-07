package com.egoflow.app.data.dao

import androidx.room.*
import com.egoflow.app.data.entity.HardBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HardBlockDao {
    @Query("SELECT * FROM hard_blocks ORDER BY start_time ASC")
    fun getAll(): Flow<List<HardBlockEntity>>

    @Query("SELECT * FROM hard_blocks WHERE start_time < :dayEnd AND end_time > :dayStart ORDER BY start_time ASC")
    fun getBlocksForDay(dayStart: Long, dayEnd: Long): Flow<List<HardBlockEntity>>

    @Query("SELECT * FROM hard_blocks WHERE start_time < :dayEnd AND end_time > :dayStart ORDER BY start_time ASC")
    suspend fun getBlocksForDaySync(dayStart: Long, dayEnd: Long): List<HardBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: HardBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<HardBlockEntity>)

    @Delete
    suspend fun delete(block: HardBlockEntity)
}

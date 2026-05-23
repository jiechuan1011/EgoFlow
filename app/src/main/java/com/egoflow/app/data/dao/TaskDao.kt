package com.egoflow.app.data.dao

import androidx.room.*
import com.egoflow.app.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY created_at DESC")
    fun getTasksByStatus(status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE category = :category AND status = 'POOL' ORDER BY created_at DESC")
    fun getPoolTasksByCategory(category: String): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks WHERE status = 'SCHEDULED' ORDER BY
            CASE WHEN drain_level = 'HIGH' THEN 0 ELSE 1 END, created_at ASC
    """)
    fun getScheduledTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT SUM(estimated_minutes) FROM tasks WHERE status = 'DONE' AND category = 'MAIN_LINE' AND created_at >= :since")
    suspend fun getCompletedMainLineMinutesSince(since: Long): Int?
}

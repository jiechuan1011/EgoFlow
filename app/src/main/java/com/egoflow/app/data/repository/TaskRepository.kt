package com.egoflow.app.data.repository

import com.egoflow.app.data.dao.TaskDao
import com.egoflow.app.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(private val taskDao: TaskDao) {

    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getPoolTasksByCategory(category: String): Flow<List<TaskEntity>> =
        taskDao.getPoolTasksByCategory(category)

    fun getScheduledTasks(): Flow<List<TaskEntity>> = taskDao.getScheduledTasks()

    suspend fun getTaskById(id: String): TaskEntity? = taskDao.getTaskById(id)

    suspend fun createTask(
        title: String,
        category: String,
        drainLevel: String,
        estimatedMinutes: Int,
        deadline: Long? = null
    ): TaskEntity {
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            category = category,
            drainLevel = drainLevel,
            status = "POOL",
            deadline = deadline,
            estimatedMinutes = estimatedMinutes,
            createdAt = System.currentTimeMillis()
        )
        taskDao.insert(task)
        return task
    }

    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)

    suspend fun updateTaskStatus(id: String, status: String) =
        taskDao.updateStatus(id, status)

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    suspend fun getCompletedMainLineMinutesSince(since: Long): Int =
        taskDao.getCompletedMainLineMinutesSince(since) ?: 0
}

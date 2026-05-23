package com.egoflow.app

import android.app.Application
import com.egoflow.app.data.database.AppDatabase
import com.egoflow.app.data.repository.*
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import com.egoflow.app.ai.DeepSeekService
import com.egoflow.app.ai.ClaudeService

class EgoFlowApp : Application() {

    lateinit var database: AppDatabase
        private set

    // Repositories
    lateinit var taskRepository: TaskRepository
        private set
    lateinit var evolutionRepository: EvolutionRepository
        private set
    lateinit var hardBlockRepository: HardBlockRepository
        private set
    lateinit var metricsRepository: MetricsRepository
        private set

    // Engines & Services
    lateinit var schedulingEngine: ElasticSchedulingEngine
        private set
    lateinit var deepSeekService: DeepSeekService
        private set
    lateinit var claudeService: ClaudeService
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)

        taskRepository = TaskRepository(database.taskDao())
        evolutionRepository = EvolutionRepository(database.evolutionBacklogDao())
        hardBlockRepository = HardBlockRepository(database.hardBlockDao())
        metricsRepository = MetricsRepository(database.dailyMetricsDao())

        schedulingEngine = ElasticSchedulingEngine()
        deepSeekService = DeepSeekService()
        claudeService = ClaudeService()
    }

    companion object {
        lateinit var instance: EgoFlowApp
            private set
    }
}

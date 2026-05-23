package com.egoflow.app

import android.app.Application
import android.util.Log
import com.egoflow.app.ai.AiConfig
import com.egoflow.app.ai.ClaudeService
import com.egoflow.app.ai.DeepSeekService
import com.egoflow.app.data.database.AppDatabase
import com.egoflow.app.data.repository.*
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EgoFlowApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var scheduleTemplateRepository: ScheduleTemplateRepository
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        android.util.Log.e("EgoFlowInit", "Application.onCreate() entered")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("EgoFlowInit", "=== CRASH on ${thread.name} ===", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            super.onCreate()
        } catch (e: Exception) {
            android.util.Log.e("EgoFlowInit", "super.onCreate() FAILED", e)
            throw e
        }

        instance = this

        try {
            database = AppDatabase.getInstance(this)
            settingsRepository = SettingsRepository(this)
            scheduleTemplateRepository = ScheduleTemplateRepository(this)

            // 从 DataStore 加载已保存的 API Key
            appScope.launch {
                AiConfig.deepSeekApiKey = settingsRepository.deepSeekApiKey.first()
                AiConfig.claudeApiKey = settingsRepository.claudeApiKey.first()
                AiConfig.deepSeekBaseUrl = settingsRepository.deepSeekBaseUrl.first()
                AiConfig.claudeBaseUrl = settingsRepository.claudeBaseUrl.first()
                Log.d("EgoFlowInit", "Settings loaded from DataStore")
            }

            taskRepository = TaskRepository(database.taskDao())
            evolutionRepository = EvolutionRepository(database.evolutionBacklogDao())
            hardBlockRepository = HardBlockRepository(database.hardBlockDao())
            metricsRepository = MetricsRepository(database.dailyMetricsDao())

            schedulingEngine = ElasticSchedulingEngine()
            deepSeekService = DeepSeekService()
            claudeService = ClaudeService()
            Log.d("EgoFlowInit", "All initialized")
        } catch (e: Exception) {
            Log.e("EgoFlowInit", "Init failed", e)
            throw e
        }
    }

    companion object {
        lateinit var instance: EgoFlowApp
            private set
    }
}

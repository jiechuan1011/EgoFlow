package com.egoflow.app

import android.app.Application
import android.util.Log
import com.egoflow.app.data.database.AppDatabase
import com.egoflow.app.data.repository.*
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import com.egoflow.app.ai.DeepSeekService
import com.egoflow.app.ai.ClaudeService
import java.io.File
import java.io.FileWriter

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
        setupCrashHandler()
        instance = this

        try {
            Log.d(TAG, "Initializing database...")
            database = AppDatabase.getInstance(this)
            Log.d(TAG, "Database initialized")

            taskRepository = TaskRepository(database.taskDao())
            evolutionRepository = EvolutionRepository(database.evolutionBacklogDao())
            hardBlockRepository = HardBlockRepository(database.hardBlockDao())
            metricsRepository = MetricsRepository(database.dailyMetricsDao())
            Log.d(TAG, "Repositories initialized")

            schedulingEngine = ElasticSchedulingEngine()
            deepSeekService = DeepSeekService()
            claudeService = ClaudeService()
            Log.d(TAG, "Services initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            writeCrashLog("init", e)
            throw e
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught crash on ${thread.name}", throwable)
                writeCrashLog("crash", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(type: String, throwable: Throwable) {
        try {
            val logFile = File(filesDir, "egoflow_crash.log")
            FileWriter(logFile, true).use { writer ->
                writer.appendLine("=== $type at ${System.currentTimeMillis()} ===")
                writer.appendLine(throwable.stackTraceToString())
                writer.appendLine()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "EgoFlow"
        lateinit var instance: EgoFlowApp
            private set
    }
}

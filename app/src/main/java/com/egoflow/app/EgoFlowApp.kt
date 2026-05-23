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
        // Log as early as possible — visible via `adb logcat -d | findstr EgoFlowInit`
        android.util.Log.e("EgoFlowInit", "Application.onCreate() entered")

        // Set up crash handler BEFORE super.onCreate() so init crashes are also caught
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("EgoFlowInit", "=== CRASH on ${thread.name} ===", throwable)
                // Try writing crash log to logcat AND file
                writeCrashLog(throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            super.onCreate()
            android.util.Log.e("EgoFlowInit", "super.onCreate() completed")
        } catch (e: Exception) {
            android.util.Log.e("EgoFlowInit", "super.onCreate() FAILED", e)
            writeCrashLog(e)
            throw e
        }

        instance = this
        android.util.Log.e("EgoFlowInit", "instance set")

        try {
            Log.d("EgoFlowInit", "Initializing database...")
            database = AppDatabase.getInstance(this)
            Log.d("EgoFlowInit", "Database initialized")

            taskRepository = TaskRepository(database.taskDao())
            evolutionRepository = EvolutionRepository(database.evolutionBacklogDao())
            hardBlockRepository = HardBlockRepository(database.hardBlockDao())
            metricsRepository = MetricsRepository(database.dailyMetricsDao())
            Log.d("EgoFlowInit", "Repositories initialized")

            schedulingEngine = ElasticSchedulingEngine()
            deepSeekService = DeepSeekService()
            claudeService = ClaudeService()
            Log.d("EgoFlowInit", "All services initialized successfully")
        } catch (e: Exception) {
            Log.e("EgoFlowInit", "Initialization FAILED", e)
            writeCrashLog(e)
            throw e
        }
    }

    private fun writeCrashLog(throwable: Throwable) {
        try {
            val logFile = File(cacheDir, "egoflow_crash.log")
            FileWriter(logFile, true).use { writer ->
                writer.appendLine("=== CRASH at ${System.currentTimeMillis()} ===")
                writer.appendLine(throwable.stackTraceToString())
                writer.appendLine()
            }
        } catch (e: Exception) {
            android.util.Log.e("EgoFlowInit", "Failed to write crash log", e)
        }
    }

    companion object {
        lateinit var instance: EgoFlowApp
            private set
    }
}

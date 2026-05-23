package com.egoflow.app

import android.app.Application
import android.util.Log
import com.egoflow.app.ai.AiConfig
import com.egoflow.app.ai.ClaudeService
import com.egoflow.app.ai.DeepSeekService
import com.egoflow.app.data.database.AppDatabase
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import com.egoflow.app.data.repository.*
import com.egoflow.app.scheduler.ElasticSchedulingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class EgoFlowApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var scheduleTemplateRepository: ScheduleTemplateRepository
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var milestoneRepository: MilestoneRepository
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
            chatRepository = ChatRepository(database.chatMessageDao())
            milestoneRepository = MilestoneRepository(this)

            // 从 DataStore 加载已保存的 API Key
            appScope.launch {
                AiConfig.deepSeekApiKey = settingsRepository.deepSeekApiKey.first()
                AiConfig.claudeApiKey = settingsRepository.claudeApiKey.first()
                AiConfig.openAiApiKey = settingsRepository.openAiApiKey.first()
                AiConfig.geminiApiKey = settingsRepository.geminiApiKey.first()
                AiConfig.customApiKey = settingsRepository.customApiKey.first()
                AiConfig.deepSeekBaseUrl = settingsRepository.deepSeekBaseUrl.first()
                AiConfig.claudeBaseUrl = settingsRepository.claudeBaseUrl.first()
                AiConfig.openAiBaseUrl = settingsRepository.openAiBaseUrl.first()
                AiConfig.geminiBaseUrl = settingsRepository.geminiBaseUrl.first()
                AiConfig.customBaseUrl = settingsRepository.customBaseUrl.first()
                AiConfig.customModelName = settingsRepository.customModelName.first()
                AiConfig.customProviderName = settingsRepository.customProviderName.first()
                AiConfig.chatProviderIndex = settingsRepository.chatProvider.first()
                AiConfig.blueprintProviderIndex = settingsRepository.blueprintProvider.first()
                Log.d("EgoFlowInit", "Settings loaded from DataStore")
            }

            taskRepository = TaskRepository(database.taskDao())
            evolutionRepository = EvolutionRepository(database.evolutionBacklogDao())
            hardBlockRepository = HardBlockRepository(database.hardBlockDao())
            metricsRepository = MetricsRepository(database.dailyMetricsDao())

            schedulingEngine = ElasticSchedulingEngine()
            deepSeekService = DeepSeekService()
            claudeService = ClaudeService()
            seedEvolutionBacklog()
            Log.d("EgoFlowInit", "All initialized")
        } catch (e: Exception) {
            Log.e("EgoFlowInit", "Init failed", e)
            throw e
        }
    }

    /** 首次启动时 seed 功能需求到进化中心 */
    private fun seedEvolutionBacklog() {
        appScope.launch {
            val existing = evolutionRepository.getAll().first()
            if (existing.isNotEmpty()) return@launch // 已有任何条目就跳过（防止覆盖安装重置）

            val seeds = listOf(
                Triple("UI_UX", "日程排版不够灵活", "生成的日程从早排到晚，不够合理。考试日程生成后无法确认后续内容是否已生成。" to null),
                Triple("FEATURE_REQ", "应用内更新推送", "更新推送能力不足，无法在应用内完成更新安装，需手动跳转 GitHub 下载。" to null),
                Triple("FEATURE_REQ", "自然语言输入规范化", "AI教练需要对用户的自然语言输入进行规范化处理，提高任务创建的准确性。" to null),
                Triple("FEATURE_REQ", "课程表管理改进", "课程表需要：一周一课表而非总表、导入去重、一键删除全部、针对不同周的课程管理。" to "已增加一键删除全部、ICS去重合并、按日期范围过滤周次")
            )

            seeds.forEach { (category, title, desc) ->
                evolutionRepository.addEntry(
                    source = "USER_PROMPT",
                    category = category,
                    rawContent = "$title：${desc.first}",
                    aiRefinedSpec = desc.second
                )
            }
            Log.d("EgoFlowInit", "Seeded ${seeds.size} evolution entries")
        }
    }

    companion object {
        lateinit var instance: EgoFlowApp
            private set
    }
}

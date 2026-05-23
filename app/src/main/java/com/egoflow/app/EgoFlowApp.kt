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
                Triple("FEATURE_REQ", "自动安排休息", "用户要求在日程中自动插入休息时段，避免连续学习。" to null),
                Triple("UI_UX", "导出蓝图自动标记已实现", "点击导出蓝图后，应自动将待处理条目标记为 IMPLEMENTED，无需逐个手动确认。" to null),
                Triple("FEATURE_REQ", "蓝图导出不可用", "蓝图文档的导出按钮是空的，点击无反应，需要实现真正的导出/分享功能。" to "已于 v2026.05.23-beta.12 修复"),
                Triple("FEATURE_REQ", "多场景指定不同 AI 模型", "日常对话和月规划应可分别指定不同的 AI 模型（DeepSeek/Claude/OpenAI/Gemini），并支持自定义配置参数。" to null),
                Triple("FEATURE_REQ", "任务完成勾选与延期", "用户无法确认任务是否完成，缺少打勾确认功能，也无法确认任务能否延期。" to null),
                Triple("UI_UX", "日程排版不合理", "生成的日程总是从早排到晚，不够合理。考试日程生成后无法确认后续内容是否已生成。" to null),
                Triple("FEATURE_REQ", "应用内更新推送", "更新推送能力不足，无法在应用内完成更新安装，需手动跳转 GitHub 下载。" to null),
                Triple("FEATURE_REQ", "AI自我进化功能无效", "用户抱怨 AI 自我进化功能没有实际效果，要求实现每天自动更新进化蓝图并推送改进建议。" to null),
                Triple("FEATURE_REQ", "删除不合理任务", "用户要求增加删除不合理任务的功能，当前只有标记完成和跳过，无法彻底删除。" to null),
                Triple("FEATURE_REQ", "从当前时间开始排程", "任务排程方式应从当下时间开始排，而非从早上6点开始排，避免排已过时的任务。" to null),
                Triple("FEATURE_REQ", "自然语言输入规范化", "AI教练需要对用户的自然语言输入进行规范化处理，提高任务创建的准确性。" to null),
                Triple("UI_UX", "应用到本周不可用", "课程表的应用到本周功能无反应，生成了硬墙但主界面不显示。" to null),
                Triple("FEATURE_REQ", "课程表管理改进", "课程表需要：一周一课表而非总表、导入去重、一键删除全部、针对不同周的课程管理。" to "已增加一键删除全部、ICS去重合并、按日期范围过滤周次"),
                Triple("FEATURE_REQ", "自动安排日常任务", "用户要求系统自动安排洗澡、洗衣服、搭衣服等日常任务，周循环并提供提示功能。" to null)
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

package com.egoflow.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.ai.AiConfig
import com.egoflow.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProviderConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val providerName: String = ""
)

data class SettingsUiState(
    val selectedTab: Int = 0,
    val deepSeek: ProviderConfig = ProviderConfig(baseUrl = "https://api.deepseek.com"),
    val claude: ProviderConfig = ProviderConfig(baseUrl = "https://api.anthropic.com"),
    val openAi: ProviderConfig = ProviderConfig(baseUrl = "https://api.openai.com"),
    val gemini: ProviderConfig = ProviderConfig(baseUrl = "https://generativelanguage.googleapis.com"),
    val custom: ProviderConfig = ProviderConfig(providerName = "自定义"),
    val chatProvider: Int = 0,
    val blueprintProvider: Int = 0,
    val saved: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 监听所有配置 Flow
        listOf(
            settingsRepository.deepSeekApiKey to { v: String -> copyDeepSeek { it.copy(apiKey = v) } },
            settingsRepository.deepSeekBaseUrl to { v: String -> copyDeepSeek { it.copy(baseUrl = v) } },
            settingsRepository.claudeApiKey to { v: String -> copyClaude { it.copy(apiKey = v) } },
            settingsRepository.claudeBaseUrl to { v: String -> copyClaude { it.copy(baseUrl = v) } },
            settingsRepository.openAiApiKey to { v: String -> copyOpenAi { it.copy(apiKey = v) } },
            settingsRepository.openAiBaseUrl to { v: String -> copyOpenAi { it.copy(baseUrl = v) } },
            settingsRepository.geminiApiKey to { v: String -> copyGemini { it.copy(apiKey = v) } },
            settingsRepository.geminiBaseUrl to { v: String -> copyGemini { it.copy(baseUrl = v) } },
            settingsRepository.customApiKey to { v: String -> copyCustom { it.copy(apiKey = v) } },
            settingsRepository.customBaseUrl to { v: String -> copyCustom { it.copy(baseUrl = v) } },
            settingsRepository.customModelName to { v: String -> copyCustom { it.copy(modelName = v) } },
            settingsRepository.customProviderName to { v: String -> copyCustom { it.copy(providerName = v) } },
            settingsRepository.chatProvider to { v: Int -> _uiState.update { it.copy(chatProvider = v) } },
            settingsRepository.blueprintProvider to { v: Int -> _uiState.update { it.copy(blueprintProvider = v) } }
        ).forEach { (flow, update) ->
            viewModelScope.launch { flow.collect { update(it) } }
        }
    }

    fun selectTab(index: Int) { _uiState.update { it.copy(selectedTab = index) } }

    fun updateApiKey(key: String) {
        val tab = _uiState.value.selectedTab
        viewModelScope.launch {
            when (tab) {
                0 -> { settingsRepository.saveDeepSeekKey(key); AiConfig.deepSeekApiKey = key }
                1 -> { settingsRepository.saveClaudeKey(key); AiConfig.claudeApiKey = key }
                2 -> { settingsRepository.saveOpenAiKey(key); AiConfig.openAiApiKey = key }
                3 -> { settingsRepository.saveGeminiKey(key); AiConfig.geminiApiKey = key }
                4 -> { settingsRepository.saveCustomKey(key); AiConfig.customApiKey = key }
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun updateBaseUrl(url: String) {
        val tab = _uiState.value.selectedTab
        viewModelScope.launch {
            when (tab) {
                0 -> { settingsRepository.saveDeepSeekUrl(url); AiConfig.deepSeekBaseUrl = url }
                1 -> { settingsRepository.saveClaudeUrl(url); AiConfig.claudeBaseUrl = url }
                2 -> { settingsRepository.saveOpenAiUrl(url); AiConfig.openAiBaseUrl = url }
                3 -> { settingsRepository.saveGeminiUrl(url); AiConfig.geminiBaseUrl = url }
                4 -> { settingsRepository.saveCustomUrl(url); AiConfig.customBaseUrl = url }
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun updateCustomModel(model: String) {
        viewModelScope.launch {
            settingsRepository.saveCustomModel(model)
            AiConfig.customModelName = model
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun updateCustomName(name: String) {
        viewModelScope.launch {
            settingsRepository.saveCustomName(name)
            AiConfig.customProviderName = name
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun setChatProvider(index: Int) {
        viewModelScope.launch {
            settingsRepository.saveChatProvider(index)
            AiConfig.chatProviderIndex = index
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun setBlueprintProvider(index: Int) {
        viewModelScope.launch {
            settingsRepository.saveBlueprintProvider(index)
            AiConfig.blueprintProviderIndex = index
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun clearSavedFlag() { _uiState.update { it.copy(saved = false) } }

    // 辅助方法
    private fun copyDeepSeek(fn: ProviderConfig.() -> ProviderConfig) =
        _uiState.update { it.copy(deepSeek = it.deepSeek.fn()) }
    private fun copyClaude(fn: ProviderConfig.() -> ProviderConfig) =
        _uiState.update { it.copy(claude = it.claude.fn()) }
    private fun copyOpenAi(fn: ProviderConfig.() -> ProviderConfig) =
        _uiState.update { it.copy(openAi = it.openAi.fn()) }
    private fun copyGemini(fn: ProviderConfig.() -> ProviderConfig) =
        _uiState.update { it.copy(gemini = it.gemini.fn()) }
    private fun copyCustom(fn: ProviderConfig.() -> ProviderConfig) =
        _uiState.update { it.copy(custom = it.custom.fn()) }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(EgoFlowApp.instance.settingsRepository) as T
        }
    }
}

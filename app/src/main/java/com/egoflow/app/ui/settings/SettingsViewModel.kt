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
        launchStringFlow(settingsRepository.deepSeekApiKey) { v -> updateState { it.copy(deepSeek = it.deepSeek.copy(apiKey = v)) } }
        launchStringFlow(settingsRepository.deepSeekBaseUrl) { v -> updateState { it.copy(deepSeek = it.deepSeek.copy(baseUrl = v)) } }
        launchStringFlow(settingsRepository.claudeApiKey) { v -> updateState { it.copy(claude = it.claude.copy(apiKey = v)) } }
        launchStringFlow(settingsRepository.claudeBaseUrl) { v -> updateState { it.copy(claude = it.claude.copy(baseUrl = v)) } }
        launchStringFlow(settingsRepository.openAiApiKey) { v -> updateState { it.copy(openAi = it.openAi.copy(apiKey = v)) } }
        launchStringFlow(settingsRepository.openAiBaseUrl) { v -> updateState { it.copy(openAi = it.openAi.copy(baseUrl = v)) } }
        launchStringFlow(settingsRepository.geminiApiKey) { v -> updateState { it.copy(gemini = it.gemini.copy(apiKey = v)) } }
        launchStringFlow(settingsRepository.geminiBaseUrl) { v -> updateState { it.copy(gemini = it.gemini.copy(baseUrl = v)) } }
        launchStringFlow(settingsRepository.customApiKey) { v -> updateState { it.copy(custom = it.custom.copy(apiKey = v)) } }
        launchStringFlow(settingsRepository.customBaseUrl) { v -> updateState { it.copy(custom = it.custom.copy(baseUrl = v)) } }
        launchStringFlow(settingsRepository.customModelName) { v -> updateState { it.copy(custom = it.custom.copy(modelName = v)) } }
        launchStringFlow(settingsRepository.customProviderName) { v -> updateState { it.copy(custom = it.custom.copy(providerName = v)) } }
        launchIntFlow(settingsRepository.chatProvider) { v -> updateState { it.copy(chatProvider = v) } }
        launchIntFlow(settingsRepository.blueprintProvider) { v -> updateState { it.copy(blueprintProvider = v) } }
    }

    private inline fun updateState(update: SettingsUiState.() -> SettingsUiState) {
        _uiState.update(update)
    }

    private fun launchStringFlow(flow: Flow<String>, block: suspend (String) -> Unit) {
        viewModelScope.launch { flow.collect { block(it) } }
    }

    private fun launchIntFlow(flow: Flow<Int>, block: suspend (Int) -> Unit) {
        viewModelScope.launch { flow.collect { block(it) } }
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

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(EgoFlowApp.instance.settingsRepository) as T
        }
    }
}

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
    val baseUrl: String = ""
)

data class SettingsUiState(
    val selectedTab: Int = 0,
    val deepSeek: ProviderConfig = ProviderConfig(baseUrl = "https://api.deepseek.com"),
    val claude: ProviderConfig = ProviderConfig(baseUrl = "https://api.anthropic.com"),
    val openAi: ProviderConfig = ProviderConfig(baseUrl = "https://api.openai.com"),
    val gemini: ProviderConfig = ProviderConfig(baseUrl = "https://generativelanguage.googleapis.com"),
    val saved: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        listOf(
            settingsRepository.deepSeekApiKey to { v: String -> _uiState.update { it.copy(deepSeek = it.deepSeek.copy(apiKey = v)) } },
            settingsRepository.deepSeekBaseUrl to { v: String -> _uiState.update { it.copy(deepSeek = it.deepSeek.copy(baseUrl = v)) } },
            settingsRepository.claudeApiKey to { v: String -> _uiState.update { it.copy(claude = it.claude.copy(apiKey = v)) } },
            settingsRepository.claudeBaseUrl to { v: String -> _uiState.update { it.copy(claude = it.claude.copy(baseUrl = v)) } },
            settingsRepository.openAiApiKey to { v: String -> _uiState.update { it.copy(openAi = it.openAi.copy(apiKey = v)) } },
            settingsRepository.openAiBaseUrl to { v: String -> _uiState.update { it.copy(openAi = it.openAi.copy(baseUrl = v)) } },
            settingsRepository.geminiApiKey to { v: String -> _uiState.update { it.copy(gemini = it.gemini.copy(apiKey = v)) } },
            settingsRepository.geminiBaseUrl to { v: String -> _uiState.update { it.copy(gemini = it.gemini.copy(baseUrl = v)) } }
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
            }
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

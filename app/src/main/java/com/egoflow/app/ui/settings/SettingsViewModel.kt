package com.egoflow.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.ai.AiConfig
import com.egoflow.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val deepSeekApiKey: String = "",
    val claudeApiKey: String = "",
    val deepSeekBaseUrl: String = "https://api.deepseek.com",
    val claudeBaseUrl: String = "https://api.anthropic.com",
    val saved: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.deepSeekApiKey.collect { key ->
                _uiState.update { it.copy(deepSeekApiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsRepository.claudeApiKey.collect { key ->
                _uiState.update { it.copy(claudeApiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsRepository.deepSeekBaseUrl.collect { url ->
                _uiState.update { it.copy(deepSeekBaseUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.claudeBaseUrl.collect { url ->
                _uiState.update { it.copy(claudeBaseUrl = url) }
            }
        }
    }

    fun updateDeepSeekKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveDeepSeekApiKey(key)
            AiConfig.deepSeekApiKey = key
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun updateClaudeKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveClaudeApiKey(key)
            AiConfig.claudeApiKey = key
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun updateDeepSeekUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.saveDeepSeekBaseUrl(url)
            AiConfig.deepSeekBaseUrl = url
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun updateClaudeUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.saveClaudeBaseUrl(url)
            AiConfig.claudeBaseUrl = url
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return SettingsViewModel(app.settingsRepository) as T
        }
    }
}

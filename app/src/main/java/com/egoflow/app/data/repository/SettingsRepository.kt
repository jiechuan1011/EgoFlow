package com.egoflow.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "egoflow_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        // DeepSeek
        private val DEEPSEEK_KEY = stringPreferencesKey("deepseek_api_key")
        private val DEEPSEEK_URL = stringPreferencesKey("deepseek_base_url")
        // Claude
        private val CLAUDE_KEY = stringPreferencesKey("claude_api_key")
        private val CLAUDE_URL = stringPreferencesKey("claude_base_url")
        // OpenAI
        private val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        private val OPENAI_URL = stringPreferencesKey("openai_base_url")
        // Gemini
        private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_URL = stringPreferencesKey("gemini_base_url")
        // 自定义
        private val CUSTOM_KEY = stringPreferencesKey("custom_api_key")
        private val CUSTOM_URL = stringPreferencesKey("custom_base_url")
        private val CUSTOM_MODEL = stringPreferencesKey("custom_model_name")
        private val CUSTOM_NAME = stringPreferencesKey("custom_provider_name")
        // 用途映射: 0=DeepSeek 1=Claude 2=OpenAI 3=Gemini 4=自定义
        private val CHAT_PROVIDER = intPreferencesKey("chat_provider")
        private val BLUEPRINT_PROVIDER = intPreferencesKey("blueprint_provider")
    }

    // DeepSeek
    val deepSeekApiKey: Flow<String> = dataStoreFlow(DEEPSEEK_KEY, "")
    val deepSeekBaseUrl: Flow<String> = dataStoreFlow(DEEPSEEK_URL, "https://api.deepseek.com")
    suspend fun saveDeepSeekKey(v: String) { save(DEEPSEEK_KEY, v) }
    suspend fun saveDeepSeekUrl(v: String) { save(DEEPSEEK_URL, v) }

    // Claude
    val claudeApiKey: Flow<String> = dataStoreFlow(CLAUDE_KEY, "")
    val claudeBaseUrl: Flow<String> = dataStoreFlow(CLAUDE_URL, "https://api.anthropic.com")
    suspend fun saveClaudeKey(v: String) { save(CLAUDE_KEY, v) }
    suspend fun saveClaudeUrl(v: String) { save(CLAUDE_URL, v) }

    // OpenAI
    val openAiApiKey: Flow<String> = dataStoreFlow(OPENAI_KEY, "")
    val openAiBaseUrl: Flow<String> = dataStoreFlow(OPENAI_URL, "https://api.openai.com")
    suspend fun saveOpenAiKey(v: String) { save(OPENAI_KEY, v) }
    suspend fun saveOpenAiUrl(v: String) { save(OPENAI_URL, v) }

    // Gemini
    val geminiApiKey: Flow<String> = dataStoreFlow(GEMINI_KEY, "")
    val geminiBaseUrl: Flow<String> = dataStoreFlow(GEMINI_URL, "https://generativelanguage.googleapis.com")
    suspend fun saveGeminiKey(v: String) { save(GEMINI_KEY, v) }
    suspend fun saveGeminiUrl(v: String) { save(GEMINI_URL, v) }

    // 自定义
    val customApiKey: Flow<String> = dataStoreFlow(CUSTOM_KEY, "")
    val customBaseUrl: Flow<String> = dataStoreFlow(CUSTOM_URL, "")
    val customModelName: Flow<String> = dataStoreFlow(CUSTOM_MODEL, "")
    val customProviderName: Flow<String> = dataStoreFlow(CUSTOM_NAME, "自定义")
    suspend fun saveCustomKey(v: String) { save(CUSTOM_KEY, v) }
    suspend fun saveCustomUrl(v: String) { save(CUSTOM_URL, v) }
    suspend fun saveCustomModel(v: String) { save(CUSTOM_MODEL, v) }
    suspend fun saveCustomName(v: String) { save(CUSTOM_NAME, v) }

    // 用途映射
    val chatProvider: Flow<Int> = dataStoreFlow(CHAT_PROVIDER, 0)
    val blueprintProvider: Flow<Int> = dataStoreFlow(BLUEPRINT_PROVIDER, 0)
    suspend fun saveChatProvider(v: Int) { save(CHAT_PROVIDER, v) }
    suspend fun saveBlueprintProvider(v: Int) { save(BLUEPRINT_PROVIDER, v) }

    private fun <T> dataStoreFlow(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

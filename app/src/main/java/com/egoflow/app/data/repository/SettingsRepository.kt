package com.egoflow.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "egoflow_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val DEEPSEEK_KEY = stringPreferencesKey("deepseek_api_key")
        private val DEEPSEEK_URL = stringPreferencesKey("deepseek_base_url")
        private val CLAUDE_KEY = stringPreferencesKey("claude_api_key")
        private val CLAUDE_URL = stringPreferencesKey("claude_base_url")
        private val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        private val OPENAI_URL = stringPreferencesKey("openai_base_url")
        private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_URL = stringPreferencesKey("gemini_base_url")
    }

    // DeepSeek
    val deepSeekApiKey: Flow<String> = context.dataStore.data.map { it[DEEPSEEK_KEY] ?: "" }
    val deepSeekBaseUrl: Flow<String> = context.dataStore.data.map { it[DEEPSEEK_URL] ?: "https://api.deepseek.com" }
    suspend fun saveDeepSeekKey(v: String) { context.dataStore.edit { it[DEEPSEEK_KEY] = v } }
    suspend fun saveDeepSeekUrl(v: String) { context.dataStore.edit { it[DEEPSEEK_URL] = v } }

    // Claude
    val claudeApiKey: Flow<String> = context.dataStore.data.map { it[CLAUDE_KEY] ?: "" }
    val claudeBaseUrl: Flow<String> = context.dataStore.data.map { it[CLAUDE_URL] ?: "https://api.anthropic.com" }
    suspend fun saveClaudeKey(v: String) { context.dataStore.edit { it[CLAUDE_KEY] = v } }
    suspend fun saveClaudeUrl(v: String) { context.dataStore.edit { it[CLAUDE_URL] = v } }

    // OpenAI
    val openAiApiKey: Flow<String> = context.dataStore.data.map { it[OPENAI_KEY] ?: "" }
    val openAiBaseUrl: Flow<String> = context.dataStore.data.map { it[OPENAI_URL] ?: "https://api.openai.com" }
    suspend fun saveOpenAiKey(v: String) { context.dataStore.edit { it[OPENAI_KEY] = v } }
    suspend fun saveOpenAiUrl(v: String) { context.dataStore.edit { it[OPENAI_URL] = v } }

    // Gemini
    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[GEMINI_KEY] ?: "" }
    val geminiBaseUrl: Flow<String> = context.dataStore.data.map { it[GEMINI_URL] ?: "https://generativelanguage.googleapis.com" }
    suspend fun saveGeminiKey(v: String) { context.dataStore.edit { it[GEMINI_KEY] = v } }
    suspend fun saveGeminiUrl(v: String) { context.dataStore.edit { it[GEMINI_URL] = v } }
}

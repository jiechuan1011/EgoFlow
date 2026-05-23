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
        private val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        private val DEEPSEEK_BASE_URL = stringPreferencesKey("deepseek_base_url")
        private val CLAUDE_BASE_URL = stringPreferencesKey("claude_base_url")
    }

    val deepSeekApiKey: Flow<String> = context.dataStore.data.map { it[DEEPSEEK_API_KEY] ?: "" }
    val claudeApiKey: Flow<String> = context.dataStore.data.map { it[CLAUDE_API_KEY] ?: "" }
    val deepSeekBaseUrl: Flow<String> = context.dataStore.data.map { it[DEEPSEEK_BASE_URL] ?: "https://api.deepseek.com" }
    val claudeBaseUrl: Flow<String> = context.dataStore.data.map { it[CLAUDE_BASE_URL] ?: "https://api.anthropic.com" }

    suspend fun saveDeepSeekApiKey(key: String) {
        context.dataStore.edit { it[DEEPSEEK_API_KEY] = key }
    }

    suspend fun saveClaudeApiKey(key: String) {
        context.dataStore.edit { it[CLAUDE_API_KEY] = key }
    }

    suspend fun saveDeepSeekBaseUrl(url: String) {
        context.dataStore.edit { it[DEEPSEEK_BASE_URL] = url }
    }

    suspend fun saveClaudeBaseUrl(url: String) {
        context.dataStore.edit { it[CLAUDE_BASE_URL] = url }
    }
}

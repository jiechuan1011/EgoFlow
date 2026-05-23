package com.egoflow.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "milestones")

data class Milestone(
    val id: String,
    val title: String,
    val date: String,       // YYYY-MM-DD
    val isExam: Boolean = true,
    val note: String = ""
)

class MilestoneRepository(private val context: Context) {
    companion object { private val KEY = stringPreferencesKey("milestones_json") }

    val milestones: Flow<List<Milestone>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: "[]"
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Milestone(o.getString("id"), o.getString("title"), o.getString("date"), o.optBoolean("isExam", true), o.optString("note", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun add(title: String, date: String, isExam: Boolean, note: String = "") {
        val current = getAll()
        val item = Milestone(UUID.randomUUID().toString(), title, date, isExam, note)
        saveAll(current + item)
    }

    suspend fun remove(id: String) {
        saveAll(getAll().filter { it.id != id })
    }

    suspend fun getAll(): List<Milestone> {
        val raw = context.dataStore.data.first()[KEY] ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Milestone(o.getString("id"), o.getString("title"), o.getString("date"), o.optBoolean("isExam", true), o.optString("note", ""))
            }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun saveAll(items: List<Milestone>) {
        val arr = JSONArray().apply {
            items.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("title", m.title); put("date", m.date); put("isExam", m.isExam); put("note", m.note)
                })
            }
        }
        context.dataStore.edit { it[KEY] = arr.toString() }
    }
}

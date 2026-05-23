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
    val date: String,          // YYYY-MM-DD
    val time: String? = null,  // HH:mm（可选）
    val type: String = "EXAM", // EXAM, DEADLINE, EVENT, OTHER
    val note: String = ""
) {
    val typeLabel: String
        get() = when (type) {
            "EXAM" -> "考试"
            "DEADLINE" -> "截止日期"
            "EVENT" -> "事件"
            else -> "其他"
        }
}

class MilestoneRepository(private val context: Context) {
    companion object { private val KEY = stringPreferencesKey("milestones_json") }

    val milestones: Flow<List<Milestone>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: "[]"
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Milestone(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    date = o.getString("date"),
                    time = o.optString("time", "").ifEmpty { null },
                    type = o.optString("type", "EXAM"),
                    note = o.optString("note", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun add(title: String, date: String, type: String = "EXAM", note: String = "", time: String? = null) {
        val current = getAll()
        val item = Milestone(UUID.randomUUID().toString(), title, date, time, type, note)
        saveAll(current + item)
    }

    suspend fun remove(id: String) {
        saveAll(getAll().filter { it.id != id })
    }

    suspend fun update(
        id: String,
        title: String,
        date: String,
        type: String = "EXAM",
        note: String = "",
        time: String? = null
    ) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = Milestone(id, title, date, time, type, note)
            saveAll(current)
        }
    }

    suspend fun getAll(): List<Milestone> {
        val raw = context.dataStore.data.first()[KEY] ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Milestone(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    date = o.getString("date"),
                    time = o.optString("time", "").ifEmpty { null },
                    type = o.optString("type", "EXAM"),
                    note = o.optString("note", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun saveAll(items: List<Milestone>) {
        val arr = JSONArray().apply {
            items.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("title", m.title); put("date", m.date)
                    put("time", m.time ?: ""); put("type", m.type); put("note", m.note)
                })
            }
        }
        context.dataStore.edit { it[KEY] = arr.toString() }
    }
}

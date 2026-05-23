package com.egoflow.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egoflow.app.domain.model.ScheduleTemplateItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule_template")

class ScheduleTemplateRepository(private val context: Context) {

    companion object {
        private val TEMPLATE_JSON = stringPreferencesKey("template_json")
    }

    val templateItems: Flow<List<ScheduleTemplateItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[TEMPLATE_JSON] ?: "[]"
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> parseItem(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveItems(items: List<ScheduleTemplateItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("subjectName", item.subjectName)
                put("dayOfWeek", item.dayOfWeek)
                put("startHour", item.startHour)
                put("startMinute", item.startMinute)
                put("endHour", item.endHour)
                put("endMinute", item.endMinute)
                item.validFrom?.let { put("validFrom", it) }
                item.validUntil?.let { put("validUntil", it) }
            })
        }
        context.dataStore.edit { it[TEMPLATE_JSON] = arr.toString() }
    }

    suspend fun addItem(
        subjectName: String,
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        val current = getAllItems()
        val newItem = ScheduleTemplateItem(
            id = UUID.randomUUID().toString(),
            subjectName = subjectName,
            dayOfWeek = dayOfWeek,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute
        )
        saveItems(current + newItem)
    }

    suspend fun removeItem(id: String) {
        val current = getAllItems()
        saveItems(current.filter { it.id != id })
    }

    suspend fun getAllItems(): List<ScheduleTemplateItem> {
        val raw = context.dataStore.data.first()[TEMPLATE_JSON] ?: "[]"
        return parseItems(raw)
    }

    private fun parseItems(raw: String): List<ScheduleTemplateItem> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> parseItem(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseItem(obj: JSONObject) = ScheduleTemplateItem(
        id = obj.getString("id"),
        subjectName = obj.getString("subjectName"),
        dayOfWeek = obj.getInt("dayOfWeek"),
        startHour = obj.getInt("startHour"),
        startMinute = obj.getInt("startMinute"),
        endHour = obj.getInt("endHour"),
        endMinute = obj.getInt("endMinute"),
        validFrom = if (obj.has("validFrom")) obj.getLong("validFrom") else null,
        validUntil = if (obj.has("validUntil")) obj.getLong("validUntil") else null
    )
}

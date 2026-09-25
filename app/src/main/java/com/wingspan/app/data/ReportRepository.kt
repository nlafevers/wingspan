package com.wingspan.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wingspan.app.domain.report.RangeReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class ReportRepository(private val dataStore: DataStore<Preferences>) {

    private val key = stringPreferencesKey("current_report")

    private val json = Json { ignoreUnknownKeys = true }

    val report: Flow<RangeReport> = dataStore.data.map { prefs ->
        prefs[key]?.let { text ->
            runCatching { json.decodeFromString<RangeReport>(text) }.getOrNull()
        } ?: RangeReport()
    }

    suspend fun update(transform: (RangeReport) -> RangeReport) {
        val current = report.first()
        val updated = transform(current)
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}

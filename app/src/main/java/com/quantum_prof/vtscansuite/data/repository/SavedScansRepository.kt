// data/repository/SavedScansRepository.kt
package com.quantum_prof.vtscansuite.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quantum_prof.vtscansuite.data.model.SavedScan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.savedScansStore by preferencesDataStore(name = "saved_scans")

@Singleton
class SavedScansRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val key = stringPreferencesKey("scans_json")
    private val serializer = ListSerializer(SavedScan.serializer())
    private val maxEntries = 100

    val savedScans: Flow<List<SavedScan>> = context.savedScansStore.data.map { prefs ->
        decode(prefs[key])
    }

    /** Fügt einen Scan hinzu oder ersetzt einen bestehenden mit gleicher ID (Position bleibt erhalten). */
    suspend fun upsert(scan: SavedScan) {
        context.savedScansStore.edit { prefs ->
            val current = decode(prefs[key])
            val idx = current.indexOfFirst { it.id == scan.id }
            val updated = if (idx >= 0) {
                current.toMutableList().also { it[idx] = scan }
            } else {
                listOf(scan) + current
            }
            prefs[key] = json.encodeToString(serializer, updated.take(maxEntries))
        }
    }

    suspend fun remove(id: String) {
        context.savedScansStore.edit { prefs ->
            val current = decode(prefs[key])
            prefs[key] = json.encodeToString(serializer, current.filterNot { it.id == id })
        }
    }

    private fun decode(raw: String?): List<SavedScan> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}

// data/repository/AppScanCacheRepository.kt
package com.quantum_prof.vtscansuite.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appScansStore by preferencesDataStore(name = "app_scans")

/**
 * Gemerkte Hash-Ergebnisse je installierter App.
 *
 * Bewusst DataStore und kein Room: es ist ein Cache, keine Datenbank. Ein paar hundert
 * Einträge sind als JSON wenige Dutzend Kilobyte, und das Projekt benutzt dasselbe Muster
 * schon für die gespeicherten Scans.
 *
 * Jeder Eintrag wird sofort geschrieben. Bricht ein Sammellauf ab – Prozess beendet,
 * Kontingent erschöpft –, ist alles bis dahin Geprüfte erhalten und der nächste Lauf
 * macht dort weiter.
 */
@Singleton
class AppScanCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val key = stringPreferencesKey("records_json")
    private val serializer = ListSerializer(AppScanRecord.serializer())

    /** Alle gemerkten Ergebnisse, nach Paketnamen. */
    val records: Flow<Map<String, AppScanRecord>> = context.appScansStore.data.map { prefs ->
        decode(prefs[key]).associateBy { it.packageName }
    }

    suspend fun put(record: AppScanRecord) {
        context.appScansStore.edit { prefs ->
            val current = decode(prefs[key]).filterNot { it.packageName == record.packageName }
            prefs[key] = json.encodeToString(serializer, current + record)
        }
    }

    /** Entfernt Einträge zu Apps, die nicht mehr installiert sind. */
    suspend fun retainOnly(packageNames: Set<String>) {
        context.appScansStore.edit { prefs ->
            val kept = decode(prefs[key]).filter { it.packageName in packageNames }
            prefs[key] = json.encodeToString(serializer, kept)
        }
    }

    /** Vergisst das Ergebnis zu einer App, damit die nächste Hash-Prüfung sie neu nachschlägt. */
    suspend fun remove(packageName: String) {
        context.appScansStore.edit { prefs ->
            val kept = decode(prefs[key]).filterNot { it.packageName == packageName }
            prefs[key] = json.encodeToString(serializer, kept)
        }
    }

    suspend fun clear() {
        context.appScansStore.edit { prefs -> prefs.remove(key) }
    }

    private fun decode(raw: String?): List<AppScanRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}

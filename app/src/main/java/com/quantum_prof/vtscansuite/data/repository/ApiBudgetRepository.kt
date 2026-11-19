// data/repository/ApiBudgetRepository.kt
package com.quantum_prof.vtscansuite.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mitgezählte VirusTotal-Anfragen des laufenden Tages.
 *
 * Das ist eine lokale Schätzung, keine Auskunft von VirusTotal: Anfragen von anderen
 * Geräten oder Programmen mit demselben Schlüssel sieht Veto nicht. Gezählt wird beim
 * Absenden, und der Zähler springt zur selben Zeit zurück wie das Kontingent bei
 * VirusTotal, nämlich um Mitternacht UTC.
 */
@Singleton
class ApiBudgetRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : BudgetReserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dayKey = stringPreferencesKey("day_utc")
    private val usedKey = intPreferencesKey("used_today")

    val usedToday: Flow<Int> = context.budgetStore.data.map { preferences ->
        if (preferences[dayKey] == todayUtc()) preferences[usedKey] ?: 0 else 0
    }

    /**
     * Gemeinsame Rechenlogik für Hash-Prüfung und Upload: zählt noch nicht gespeicherte
     * Anfragen mit und erlaubt Reservierungen (siehe [RequestBudget]).
     */
    private val budget = RequestBudget(DAILY_LIMIT) { usedToday.first() }

    /** Aus dem Netzwerk-Interceptor aufgerufen; darf nicht blockieren. */
    fun recordRequest() {
        budget.requestStarted()
        scope.launch {
            try {
                record()
            } finally {
                budget.requestPersisted()
            }
        }
    }

    /**
     * Reserviert [requests] Anfragen vom Tageskontingent oder liefert null, wenn es nicht
     * mehr reicht. Aufrufer geben die Reservierung nach den Anfragen wieder frei.
     */
    override suspend fun tryReserve(requests: Int): RequestBudget.Reservation? = budget.tryReserve(requests)

    private suspend fun record() {
        val today = todayUtc()
        context.budgetStore.edit { preferences ->
            val used = if (preferences[dayKey] == today) preferences[usedKey] ?: 0 else 0
            preferences[dayKey] = today
            preferences[usedKey] = used + 1
        }
    }

    private fun todayUtc(): String = LocalDate.now(ZoneOffset.UTC).toString()

    companion object {
        /** Free-Tier: 500 Anfragen pro Tag ... */
        const val DAILY_LIMIT = 500

        /** ... und 4 pro Minute. */
        const val PER_MINUTE_LIMIT = 4

        /** Abstand zwischen zwei Abfragen im Sammellauf, mit etwas Luft. */
        const val MIN_INTERVAL_MS = 16_000L

        /** So viele Apps nimmt sich ein Sammellauf höchstens vor. */
        const val MAX_PER_RUN = 100

        fun remaining(usedToday: Int): Int = (DAILY_LIMIT - usedToday).coerceAtLeast(0)
    }
}

private val Context.budgetStore by preferencesDataStore(name = "api_budget")

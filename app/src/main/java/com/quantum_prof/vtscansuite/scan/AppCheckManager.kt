// scan/AppCheckManager.kt
package com.quantum_prof.vtscansuite.scan

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.data.repository.AppScanCacheRepository
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import com.quantum_prof.vtscansuite.domain.usecase.HashFileUseCase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Warum ein Sammellauf geendet hat. */
enum class StopReason {
    /** Alle vorgesehenen Apps sind geprüft. */
    COMPLETED,

    /** Die Obergrenze eines einzelnen Laufs ist erreicht; es sind noch Apps offen. */
    RUN_LIMIT,

    /** Das Tageskontingent ist aufgebraucht. */
    DAILY_LIMIT,

    /** VirusTotal hat mit einem Rate-Limit geantwortet. */
    RATE_LIMITED,

    /** Es ist kein API-Schlüssel hinterlegt. */
    NO_API_KEY,

    /** Der Nutzer hat abgebrochen. */
    CANCELLED
}

sealed interface BulkCheckState {
    data object Idle : BulkCheckState

    data class Running(
        val done: Int,
        val total: Int,
        val currentApp: String
    ) : BulkCheckState

    data class Finished(
        val checked: Int,
        val unknown: Int,
        val flagged: Int,
        val failed: Int,
        val remainingApps: Int,
        val reason: StopReason
    ) : BulkCheckState
}

/**
 * Prüft installierte Apps **nur anhand ihres SHA-256**: nachgeschlagen wird, ob
 * VirusTotal die APK schon kennt. Hochgeladen wird hier nichts, niemals. Eine App, die
 * VirusTotal nicht kennt, bleibt unbekannt, bis der Nutzer sie einzeln prüfen lässt.
 *
 * System-Apps sind nie dabei. Das Tempo richtet sich nach dem Free-Tier (4 Anfragen pro
 * Minute), und jedes Ergebnis wird sofort gespeichert: bricht der Lauf ab, ist nichts
 * verloren und der nächste macht dort weiter.
 */
@Singleton
class AppCheckManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VirusTotalRepository,
    private val hashFileUseCase: HashFileUseCase,
    private val prefs: PrefsRepository,
    private val cache: AppScanCacheRepository,
    private val budget: ApiBudgetRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BulkCheckState>(BulkCheckState.Idle)
    val state: StateFlow<BulkCheckState> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Prüft alle übergebenen Apps, die noch kein gültiges Ergebnis haben.
     * System-Apps werden herausgefiltert, auch wenn sie in [apps] stehen.
     */
    fun checkAll(apps: List<InstalledApp>) {
        if (isRunning) return
        job = scope.launch {
            val key = prefs.apiKeyFlow.first()?.takeIf { it.isNotBlank() }
            if (key == null) {
                _state.value = finished(reason = StopReason.NO_API_KEY)
                return@launch
            }

            val known = cache.records.first()
            val pending = BulkCheckPlan.pending(apps, known)

            val allowance = BulkCheckPlan.allowance(budget.usedToday.first())
            if (allowance <= 0) {
                _state.value = finished(remainingApps = pending.size, reason = StopReason.DAILY_LIMIT)
                return@launch
            }

            // Erst jetzt, wo wirklich gearbeitet wird: der Lauf soll das Schließen der App
            // überleben, ein No-op-Lauf aber keine Benachrichtigung erzeugen.
            startService()

            val batch = pending.take(allowance)
            var checked = 0
            var unknown = 0
            var flagged = 0
            var failed = 0
            var stop = if (batch.size < pending.size) StopReason.RUN_LIMIT else StopReason.COMPLETED

            for ((index, app) in batch.withIndex()) {
                _state.value = BulkCheckState.Running(index, batch.size, app.name)

                // Zwischen zwei Anfragen warten, aber nicht vor der ersten.
                if (index > 0) delay(ApiBudgetRepository.MIN_INTERVAL_MS)

                when (checkApp(key, app)) {
                    CheckOutcome.KNOWN -> checked++
                    CheckOutcome.FLAGGED -> {
                        checked++
                        flagged++
                    }

                    CheckOutcome.UNKNOWN -> {
                        checked++
                        unknown++
                    }

                    CheckOutcome.RATE_LIMITED -> {
                        stop = StopReason.RATE_LIMITED
                        break
                    }

                    CheckOutcome.FAILED -> failed++
                }
            }

            val done = checked + failed
            _state.value = BulkCheckState.Finished(
                checked = checked,
                unknown = unknown,
                flagged = flagged,
                failed = failed,
                remainingApps = (pending.size - done).coerceAtLeast(0),
                reason = stop
            )
        }
    }

    /**
     * Startet den Vordergrunddienst. Schlägt das fehl (etwa weil Android den Start aus dem
     * Hintergrund verweigert), läuft der Scan trotzdem weiter, nur eben nur solange der
     * Prozess lebt.
     */
    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, AppCheckService::class.java))
        }
    }

    /** Ergebnis einer einzelnen Hash-Prüfung. */
    private enum class CheckOutcome { KNOWN, FLAGGED, UNKNOWN, RATE_LIMITED, FAILED }

    /**
     * Hasht die APK, schlägt den Hash nach und merkt sich das Ergebnis. Lädt nichts hoch:
     * eine unbekannte Datei bleibt unbekannt.
     */
    private suspend fun checkApp(key: String, app: InstalledApp): CheckOutcome {
        val hash = runCatching { hashFileUseCase(app.apkFile) }.getOrNull()
            ?: return CheckOutcome.FAILED

        val lookup = repository.getFileReport(key, hash)
        val error = lookup.exceptionOrNull()
        return when {
            lookup.isSuccess -> {
                val record = lookup.getOrNull().toRecord(app, hash)
                cache.put(record)
                if (record.flagged > 0) CheckOutcome.FLAGGED else CheckOutcome.KNOWN
            }

            UploadGate.isVerifiedUnknown(error) -> {
                cache.put(
                    AppScanRecord(
                        packageName = app.packageName,
                        sha256 = hash,
                        versionCode = app.versionCode,
                        apkSize = app.apkSize,
                        checkedAt = System.currentTimeMillis(),
                        outcome = AppScanOutcome.UNKNOWN
                    )
                )
                CheckOutcome.UNKNOWN
            }

            error is RateLimitException -> CheckOutcome.RATE_LIMITED

            // Netz- oder Serverfehler: nichts merken, der nächste Lauf versucht es erneut.
            else -> CheckOutcome.FAILED
        }
    }

    fun cancel() {
        val running = _state.value as? BulkCheckState.Running
        job?.cancel()
        job = null
        _state.value = BulkCheckState.Finished(
            checked = running?.done ?: 0,
            unknown = 0,
            flagged = 0,
            failed = 0,
            remainingApps = ((running?.total ?: 0) - (running?.done ?: 0)).coerceAtLeast(0),
            reason = StopReason.CANCELLED
        )
    }

    /** Abschlussmeldung als gesehen markieren. */
    fun acknowledge() {
        if (_state.value is BulkCheckState.Finished) _state.value = BulkCheckState.Idle
    }

    /** Übernimmt das Ergebnis einer einzeln gestarteten Prüfung in den Cache. */
    fun recordSingleResult(app: InstalledApp, report: FileReportResponse) {
        scope.launch {
            val hash = report.data.attributes.sha256
                ?: runCatching { hashFileUseCase(app.apkFile) }.getOrNull()
                ?: return@launch
            cache.put(report.toRecord(app, hash))
        }
    }

    /**
     * Merkt sich, dass VirusTotal diese App nicht kennt. Wird aus einem Einzelscan heraus
     * aufgerufen, sobald die Upload-Rückfrage erscheint – denn dahin führt nur eine
     * bestätigte 404.
     */
    fun recordUnknown(app: InstalledApp) {
        scope.launch {
            val hash = runCatching { hashFileUseCase(app.apkFile) }.getOrNull() ?: return@launch
            cache.put(
                AppScanRecord(
                    packageName = app.packageName,
                    sha256 = hash,
                    versionCode = app.versionCode,
                    apkSize = app.apkSize,
                    checkedAt = System.currentTimeMillis(),
                    outcome = AppScanOutcome.UNKNOWN
                )
            )
        }
    }

    /** Entfernt Einträge zu Apps, die nicht mehr installiert sind. */
    fun forgetUninstalled(installed: Set<String>) {
        scope.launch { cache.retainOnly(installed) }
    }

    private fun FileReportResponse?.toRecord(app: InstalledApp, hash: String): AppScanRecord {
        val stats = this?.data?.attributes?.lastAnalysisStats
        return AppScanRecord(
            packageName = app.packageName,
            sha256 = hash,
            versionCode = app.versionCode,
            apkSize = app.apkSize,
            checkedAt = System.currentTimeMillis(),
            outcome = AppScanOutcome.KNOWN,
            malicious = stats?.malicious ?: 0,
            suspicious = stats?.suspicious ?: 0,
            total = stats?.total ?: 0
        )
    }

    private fun finished(
        remainingApps: Int = 0,
        reason: StopReason
    ) = BulkCheckState.Finished(
        checked = 0,
        unknown = 0,
        flagged = 0,
        failed = 0,
        remainingApps = remainingApps,
        reason = reason
    )
}

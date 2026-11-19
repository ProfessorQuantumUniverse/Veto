// scan/AppCheckManager.kt
package com.quantum_prof.vtscansuite.scan

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.remote.BudgetExhaustedException
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.data.repository.AppScanCacheRepository
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import com.quantum_prof.vtscansuite.domain.usecase.HashFileUseCase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
    CANCELLED,

    /** Ein unerwarteter Fehler im Lauf selbst (etwa beim Lesen des Speichers). */
    ERROR,

    /** Es läuft schon eine andere Arbeit an VirusTotal (Einzelscan oder Upload-Stapel). */
    BUSY
}

sealed interface BulkCheckState {
    data object Idle : BulkCheckState

    /**
     * Fortschritt eines laufenden Sammellaufs.
     *
     * [total] ist die Zahl der Apps, die **dieser Lauf** prüft (höchstens
     * [ApiBudgetRepository.MAX_PER_RUN]) und bleibt für den ganzen Lauf gleich. [done] zählt
     * erledigte Apps: geprüft oder fehlgeschlagen. Die App in Arbeit zählt noch nicht.
     * [queued] sind Apps, die erst ein späterer Lauf prüft.
     */
    data class Running(
        val done: Int,
        val total: Int,
        val currentApp: String,
        val checked: Int = 0,
        val unknown: Int = 0,
        val flagged: Int = 0,
        val failed: Int = 0,
        val queued: Int = 0
    ) : BulkCheckState {
        /** Anteil erledigter Apps, immer zwischen 0 und 1. */
        val fraction: Float
            get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

        /** Nummer der App in Arbeit (ab 1), nie größer als [total]. */
        val position: Int get() = (done + 1).coerceAtMost(total)

        /** Beendet den Lauf mit den bisher *tatsächlich* erreichten Zählern. */
        fun toFinished(reason: StopReason) = Finished(
            checked = checked,
            unknown = unknown,
            flagged = flagged,
            failed = failed,
            remainingApps = (total - done).coerceAtLeast(0) + queued,
            reason = reason
        )
    }

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
    private val budget: ApiBudgetRepository,
    private val gate: BulkWorkGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BulkCheckState>(BulkCheckState.Idle)
    val state: StateFlow<BulkCheckState> = _state.asStateFlow()

    private val lock = Any()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Prüft alle übergebenen Apps, die noch kein gültiges Ergebnis haben.
     * System-Apps werden herausgefiltert, auch wenn sie in [apps] stehen.
     * Läuft schon ein Lauf, passiert nichts.
     */
    fun checkAll(apps: List<InstalledApp>) {
        synchronized(lock) {
            if (job?.isActive == true) return
            // Hash-Prüfung und Upload-Stapel teilen sich das Free-Tier: nie beide zugleich.
            if (!gate.tryAcquire(BulkWorkGate.HASH_CHECK)) {
                // Nicht stillschweigend verwerfen: der Nutzer sieht, warum nichts startet, und
                // kann es nach dem Ende der anderen Arbeit erneut anstoßen ("Continue").
                _state.value = finished(
                    remainingApps = apps.count { !it.isSystem },
                    reason = StopReason.BUSY
                )
                return
            }
            job = scope.launch {
                try {
                    runCheck(apps)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Der Lauf darf nicht auf "läuft" stehen bleiben: Zähler behalten, Grund nennen.
                    val running = _state.value as? BulkCheckState.Running
                    publish(running?.toFinished(StopReason.ERROR) ?: finished(reason = StopReason.ERROR))
                } finally {
                    gate.release(BulkWorkGate.HASH_CHECK)
                }
            }
        }
    }

    private suspend fun runCheck(apps: List<InstalledApp>) {
        val key = prefs.apiKeyFlow.first()?.takeIf { it.isNotBlank() }
        if (key == null) {
            publish(finished(reason = StopReason.NO_API_KEY))
            return
        }

        val known = cache.records.first()
        val pending = BulkCheckPlan.pending(apps, known)
        if (pending.isEmpty()) {
            publish(finished(reason = StopReason.COMPLETED))
            return
        }

        val allowance = BulkCheckPlan.allowance(budget.usedToday.first())
        if (allowance <= 0) {
            publish(finished(remainingApps = pending.size, reason = StopReason.DAILY_LIMIT))
            return
        }

        val batch = pending.take(allowance)
        val tally = BulkRunTally(total = batch.size, queued = pending.size - batch.size)

        // Der Zustand steht auf Running, *bevor* der Dienst startet: er soll beim ersten
        // Blick keinen alten Abschlusszustand sehen und sich sofort wieder beenden.
        publish(tally.running(batch.first().name))
        // Erst jetzt, wo wirklich gearbeitet wird: der Lauf soll das Schließen der App
        // überleben, ein No-op-Lauf aber keine Benachrichtigung erzeugen.
        startService()
        // Mindestabstand zur vorigen Arbeit an VirusTotal (Einzelscan, Upload-Stapel).
        gate.awaitSpacing()

        var stop = if (tally.queued > 0) StopReason.RUN_LIMIT else StopReason.COMPLETED
        for ((index, app) in batch.withIndex()) {
            if (index > 0) {
                publish(tally.running(app.name))
                // Zwischen zwei Anfragen warten, aber nicht vor der ersten.
                delay(ApiBudgetRepository.MIN_INTERVAL_MS)
            }

            // Das gemeinsame Tageskontingent reserviert das Repository vor jeder Anfrage; ist es
            // verbraucht, geht nichts raus und der Lauf hält an. Eine Prüfung kostet eine Anfrage.
            val outcome = checkApp(key, app)
            tally.record(outcome)
            if (outcome == CheckOutcome.RATE_LIMITED) {
                stop = StopReason.RATE_LIMITED
                break
            }
            if (outcome == CheckOutcome.BUDGET_EXHAUSTED) {
                stop = StopReason.DAILY_LIMIT
                break
            }
        }

        // Kein Running mit done == total: 100 % gibt es erst mit dem Abschluss.
        publish(tally.finished(stop))
    }

    /**
     * Schreibt den Zustand, aber nur für einen Lauf, der noch aktiv ist. Zusammen mit
     * [cancel] unter derselben Sperre: ein abgebrochener Lauf kann den Abbruchzustand nicht
     * mehr überschreiben.
     */
    private suspend fun publish(state: BulkCheckState) {
        val context = currentCoroutineContext()
        synchronized(lock) {
            if (context.isActive) _state.value = state
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

    /**
     * Hasht die APK, schlägt den Hash nach und merkt sich das Ergebnis. Lädt nichts hoch:
     * eine unbekannte Datei bleibt unbekannt.
     */
    private suspend fun checkApp(key: String, app: InstalledApp): CheckOutcome {
        val hash = try {
            hashFileUseCase(app.apkFile)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nicht lesbar oder verschwunden: diese App ist fehlgeschlagen, der Lauf geht weiter.
            return CheckOutcome.FAILED
        }
        currentCoroutineContext().ensureActive()

        val lookup = repository.getFileReport(key, hash)
        val error = lookup.exceptionOrNull()
        if (error is CancellationException) throw error
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
            error is BudgetExhaustedException -> CheckOutcome.BUDGET_EXHAUSTED

            // Netz- oder Serverfehler: nichts merken, der nächste Lauf versucht es erneut.
            else -> CheckOutcome.FAILED
        }
    }

    /**
     * Bricht den laufenden Sammellauf ab. Die Zähler sind die tatsächlich erreichten; eine
     * App, die gerade geprüft wurde, zählt nicht als erledigt. Ohne laufenden Lauf passiert
     * nichts.
     */
    fun cancel() {
        synchronized(lock) {
            val running = _state.value as? BulkCheckState.Running
            job?.cancel()
            job = null
            if (running != null) _state.value = running.toFinished(StopReason.CANCELLED)
        }
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

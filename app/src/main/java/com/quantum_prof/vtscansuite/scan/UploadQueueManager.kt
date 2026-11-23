// scan/UploadQueueManager.kt
package com.quantum_prof.vtscansuite.scan

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.repository.AppScanCacheRepository
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import com.quantum_prof.vtscansuite.domain.usecase.ExtractApkUseCase
import com.quantum_prof.vtscansuite.domain.usecase.HashFileUseCase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die Upload-Warteschlange des laufenden Prozesses.
 *
 * Sie entsteht nur aus einer [UploadConsent], bleibt im Speicher und wird nie gespeichert:
 * endet der Prozess, ist sie weg, und es braucht eine neue Durchsicht und eine neue
 * Einwilligung. Sie reicht Dateien nur ein ([VirusTotalRepository.submitFile]) und wartet
 * nicht auf eine Analyse; das Urteil holt später die Hash-Prüfung.
 *
 * Android garantiert nicht, dass der Vordergrunddienst den Prozess am Leben hält. Ein Upload,
 * der dabei mitten in der Übertragung endet, ist für VirusTotal unvollständig, aber Veto
 * kann das nicht sehen: nach einem Neustart zeigt die App ihn als nicht abgeschickt.
 */
@Singleton
class UploadQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VirusTotalRepository,
    private val hashFileUseCase: HashFileUseCase,
    private val prefs: PrefsRepository,
    private val cache: AppScanCacheRepository,
    private val extractApk: ExtractApkUseCase,
    private val gate: BulkWorkGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UploadQueueState>(UploadQueueState.Idle)
    val state: StateFlow<UploadQueueState> = _state.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    /** Kennzeichnet den Lauf, dem der Zustand gerade gehört. Null: kein Lauf schreibt mehr. */
    private var activeRun: Any? = null

    val isRunning: Boolean get() = _state.value is UploadQueueState.Running

    /**
     * Legt die Warteschlange aus der Einwilligung an und startet sie. Liefert false, wenn
     * schon etwas läuft (Upload oder Hash-Prüfung).
     */
    fun start(consent: UploadConsent): Boolean = synchronized(lock) {
        if (_state.value is UploadQueueState.Running) return false
        val items = consent.items.map { UploadItem(it) }
        if (!gate.tryAcquire(BulkWorkGate.UPLOAD)) {
            // Nicht verwerfen: die Einwilligung bleibt in dieser Sitzung gültig, und "Continue"
            // startet den Stapel, sobald die andere Arbeit beendet ist.
            _state.value = UploadQueueState.Finished(items, UploadStopReason.BUSY)
            return false
        }
        launchRun(items)
        true
    }

    /**
     * Setzt einen angehaltenen Lauf (Tageslimit, Rate-Limit) mit den noch offenen Einträgen
     * fort. Die Einwilligung gilt weiter, weil sie genau diese Einträge betraf. Nach einem
     * Rate-Limit erst, wenn die von VirusTotal genannte Wartezeit um ist.
     */
    fun resume(): Boolean = synchronized(lock) {
        val finished = _state.value as? UploadQueueState.Finished ?: return false
        if (!finished.canResume) return false
        val notBefore = finished.retryNotBeforeMillis
        if (notBefore != null && System.currentTimeMillis() < notBefore) return false
        if (!gate.tryAcquire(BulkWorkGate.UPLOAD)) return false
        launchRun(finished.items)
        true
    }

    /**
     * Bricht ab: offene Einträge werden "abgebrochen", der laufende Upload wird abgebrochen,
     * und es startet kein weiterer. Bereits abgeschickte Einträge bleiben abgeschickt.
     */
    fun cancel() {
        synchronized(lock) {
            val running = _state.value as? UploadQueueState.Running ?: return
            activeRun = null
            job?.cancel()
            job = null
            val items = running.items.toMutableList()
            UploadQueueRunner.cancelOpen(items)
            _state.value = UploadQueueState.Finished(items, UploadStopReason.CANCELLED)
        }
    }

    /** Abschlussmeldung als gesehen markieren. Die Warteschlange ist danach verworfen. */
    fun acknowledge() {
        synchronized(lock) {
            if (_state.value is UploadQueueState.Finished) _state.value = UploadQueueState.Idle
        }
    }

    /** Muss unter [lock] aufgerufen werden und hat die Sperre des Gates bereits. */
    private fun launchRun(initial: List<UploadItem>) {
        val token = Any()
        activeRun = token
        val items = initial.toMutableList()
        // Der Zustand steht auf Running, bevor der Dienst startet: er soll keinen alten
        // Abschlusszustand sehen und sich sofort wieder beenden.
        _state.value = UploadQueueState.Running(items.toList())
        job = scope.launch {
            try {
                runQueue(token, items)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fehler außerhalb des Ablaufs (etwa beim Lesen des Schlüssels): nicht auf "läuft" stehen bleiben.
                finish(token, items, UploadQueueRunner.Outcome(UploadStopReason.ERROR))
            } finally {
                gate.release(BulkWorkGate.UPLOAD)
            }
        }
    }

    private suspend fun runQueue(token: Any, items: MutableList<UploadItem>) {
        val key = prefs.apiKeyFlow.first()?.takeIf { it.isNotBlank() }
        if (key == null) {
            finish(token, items, UploadQueueRunner.Outcome(UploadStopReason.NO_API_KEY))
            return
        }
        startService()
        // Mindestabstand zur vorigen Arbeit an VirusTotal (Einzelscan, Hash-Prüfung).
        gate.awaitSpacing()
        val runner = UploadQueueRunner(Environment(key)) { snapshot ->
            publish(token, UploadQueueState.Running(snapshot))
        }
        finish(token, items, runner.run(items))
    }

    private fun finish(token: Any, items: List<UploadItem>, outcome: UploadQueueRunner.Outcome) {
        val notBefore = outcome.retryAfterSeconds?.let { System.currentTimeMillis() + it * 1000 }
        publish(
            token,
            UploadQueueState.Finished(items.toList(), outcome.reason, outcome.retryAfterSeconds, notBefore)
        )
        synchronized(lock) { if (activeRun === token) activeRun = null }
    }

    /** Schreibt den Zustand nur für den Lauf, dem er gehört: ein abgebrochener schreibt nichts mehr. */
    private fun publish(token: Any, state: UploadQueueState) {
        synchronized(lock) {
            if (activeRun === token) _state.value = state
        }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, UploadService::class.java))
        }
    }

    /** Die echten Zugriffe hinter [UploadEnvironment]. */
    private inner class Environment(private val key: String) : UploadEnvironment {
        override suspend fun resolveApp(packageName: String): InstalledApp? =
            withContext(Dispatchers.IO) { extractApk.getInstalledApp(packageName) }

        override suspend fun currentRecord(packageName: String): AppScanRecord? =
            cache.records.first()[packageName]

        override suspend fun hash(file: File): String =
            withContext(Dispatchers.IO) { hashFileUseCase(file) }

        override fun isReadable(file: File): Boolean = file.isFile && file.canRead()

        override suspend fun submit(file: File): Result<String> = repository.submitFile(key, file)

        override suspend fun pause(millis: Long) = delay(millis)

        override suspend fun onSubmitted(packageName: String) {
            cache.remove(packageName)
        }
    }
}

// scan/ScanManager.kt
package com.quantum_prof.vtscansuite.scan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.SavedScan
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.data.repository.SavedScansRepository
import com.quantum_prof.vtscansuite.domain.repository.ProgressCallback
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import com.quantum_prof.vtscansuite.domain.usecase.HashFileUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Zustand eines Scans – wird app-weit geteilt und überlebt die Activity. */
sealed interface ScanState {
    data object Idle : ScanState
    data class Running(
        val phase: ScanPhase,
        val progress: Float?,
        val startedAt: Long,
        val label: String
    ) : ScanState
    data class Success(val report: FileReportResponse, val label: String) : ScanState
    data class Error(val message: String, val label: String, val isRateLimit: Boolean = false) : ScanState

    /**
     * VirusTotal kennt die Datei nicht – für eine Analyse müsste sie vollständig hochgeladen
     * werden. Der Scan wartet in diesem Zustand auf die Entscheidung des Nutzers
     * ([ScanManager.confirmUpload] / [ScanManager.declineUpload]); ohne Zustimmung verlässt
     * kein Byte das Gerät.
     */
    data class UploadConsent(
        val label: String,
        val sizeBytes: Long,
        val subject: UploadSubject
    ) : ScanState
}

/**
 * Führt Scans in einem App-weiten Scope aus (überlebt das Schließen der Activity).
 * Zusammen mit [ScanService] (Foreground-Service) läuft der Scan auch weiter,
 * wenn die App in den Hintergrund geht oder geschlossen wird.
 */
@Singleton
class ScanManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VirusTotalRepository,
    private val hashFileUseCase: HashFileUseCase,
    private val prefs: PrefsRepository,
    private val savedScansRepository: SavedScansRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    @Volatile
    var currentLabel: String = ""
        private set

    private var job: Job? = null

    /** Offene Upload-Rückfrage; wird von der UI mit true/false beantwortet. */
    @Volatile
    private var uploadDecision: CompletableDeferred<Boolean>? = null

    init {
        // Kopien ausgewählter Dateien werden nach jedem Scan gelöscht. Stirbt der Prozess
        // mitten im Scan, bleiben sie liegen – beim Start aufräumen (hier läuft noch kein Scan).
        scope.launch {
            runCatching {
                File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() }
            }
        }
    }

    fun scanUrl(url: String) = start(url.trim()) { key, onProgress ->
        repository.scanUrl(key, url, onProgress)
    }

    fun scanApp(apkFile: File, name: String, isSystemApp: Boolean = false) = start(name) { key, onProgress ->
        val subject = if (isSystemApp) UploadSubject.SYSTEM_APP else UploadSubject.INSTALLED_APP
        analyzeFile(key, apkFile, subject, onProgress, deleteAfter = false)
    }

    fun scanUri(uri: Uri) {
        val name = queryDisplayName(uri) ?: "file"
        start(name) { key, onProgress ->
            onProgress(ScanPhase.CHECKING, null)
            val temp = copyToCache(uri, name)
            analyzeFile(key, temp, UploadSubject.PICKED_FILE, onProgress, deleteAfter = true)
        }
    }

    /** Der Nutzer hat dem Upload dieser einen Datei zugestimmt. */
    fun confirmUpload() {
        uploadDecision?.complete(true)
    }

    /** Der Nutzer will diese Datei nicht hochladen – der Scan endet ohne Upload. */
    fun declineUpload() {
        uploadDecision?.complete(false)
    }

    /**
     * Öffnet einen gespeicherten Scan und lädt seine Informationen frisch nach.
     * Schlägt die Aktualisierung fehl (offline/Rate-Limit), wird der gespeicherte
     * Bericht angezeigt. Kein Foreground-Service – das ist eine schnelle In-App-Abfrage.
     */
    fun openSaved(saved: SavedScan) {
        if (job?.isActive == true) return
        currentLabel = saved.label
        job = scope.launch {
            _state.value = ScanState.Running(ScanPhase.FETCHING, null, System.currentTimeMillis(), saved.label)
            val key = prefs.apiKeyFlow.first()?.takeIf { it.isNotBlank() }
            val fresh = if (key.isNullOrBlank()) null
            else repository.getReport(key, saved.id, saved.isUrl).getOrNull()

            if (fresh != null && fresh.data.attributes.lastAnalysisResults.isNotEmpty()) {
                runCatching { savedScansRepository.upsert(saved.copy(report = fresh)) }
                _state.value = ScanState.Success(fresh, saved.label)
            } else {
                _state.value = ScanState.Success(saved.report, saved.label)
            }
        }
    }

    /**
     * Erst den Hash nachschlagen, dann entscheiden:
     *
     * - Bericht mit Engine-Ergebnissen vorhanden → anzeigen, die Datei bleibt auf dem Gerät.
     * - Verifizierte 404-Antwort → VirusTotal kennt die Datei nicht. Erst **nachfragen**,
     *   dann hochladen.
     * - Jeder andere Fehlschlag (Rate-Limit, ungültiger Schlüssel, Serverfehler, kein Netz)
     *   → Fehler melden. Es wird nichts hochgeladen.
     */
    private suspend fun analyzeFile(
        key: String,
        file: File,
        subject: UploadSubject,
        onProgress: ProgressCallback,
        deleteAfter: Boolean
    ): Result<FileReportResponse> {
        return try {
            onProgress(ScanPhase.CHECKING, null)
            val hash = hashFileUseCase(file)
            val lookup = repository.getFileReport(key, hash)
            val known = lookup.getOrNull()
            val error = lookup.exceptionOrNull()
            when {
                // Bekannte Datei mit Ergebnissen: nichts verlässt das Gerät.
                known != null && known.data.attributes.lastAnalysisResults.isNotEmpty() ->
                    Result.success(known)

                // Fehlschlag, der *kein* verifiziertes "unbekannt" ist: niemals hochladen.
                error != null && !UploadGate.isVerifiedUnknown(error) ->
                    Result.failure(lookupFailure(error))

                // System-APKs verlassen das Gerät nie, auch nicht auf Wunsch.
                subject == UploadSubject.SYSTEM_APP ->
                    Result.failure(SystemAppUploadRefusedException())

                // Unbekannt (404) oder Eintrag ohne Engine-Ergebnisse: nur ein Upload bringt
                // ein Ergebnis. Zurückfragen, sofern der Nutzer das nicht abgeschaltet hat.
                else -> {
                    val mustAsk = prefs.askBeforeUploadFlow.first()
                    if (mustAsk && !awaitUploadConsent(file, subject)) {
                        Result.failure(UploadDeclinedException())
                    } else {
                        repository.uploadFile(key, file, knownHash = hash, onProgress = onProgress)
                    }
                }
            }
        } finally {
            if (deleteAfter) runCatching { file.delete() }
        }
    }

    /** Macht aus einem fehlgeschlagenen Lookup eine Meldung, die klarstellt: nichts ging raus. */
    private fun lookupFailure(error: Throwable): Throwable = when (error) {
        is RateLimitException -> error
        else -> Exception(
            (error.localizedMessage ?: "The hash lookup failed.") + " Nothing was uploaded."
        )
    }

    /**
     * Hält den Scan an und fragt die Zustimmung für genau diesen Upload ab. Kein erneuter
     * Lookup, der Scan wartet einfach an dieser Stelle.
     */
    private suspend fun awaitUploadConsent(file: File, subject: UploadSubject): Boolean {
        val decision = CompletableDeferred<Boolean>()
        uploadDecision = decision
        _state.value = ScanState.UploadConsent(
            label = currentLabel.ifBlank { file.name },
            sizeBytes = file.length(),
            subject = subject
        )
        return try {
            val granted = decision.await()
            if (granted) {
                // Sofort zurück in den Laufzustand: das Hashen einer großen APK dauert,
                // und so lange soll der Dialog nicht stehen bleiben.
                _state.value = ScanState.Running(
                    ScanPhase.UPLOADING, 0f, System.currentTimeMillis(), currentLabel
                )
            }
            granted
        } finally {
            uploadDecision = null
        }
    }

    private fun start(
        label: String,
        block: suspend (key: String, onProgress: ProgressCallback) -> Result<FileReportResponse>
    ) {
        if (job?.isActive == true) return // immer nur ein Scan gleichzeitig
        currentLabel = label
        job = scope.launch {
            val key = prefs.apiKeyFlow.first()?.takeIf { it.isNotBlank() }
            if (key.isNullOrBlank()) {
                _state.value = ScanState.Error("Your API key is missing or invalid.", label)
                return@launch
            }
            _state.value = ScanState.Running(ScanPhase.CHECKING, null, System.currentTimeMillis(), label)
            startService()

            val onProgress: ProgressCallback = { phase, progress ->
                val started = (_state.value as? ScanState.Running)?.startedAt ?: System.currentTimeMillis()
                _state.value = ScanState.Running(phase, progress, started, label)
            }

            val result = runCatching { block(key, onProgress) }.getOrElse { Result.failure(it) }
            _state.value = result.fold(
                onSuccess = { ScanState.Success(it, label) },
                onFailure = {
                    // Eine abgelehnte Upload-Rückfrage ist kein Fehler, sondern ein Abbruch.
                    if (it is UploadDeclinedException) ScanState.Idle
                    else ScanState.Error(
                        message = it.localizedMessage ?: "Scan failed.",
                        label = label,
                        isRateLimit = it is RateLimitException
                    )
                }
            )
        }
    }

    /** Ergebnis als gesehen markieren (nur wenn gerade kein Scan läuft). */
    fun acknowledge() {
        // Wird die Upload-Rückfrage weggetippt, gilt das als Ablehnung – sonst würde der
        // wartende Scan für immer hängen.
        if (_state.value is ScanState.UploadConsent) {
            declineUpload()
            return
        }
        if (_state.value !is ScanState.Running) _state.value = ScanState.Idle
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java))
        }
    }

    // -------- Datei-Hilfsfunktionen --------
    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    /**
     * Kopiert die gewählte Datei in ein eigenes Unterverzeichnis des Caches.
     * Der Dateiname stammt aus einem fremden ContentProvider und wird deshalb
     * bereinigt – sonst könnte ein Name wie "../databases/x" ausserhalb des
     * Cache-Verzeichnisses landen.
     */
    private fun copyToCache(uri: Uri, name: String): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val tempFile = File(dir, sanitizeFileName(name))
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to read the selected file.")
        return tempFile
    }

    /** Entfernt Pfadanteile und Sonderzeichen aus einem vom Nutzer/Provider gelieferten Namen. */
    private fun sanitizeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')
            .take(120)
        return base.ifBlank { "upload" }
    }

    private companion object {
        /** Unterverzeichnis im Cache für die temporären Kopien ausgewählter Dateien. */
        const val CACHE_DIR = "scan_uploads"
    }
}

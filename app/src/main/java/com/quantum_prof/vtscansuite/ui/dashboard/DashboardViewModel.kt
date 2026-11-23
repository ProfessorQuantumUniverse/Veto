// ui/dashboard/DashboardViewModel.kt
package com.quantum_prof.vtscansuite.ui.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.SavedScan
import com.quantum_prof.vtscansuite.data.model.savedScanFrom
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.data.repository.AppScanCacheRepository
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.data.repository.SavedScansRepository
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import androidx.compose.ui.graphics.ImageBitmap
import com.quantum_prof.vtscansuite.domain.usecase.AppIconLoader
import com.quantum_prof.vtscansuite.domain.usecase.ExtractApkUseCase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.scan.AppCheckManager
import com.quantum_prof.vtscansuite.scan.BulkCheckState
import com.quantum_prof.vtscansuite.scan.ScanManager
import com.quantum_prof.vtscansuite.scan.ScanState
import com.quantum_prof.vtscansuite.scan.UploadCandidate
import com.quantum_prof.vtscansuite.scan.UploadConsent
import com.quantum_prof.vtscansuite.scan.UploadEligibilityRules
import com.quantum_prof.vtscansuite.scan.UploadQueueManager
import com.quantum_prof.vtscansuite.scan.UploadQueueState
import com.quantum_prof.vtscansuite.scan.UploadSubject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val appCheckManager: AppCheckManager,
    private val appScanCache: AppScanCacheRepository,
    private val apiBudget: ApiBudgetRepository,
    private val extractApkUseCase: ExtractApkUseCase,
    private val appIconLoader: AppIconLoader,
    private val prefsRepository: PrefsRepository,
    private val savedScansRepository: SavedScansRepository,
    private val uploadQueueManager: UploadQueueManager
) : ViewModel() {

    // UI-Status leitet sich vom app-weiten ScanManager ab (überlebt die Activity)
    val uiState: StateFlow<DashboardState> = scanManager.state
        .map { it.toDashboardState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState.Idle)

    /**
     * Die Liste folgt den Einstellungen (System-Apps, WebAPKs) und meldet dabei einen
     * eigenen Ladezustand – sonst stünde beim Umschalten sekundenlang die alte Liste da.
     */
    /** Hochzählen lädt die Liste neu (Ziehen zum Aktualisieren). */
    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val appList: StateFlow<AppListState> = combine(
        prefsRepository.showSystemAppsFlow,
        prefsRepository.hideWebApksFlow,
        refreshTrigger
    ) { includeSystem, hideWebApks, _ -> includeSystem to hideWebApks }
        .flatMapLatest { (includeSystem, hideWebApks) ->
            flow {
                emit(AppListState.Loading)
                val apps = withContext(Dispatchers.IO) {
                    runCatching { extractApkUseCase.getInstalledApps(includeSystem, hideWebApks) }
                        .getOrDefault(emptyList())
                }
                emit(AppListState.Ready(apps))
            }
        }
        // Lazily statt WhileSubscribed: verlässt der Nutzer die Startseite (Ergebnisansicht), darf die
        // Liste nicht nach ein paar Sekunden neu eingelesen werden. Sonst stünde beim Zurückkommen
        // kurz "Loading" da, und die Scrollposition wäre weg.
        .stateIn(viewModelScope, SharingStarted.Lazily, AppListState.Loading)

    /** Lädt ein App-Icon, sobald eine Zeile sichtbar wird. */
    val loadAppIcon: suspend (String) -> ImageBitmap? = appIconLoader::load

    /** Wie viele WebAPK-Verknüpfungen installiert sind (Text in den Einstellungen). */
    val webApkCount: StateFlow<Int> = flow { emit(extractApkUseCase.countWebApks()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hideWebApks: StateFlow<Boolean> = prefsRepository.hideWebApksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Gemerkte Hash-Ergebnisse, nach Paketnamen. */
    val appRecords: StateFlow<Map<String, AppScanRecord>> = appScanCache.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Stand des Sammellaufs. */
    val bulkCheck: StateFlow<BulkCheckState> = appCheckManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BulkCheckState.Idle)

    /** Stand des Upload-Stapels (nur im Speicher). */
    val uploadState: StateFlow<UploadQueueState> = uploadQueueManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadQueueState.Idle)

    /**
     * Apps, die zum Upload angeboten werden dürfen: bestätigt unbekannt, zur installierten
     * Fassung passend, lesbar, keine System-App und kein WebAPK. Das Dateisystem wird dafür
     * angefasst, deshalb im IO-Kontext.
     */
    val uploadCandidates: StateFlow<List<UploadCandidate>> =
        combine(appList, appRecords) { list, records ->
            UploadEligibilityRules.candidates(list.appsOrEmpty, records)
        }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Heute verbrauchte VirusTotal-Anfragen (lokal mitgezählt). */
    val requestsUsedToday: StateFlow<Int> = apiBudget.usedToday
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val showSystemApps: StateFlow<Boolean> = prefsRepository.showSystemAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl.asStateFlow()

    val apiKey: StateFlow<String> = prefsRepository.apiKeyFlow
        .map { it ?: "" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val savedScans: StateFlow<List<SavedScan>> = savedScansRepository.savedScans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showHelp: StateFlow<Boolean> = prefsRepository.showHelpFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Rückfrage vor jedem Upload – an, solange der Nutzer sie nicht abgeschaltet hat. */
    val askBeforeUpload: StateFlow<Boolean> = prefsRepository.askBeforeUploadFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        // Ergebnisse zu deinstallierten Apps verwerfen. Bewusst über *alle* Pakete, nicht
        // über die gefilterte Liste: sonst flöge beim Ausblenden von System-Apps deren
        // Ergebnis gleich mit.
        viewModelScope.launch(Dispatchers.IO) {
            appCheckManager.forgetUninstalled(extractApkUseCase.installedPackageNames())
        }

        // Das Ergebnis einer einzeln gestarteten App-Prüfung landet im selben Cache wie
        // der Sammellauf – sonst stünde in der Liste weiter "unbekannt".
        viewModelScope.launch {
            scanManager.state.collect { state ->
                val app = pendingSingleCheck ?: return@collect
                when (state) {
                    is ScanState.Success -> {
                        appCheckManager.recordSingleResult(app, state.report)
                        pendingSingleCheck = null
                    }

                    // Dieser Zustand wird nur nach einer bestätigten 404 erreicht: die App
                    // ist VirusTotal unbekannt. Das gehört in die Liste, egal wie der
                    // Nutzer die Upload-Frage beantwortet.
                    is ScanState.UploadConsent -> appCheckManager.recordUnknown(app)

                    is ScanState.Error -> pendingSingleCheck = null
                    else -> Unit
                }
            }
        }
    }

    /** Welche App gerade einzeln geprüft wird (für die Übernahme ins Ergebnis). */
    private var pendingSingleCheck: InstalledApp? = null

    fun updateClipboardUrl(url: String?) {
        _clipboardUrl.value = url
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.saveApiKey(key)
        }
    }

    fun setShowHelp(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setShowHelp(enabled)
        }
    }

    fun setAskBeforeUpload(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setAskBeforeUpload(enabled)
        }
    }

    fun resetState() = scanManager.acknowledge()

    /** Upload dieser einen Datei freigeben bzw. ablehnen. */
    fun confirmUpload() = scanManager.confirmUpload()

    fun declineUpload() = scanManager.declineUpload()

    fun scanUrl(url: String) {
        if (url.isBlank()) return
        scanManager.scanUrl(url)
    }

    fun scanApp(app: InstalledApp) {
        pendingSingleCheck = app
        scanManager.scanApp(app.apkFile, app.name, isSystemApp = app.isSystem)
    }

    /** Sammellauf über alle sichtbaren Nicht-System-Apps. */
    fun checkAllHashes() = appCheckManager.checkAll(appList.value.appsOrEmpty)

    /** Liste neu einlesen, etwa nachdem eine App installiert oder entfernt wurde. */
    fun refreshApps() {
        refreshTrigger.value++
    }

    fun setHideWebApks(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setHideWebApks(enabled)
        }
    }

    fun cancelBulkCheck() = appCheckManager.cancel()

    /**
     * Startet den Upload-Stapel für genau die bestätigten Pakete. Aus der Auswahl wird nur
     * übernommen, was aktuell zur Auswahl angeboten wird: ein Paketname von außen genügt nicht.
     */
    fun startUpload(confirmedPackages: Set<String>) {
        val chosen = uploadCandidates.value.filter { it.packageName in confirmedPackages }
        if (chosen.isEmpty() || chosen.size > UploadConsent.MAX_ITEMS) return
        uploadQueueManager.start(UploadConsent.create(chosen))
    }

    fun cancelUpload() = uploadQueueManager.cancel()

    fun resumeUpload() {
        uploadQueueManager.resume()
    }

    fun acknowledgeUpload() = uploadQueueManager.acknowledge()

    fun acknowledgeBulkCheck() = appCheckManager.acknowledge()

    fun setShowSystemApps(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setShowSystemApps(enabled)
        }
    }

    fun scanUri(uri: Uri) = scanManager.scanUri(uri)

    fun toggleSave(report: FileReportResponse, label: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val id = report.data.id
            if (savedScans.value.any { it.id == id }) {
                savedScansRepository.remove(id)
            } else {
                savedScansRepository.upsert(savedScanFrom(report, labelOverride = label))
            }
        }
    }

    /** Öffnet einen gespeicherten Scan erneut – die Daten werden dabei automatisch aktualisiert. */
    fun openSavedScan(saved: SavedScan) = scanManager.openSaved(saved)

    fun deleteSavedScan(saved: SavedScan) {
        viewModelScope.launch(Dispatchers.IO) {
            savedScansRepository.remove(saved.id)
        }
    }

    private fun ScanState.toDashboardState(): DashboardState = when (this) {
        ScanState.Idle -> DashboardState.Idle
        is ScanState.Running -> DashboardState.Loading(
            message = phaseMessage(phase),
            progress = progress,
            phase = phase,
            startedAt = startedAt
        )
        is ScanState.Success -> DashboardState.Success(report, label)
        is ScanState.Error -> DashboardState.Error(message, isRateLimit)
        is ScanState.UploadConsent -> DashboardState.UploadConsent(
            label = label,
            sizeBytes = sizeBytes,
            isInstalledApp = subject == UploadSubject.INSTALLED_APP
        )
    }

    private fun phaseMessage(phase: ScanPhase): String = when (phase) {
        ScanPhase.CHECKING -> "Checking the VirusTotal database…"
        ScanPhase.UPLOADING -> "Uploading file to VirusTotal…"
        ScanPhase.SUBMITTING -> "Submitting URL for analysis…"
        ScanPhase.ANALYZING -> "Analyzing with 70+ security engines…"
        ScanPhase.FETCHING -> "Fetching the full report…"
    }
}

// ui/dashboard/DashboardViewModel.kt
package com.quantum_prof.vtscansuite.ui.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.SavedScan
import com.quantum_prof.vtscansuite.data.model.savedScanFrom
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.data.repository.SavedScansRepository
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import com.quantum_prof.vtscansuite.domain.usecase.ExtractApkUseCase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.scan.ScanManager
import com.quantum_prof.vtscansuite.scan.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val extractApkUseCase: ExtractApkUseCase,
    private val prefsRepository: PrefsRepository,
    private val savedScansRepository: SavedScansRepository
) : ViewModel() {

    // UI-Status leitet sich vom app-weiten ScanManager ab (überlebt die Activity)
    val uiState: StateFlow<DashboardState> = scanManager.state
        .map { it.toDashboardState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState.Idle)

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

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

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _installedApps.value = extractApkUseCase.getInstalledApps()
            } catch (e: Exception) {
                _installedApps.value = emptyList()
            }
        }
    }

    fun updateClipboardUrl(url: String?) {
        _clipboardUrl.value = url
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.saveApiKey(key)
        }
    }

    fun resetState() = scanManager.acknowledge()

    fun scanUrl(url: String) {
        if (url.isBlank()) return
        scanManager.scanUrl(url)
    }

    fun scanApp(app: InstalledApp) = scanManager.scanApp(app.apkFile, app.name)

    fun scanUri(uri: Uri) = scanManager.scanUri(uri)

    // -------- Gespeicherte Scans --------
    fun isSaved(reportId: String): Boolean = savedScans.value.any { it.id == reportId }

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
        is ScanState.Error -> DashboardState.Error(message)
    }

    private fun phaseMessage(phase: ScanPhase): String = when (phase) {
        ScanPhase.CHECKING -> "Checking the VirusTotal database…"
        ScanPhase.UPLOADING -> "Uploading file to VirusTotal…"
        ScanPhase.SUBMITTING -> "Submitting URL for analysis…"
        ScanPhase.ANALYZING -> "Analyzing with 70+ security engines…"
        ScanPhase.FETCHING -> "Fetching the full report…"
    }
}

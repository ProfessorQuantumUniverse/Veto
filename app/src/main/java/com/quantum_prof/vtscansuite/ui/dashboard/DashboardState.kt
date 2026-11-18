package com.quantum_prof.vtscansuite.ui.dashboard

import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp

/**
 * Zustand der App-Liste. Das Aufbauen dauert spürbar, deshalb ist "wird geladen" ein
 * eigener Zustand – eine leere Liste bedeutet jetzt wirklich, dass nichts da ist.
 */
sealed interface AppListState {
    data object Loading : AppListState
    data class Ready(val apps: List<InstalledApp>) : AppListState
}

/** Die Apps, oder eine leere Liste solange noch geladen wird. */
val AppListState.appsOrEmpty: List<InstalledApp>
    get() = (this as? AppListState.Ready)?.apps ?: emptyList()

sealed interface DashboardState {
    object Idle : DashboardState
    /** progress == null bedeutet unbestimmt (animierte Indeterminate-Bar). */
    data class Loading(
        val message: String,
        val progress: Float? = null,
        val phase: ScanPhase? = null,
        val startedAt: Long = 0L
    ) : DashboardState
    data class Success(val report: FileReportResponse, val label: String = "") : DashboardState
    data class Error(val errorMsg: String, val isRateLimit: Boolean = false) : DashboardState

    /**
     * VirusTotal kennt die Datei nicht. Für eine Analyse müsste sie vollständig hochgeladen
     * werden – der Scan wartet auf die Entscheidung des Nutzers.
     */
    data class UploadConsent(
        val label: String,
        val sizeBytes: Long,
        val isInstalledApp: Boolean
    ) : DashboardState
}
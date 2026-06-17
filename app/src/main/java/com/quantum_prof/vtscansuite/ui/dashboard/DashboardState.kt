package com.quantum_prof.vtscansuite.ui.dashboard

import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase

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
    data class Error(val errorMsg: String) : DashboardState
}
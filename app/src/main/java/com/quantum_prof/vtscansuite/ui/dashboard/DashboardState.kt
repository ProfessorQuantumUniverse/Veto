package com.quantum_prof.vtscansuite.ui.dashboard

import com.quantum_prof.vtscansuite.data.model.FileReportResponse

sealed interface DashboardState {
    object Idle : DashboardState
    data class Loading(val message: String) : DashboardState
    data class Success(val report: FileReportResponse) : DashboardState
    data class Error(val errorMsg: String) : DashboardState

}
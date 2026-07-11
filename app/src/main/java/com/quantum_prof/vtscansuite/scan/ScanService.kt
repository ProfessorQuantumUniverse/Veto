// scan/ScanService.kt
package com.quantum_prof.vtscansuite.scan

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-Service, der den Prozess während eines Scans am Leben hält
 * (auch wenn die App geschlossen wird) und Fortschritts- bzw. Ergebnis-Notifications zeigt.
 */
@AndroidEntryPoint
class ScanService : Service() {

    @Inject
    lateinit var scanManager: ScanManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ScanNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initial = scanManager.state.value as? ScanState.Running
        startInForeground(initial?.phase, initial?.progress, initial?.label ?: scanManager.currentLabel)

        scope.launch {
            scanManager.state.collect { state ->
                when (state) {
                    is ScanState.Running -> updateOngoing(state)
                    is ScanState.Success -> {
                        ScanNotifications.showResult(this@ScanService, resultTitle(state), resultText(state))
                        finish()
                    }
                    is ScanState.Error -> {
                        ScanNotifications.showResult(
                            this@ScanService,
                            "Scan failed",
                            "${state.label}: ${state.message}"
                        )
                        finish()
                    }
                    ScanState.Idle -> finish()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(phase: ScanPhase?, progress: Float?, label: String) {
        val notification = ScanNotifications.buildOngoing(this, phase, progress, label)
        ServiceCompat.startForeground(
            this, ScanNotifications.ONGOING_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun updateOngoing(state: ScanState.Running) {
        runCatching {
            NotificationManagerCompat.from(this).notify(
                ScanNotifications.ONGOING_ID,
                ScanNotifications.buildOngoing(this, state.phase, state.progress, state.label)
            )
        }
    }

    private fun resultTitle(state: ScanState.Success): String {
        val stats = state.report.data.attributes.lastAnalysisStats
        return when {
            stats.malicious > 0 -> "⚠️ Threat detected"
            stats.suspicious > 0 -> "Suspicious item"
            else -> "Scan complete — clean"
        }
    }

    private fun resultText(state: ScanState.Success): String {
        val stats = state.report.data.attributes.lastAnalysisStats
        val verdict = when {
            stats.malicious > 0 -> "${stats.malicious}/${stats.total} engines flagged it as malicious"
            stats.suspicious > 0 -> "${stats.suspicious}/${stats.total} engines flagged it as suspicious"
            else -> "No engine reported a threat"
        }
        return "${state.label}: $verdict. Tap to view the full report."
    }

    private fun finish() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { scope.cancel() }
    }
}

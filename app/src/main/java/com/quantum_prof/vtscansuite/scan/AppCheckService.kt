// scan/AppCheckService.kt
package com.quantum_prof.vtscansuite.scan

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält den Prozess während eines Sammellaufs am Leben.
 *
 * Ein Durchlauf über hundert Apps dauert bei vier Anfragen pro Minute knapp eine halbe
 * Stunde – ohne Vordergrunddienst wäre er vorbei, sobald der Nutzer die App verlässt.
 * Gestartet wird der Dienst nur durch eine Nutzeraktion, nie von selbst.
 *
 * Das ist keine Garantie: Android kann den Prozess auch mit Vordergrunddienst beenden
 * (etwa bei Speicherdruck oder wenn der Nutzer die App stoppt). Jedes Ergebnis wird sofort
 * gespeichert, deshalb geht dabei nichts verloren, nur der Lauf endet.
 */
@AndroidEntryPoint
class AppCheckService : Service() {

    @Inject
    lateinit var appCheckManager: AppCheckManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ScanNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            appCheckManager.cancel()
            finish()
            return START_NOT_STICKY
        }

        val initial = appCheckManager.state.value as? BulkCheckState.Running
        startInForeground(initial)

        // Ein zweiter Start darf keinen zweiten Beobachter anlegen.
        observer?.cancel()
        observer = scope.launch {
            appCheckManager.state.collect { state ->
                when (state) {
                    is BulkCheckState.Running -> update(state)
                    is BulkCheckState.Finished -> {
                        // Den Lauf hat der Nutzer abgebrochen: er weiß es, keine Meldung.
                        if (state.reason != StopReason.CANCELLED) {
                            ScanNotifications.showBulkResult(this@AppCheckService, state)
                        }
                        finish()
                    }

                    BulkCheckState.Idle -> finish()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(state: BulkCheckState.Running?) {
        ServiceCompat.startForeground(
            this,
            ScanNotifications.APP_CHECK_ID,
            ScanNotifications.buildAppCheck(this, state, stopIntent(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun update(state: BulkCheckState.Running) {
        try {
            NotificationManagerCompat.from(this).notify(
                ScanNotifications.APP_CHECK_ID,
                ScanNotifications.buildAppCheck(this, state, stopIntent(this))
            )
        } catch (_: SecurityException) {
            // Der Nutzer hat Benachrichtigungen verweigert. Der Lauf geht trotzdem weiter,
            // er ist dann nur nicht mehr von außen zu sehen.
        }
    }

    private fun finish() {
        observer?.cancel()
        observer = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { scope.cancel() }
    }

    companion object {
        private const val ACTION_STOP = "com.quantum_prof.vtscansuite.STOP_APP_CHECK"

        private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, AppCheckService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

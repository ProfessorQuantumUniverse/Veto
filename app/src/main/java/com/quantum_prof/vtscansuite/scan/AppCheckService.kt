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
 */
@AndroidEntryPoint
class AppCheckService : Service() {

    @Inject
    lateinit var appCheckManager: AppCheckManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        startInForeground(initial?.done ?: 0, initial?.total ?: 0, initial?.currentApp.orEmpty())

        scope.launch {
            appCheckManager.state.collect { state ->
                when (state) {
                    is BulkCheckState.Running -> update(state)
                    // Das Ergebnis zeigt der Bildschirm; hier reicht es, sich zu beenden.
                    else -> finish()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(done: Int, total: Int, currentApp: String) {
        ServiceCompat.startForeground(
            this,
            ScanNotifications.APP_CHECK_ID,
            ScanNotifications.buildAppCheck(this, done, total, currentApp, stopIntent(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun update(state: BulkCheckState.Running) {
        try {
            NotificationManagerCompat.from(this).notify(
                ScanNotifications.APP_CHECK_ID,
                ScanNotifications.buildAppCheck(
                    this, state.done, state.total, state.currentApp, stopIntent(this)
                )
            )
        } catch (_: SecurityException) {
            // Der Nutzer hat Benachrichtigungen verweigert. Der Lauf geht trotzdem weiter,
            // er ist dann nur nicht mehr von außen zu sehen.
        }
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

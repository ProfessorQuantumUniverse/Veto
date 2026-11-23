// scan/UploadService.kt
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
 * Hält den Prozess während eines Upload-Stapels am Leben. Gestartet wird der Dienst nur,
 * nachdem der Nutzer den Stapel bestätigt hat. Android garantiert nicht, dass der Prozess
 * trotzdem bis zum Ende lebt; die Warteschlange liegt nur im Speicher.
 */
@AndroidEntryPoint
class UploadService : Service() {

    @Inject
    lateinit var uploadQueueManager: UploadQueueManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ScanNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            uploadQueueManager.cancel()
            finish()
            return START_NOT_STICKY
        }

        val initial = uploadQueueManager.state.value as? UploadQueueState.Running
        startInForeground(initial)

        // Ein zweiter Start darf keinen zweiten Beobachter anlegen.
        observer?.cancel()
        observer = scope.launch {
            uploadQueueManager.state.collect { state ->
                when (state) {
                    is UploadQueueState.Running -> update(state)
                    is UploadQueueState.Finished -> {
                        // Den Lauf hat der Nutzer abgebrochen: er weiß es, keine Meldung.
                        if (state.reason != UploadStopReason.CANCELLED) {
                            ScanNotifications.showUploadResult(this@UploadService, state)
                        }
                        finish()
                    }

                    UploadQueueState.Idle -> finish()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(state: UploadQueueState.Running?) {
        ServiceCompat.startForeground(
            this,
            ScanNotifications.UPLOAD_ID,
            ScanNotifications.buildUpload(this, state, stopIntent(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun update(state: UploadQueueState.Running) {
        try {
            NotificationManagerCompat.from(this).notify(
                ScanNotifications.UPLOAD_ID,
                ScanNotifications.buildUpload(this, state, stopIntent(this))
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
        private const val ACTION_STOP = "com.quantum_prof.vtscansuite.STOP_UPLOAD"

        private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, UploadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

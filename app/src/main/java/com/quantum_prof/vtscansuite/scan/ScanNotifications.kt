// scan/ScanNotifications.kt
package com.quantum_prof.vtscansuite.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quantum_prof.vtscansuite.MainActivity
import com.quantum_prof.vtscansuite.R
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase

object ScanNotifications {
    const val CHANNEL_PROGRESS = "scan_progress"
    const val CHANNEL_RESULT = "scan_result"
    const val ONGOING_ID = 1001
    const val RESULT_ID = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Scan progress", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows an active VirusTotal scan." }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Scan results", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Notifies you when a scan is finished." }
        )
    }

    fun buildOngoing(context: Context, phase: ScanPhase?, progress: Float?, label: String): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_scan_notification)
            .setContentTitle(phaseTitle(phase))
            .setContentText(if (label.isBlank()) "VirusTotal scan in progress" else label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, requestCode = 0))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (progress != null) {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun showResult(context: Context, title: String, text: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_scan_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context, requestCode = 1))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RESULT_ID, notif) }
    }

    fun cancelResult(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(RESULT_ID) }
    }

    private fun phaseTitle(phase: ScanPhase?): String = when (phase) {
        ScanPhase.UPLOADING -> "Uploading file…"
        ScanPhase.SUBMITTING -> "Submitting URL…"
        ScanPhase.ANALYZING -> "Analyzing with 70+ engines…"
        ScanPhase.FETCHING -> "Fetching report…"
        else -> "Scanning…"
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

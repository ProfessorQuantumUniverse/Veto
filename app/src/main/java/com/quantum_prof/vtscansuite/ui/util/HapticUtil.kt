// ui/util/HapticUtil.kt
package com.quantum_prof.vtscansuite.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Universelle Hilfsklasse für differenziertes Haptic-Feedback ab Android 10+.
 * Sehr leichtgewichtig (kein Vibrator-Service), daher ideal für häufige Taps.
 */
fun triggerHaptic(view: View, type: HapticType) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (type) {
            HapticType.SUCCESS -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            HapticType.WARNING -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            HapticType.CLICK -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/**
 * Deutliches, unterscheidbares Abschluss-Feedback nach einem Scan.
 * Erfolg: zwei kurze, sanfte Pulse. Fehler: drei längere, kräftigere Pulse.
 */
fun triggerScanComplete(context: Context, success: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    // timings: warte/vibriere abwechselnd (ms)
    val timings = if (success) longArrayOf(0, 28, 55, 28) else longArrayOf(0, 55, 45, 55, 45, 95)
    runCatching { vibrator.vibrate(VibrationEffect.createWaveform(timings, -1)) }
}

enum class HapticType {
    SUCCESS, WARNING, CLICK
}
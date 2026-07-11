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
 * Universelle Hilfsklasse für differenziertes Haptic-Feedback.
 *
 * Wichtig für Samsung/One UI: die früher genutzten Konstanten KEYBOARD_TAP/VIRTUAL_KEY
 * hängen an der Systemeinstellung „Tastatur-Vibration" (standardmäßig AUS) und lösen dort
 * gar nichts aus. Deshalb nutzen wir semantisch starke, geräteübergreifend zuverlässig
 * gerenderte Konstanten (CONTEXT_CLICK/CONFIRM/REJECT) plus FLAG_IGNORE_VIEW_SETTING und
 * fallen – falls die View-Haptik nichts ausführt – auf einen vordefinierten Vibrator-Effekt
 * zurück. So ist der Tap auf jedem Gerät spürbar.
 */
fun triggerHaptic(view: View, type: HapticType) {
    val constant = when (type) {
        HapticType.SUCCESS -> HapticFeedbackConstants.CONFIRM
        HapticType.WARNING -> HapticFeedbackConstants.REJECT
        HapticType.CLICK -> HapticFeedbackConstants.CONTEXT_CLICK
    }

    // FLAG_IGNORE_VIEW_SETTING: auch feuern, wenn die View-eigene Haptik unterdrückt wäre.
    val performed = view.performHapticFeedback(
        constant,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )

    // Fallback für Geräte (z. B. Samsung One UI), die diese Konstanten ignorieren.
    if (!performed) {
        vibratePredefined(view.context, type)
    }
}

private fun vibratePredefined(context: Context, type: HapticType) {
    val vibrator = resolveVibrator(context) ?: return
    if (!vibrator.hasVibrator()) return

    val effectId = when (type) {
        HapticType.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
        HapticType.WARNING -> VibrationEffect.EFFECT_HEAVY_CLICK
        HapticType.CLICK -> VibrationEffect.EFFECT_TICK
    }
    runCatching { vibrator.vibrate(VibrationEffect.createPredefined(effectId)) }
}

/**
 * Deutliches, unterscheidbares Abschluss-Feedback nach einem Scan.
 * Erfolg: zwei kurze, sanfte Pulse. Fehler: drei längere, kräftigere Pulse.
 */
fun triggerScanComplete(context: Context, success: Boolean) {
    val vibrator = resolveVibrator(context) ?: return
    if (!vibrator.hasVibrator()) return

    // timings: warte/vibriere abwechselnd (ms)
    val timings = if (success) longArrayOf(0, 28, 55, 28) else longArrayOf(0, 55, 45, 55, 45, 95)
    runCatching { vibrator.vibrate(VibrationEffect.createWaveform(timings, -1)) }
}

private fun resolveVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

enum class HapticType {
    SUCCESS, WARNING, CLICK
}

// ui/util/HapticUtil.kt
package com.quantum_prof.vtscansuite.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Universelle Hilfsklasse für differenziertes Haptic-Feedback ab Android 10+.
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

enum class HapticType {
    SUCCESS, WARNING, CLICK
}
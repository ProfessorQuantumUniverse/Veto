// ui/theme/Motion.kt
package com.quantum_prof.vtscansuite.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith

/**
 * Material 3 Motion-Tokens: emphasized/standard Easing-Kurven und Standard-Dauern.
 * Quelle: m3.material.io/styles/motion.
 */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val DurationShort = 200
    const val DurationMedium = 350
    const val DurationLong = 500
}

/**
 * Material 3 "Fade Through" – für den Wechsel zwischen unabhängigen Inhalten
 * (z. B. Top-Level-Zielen). Ausgehender Inhalt blendet schnell aus, neuer Inhalt
 * blendet verzögert ein und skaliert dezent von 92 % hoch.
 */
fun fadeThrough(): ContentTransform =
    (fadeIn(tween(Motion.DurationMedium, delayMillis = 90, easing = Motion.Standard)) +
        scaleIn(tween(Motion.DurationMedium, delayMillis = 90, easing = Motion.EmphasizedDecelerate), initialScale = 0.92f))
        .togetherWith(fadeOut(tween(90, easing = Motion.Standard)))

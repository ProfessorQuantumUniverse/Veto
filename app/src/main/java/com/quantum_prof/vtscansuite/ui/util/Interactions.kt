// ui/util/Interactions.kt
package com.quantum_prof.vtscansuite.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Animierter Druck-Maßstab (Material-„squish"): liefert einen Skalierungswert,
 * der beim Drücken sanft einfedert. Für die [interactionSource] eines klickbaren
 * Composables (z. B. `Card(onClick=…, interactionSource=…)`).
 */
@Composable
fun pressScale(
    interactionSource: MutableInteractionSource,
    scaleDown: Float = 0.97f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return scale
}

/** Bequemer Modifier für Skalierung anhand des [pressScale]-Werts. */
fun Modifier.scaleOnPress(scale: Float): Modifier =
    graphicsLayer { scaleX = scale; scaleY = scale }

/**
 * Vollständiges Touch-Feedback in einem Modifier: M3-Ripple (Indication),
 * einfederndes Drücken UND haptisches Feedback. Für Boxen/Surfaces, die zuvor
 * mit `.clip(shape)` beschnitten wurden, damit der Ripple der Form folgt.
 */
@Composable
fun Modifier.bounceClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: HapticType = HapticType.CLICK,
    scaleDown: Float = 0.97f
): Modifier {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource, scaleDown)
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled
        ) {
            triggerHaptic(view, haptic)
            onClick()
        }
}

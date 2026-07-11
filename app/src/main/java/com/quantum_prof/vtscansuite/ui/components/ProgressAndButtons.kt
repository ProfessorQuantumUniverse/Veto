// ui/components/ProgressAndButtons.kt
package com.quantum_prof.vtscansuite.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantum_prof.vtscansuite.ui.theme.auroraBrush
import com.quantum_prof.vtscansuite.ui.util.HapticType
import com.quantum_prof.vtscansuite.ui.util.triggerHaptic

// ============================================================================
//  EXPRESSIVE PROGRESS BAR – abgerundet, mit Aurora-Verlauf.
//  progress == null  →  unbestimmte, gleitende Animation.
// ============================================================================
@Composable
fun ExpressiveProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    brush: Brush = auroraBrush(),
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: androidx.compose.ui.unit.Dp = 12.dp
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { containerWidthPx = it.width }
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        if (progress != null) {
            // Sanftes, gleichmäßiges Nachführen ohne Federn/Überschwingen
            val animated by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 320,
                    easing = FastOutSlowInEasing
                ),
                label = "progress"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(brush)
            )
        } else if (containerWidthPx > 0) {
            val fullWidth = with(density) { containerWidthPx.toDp() }
            val segment = fullWidth * 0.42f
            val transition = rememberInfiniteTransition(label = "indeterminate")
            val t by transition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    androidx.compose.animation.core.tween(1150, easing = FastOutSlowInEasing)
                ),
                label = "slide"
            )
            val x = (fullWidth + segment) * t - segment
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .width(segment)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(brush)
            )
        }
    }
}

// ============================================================================
//  GRADIENT BUTTON – auffälliger Aurora-CTA mit Press-Animation.
// ============================================================================
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    brush: Brush = auroraBrush(),
    contentColor: Color = Color.White
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ctaPress"
    )

    val shape = RoundedCornerShape(50)
    val bg: Modifier = if (enabled) {
        Modifier.background(brush, shape)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape)
    }
    val effectiveContent = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = LocalIndication.current
            ) {
                triggerHaptic(view, HapticType.CLICK)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = effectiveContent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = effectiveContent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

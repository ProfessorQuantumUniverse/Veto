// ui/dashboard/components/ScoreGaugeChart.kt
package com.quantum_prof.vtscansuite.ui.dashboard.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScoreGaugeChart(
    maliciousCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val targetSweepAngle = if (totalCount > 0) {
        (maliciousCount.toFloat() / totalCount.toFloat()) * 360f
    } else {
        0f
    }

    val animatedSweepAngle = remember { Animatable(0f) }
    val colorScheme = MaterialTheme.colorScheme
    val isSafe = maliciousCount == 0

    // Bouncy Spring-Animation
    LaunchedEffect(targetSweepAngle) {
        animatedSweepAngle.animateTo(
            targetValue = targetSweepAngle,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            // Hintergrundring
            drawCircle(
                color = colorScheme.surfaceVariant,
                radius = size.minDimension / 2,
                style = Stroke(width = 16.dp.toPx())
            )

            // Aktiver Fortschrittsring
            drawArc(
                color = if (isSafe) colorScheme.primary else colorScheme.error,
                startAngle = -90f,
                sweepAngle = if (isSafe) 360f else animatedSweepAngle.value.coerceAtLeast(10f),
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSafe) "Safe" else "$maliciousCount",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSafe) colorScheme.primary else colorScheme.error
            )
            if (!isSafe) {
                Text(
                    text = "Scanner-Meldungen",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
// ui/theme/Shapes.kt
package com.quantum_prof.vtscansuite.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Eine weiche, gewellte "Cookie"-/Siegel-Form – charakteristisch für Material 3 Expressive.
 * Erzeugt über eine Polar-Funktion r(θ) = R · (1 + amp · cos(n·θ)).
 *
 * @param scallops Anzahl der Wellen entlang des Randes.
 * @param amplitude Stärke der Wellung (0f = perfekter Kreis).
 */
class CookieShape(
    private val scallops: Int = 12,
    private val amplitude: Float = 0.06f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Basisradius so wählen, dass die Wellenspitzen exakt in die Bounds passen.
        val baseR = min(cx, cy) / (1f + amplitude)
        val steps = 720
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * PI.toFloat()
            val r = baseR * (1f + amplitude * cos(scallops * t))
            val x = cx + r * cos(t)
            val y = cy + r * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

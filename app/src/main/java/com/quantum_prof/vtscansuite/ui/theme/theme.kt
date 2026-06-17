// ui/theme/Theme.kt
package com.quantum_prof.vtscansuite.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ============================================================================
//  EXPRESSIVE SHAPES – auffällig runde, weiche Formen
// ============================================================================
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

// ============================================================================
//  EXPRESSIVE TYPOGRAPHY – kräftige, selbstbewusste Schriftgewichte
// ============================================================================
private val baseTypography = Typography()
private val ExpressiveTypography = baseTypography.copy(
    displayLarge = baseTypography.displayLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = (-1).sp),
    displayMedium = baseTypography.displayMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = (-0.5).sp),
    displaySmall = baseTypography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
    headlineLarge = baseTypography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    labelMedium = baseTypography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.8.sp)
)

// ============================================================================
//  SEMANTISCHE FARBROLLEN (Verdikt) – via CompositionLocal überall verfügbar
// ============================================================================
@Immutable
data class ExpressiveColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

private val LightExpressiveColors = ExpressiveColors(
    safe = SafeLight,
    safeContainer = SafeContainerLight,
    onSafeContainer = OnSafeContainerLight,
    danger = DangerLight,
    dangerContainer = DangerContainerLight,
    onDangerContainer = OnDangerContainerLight,
    warning = WarningLight,
    warningContainer = WarningContainerLight,
    onWarningContainer = OnWarningContainerLight
)

private val DarkExpressiveColors = ExpressiveColors(
    safe = SafeDark,
    safeContainer = SafeContainerDark,
    onSafeContainer = OnSafeContainerDark,
    danger = DangerDark,
    dangerContainer = DangerContainerDark,
    onDangerContainer = OnDangerContainerDark,
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = OnWarningContainerDark
)

val LocalExpressiveColors = staticCompositionLocalOf { DarkExpressiveColors }

/** Komfort-Zugriff: `MaterialTheme.expressive.safe` usw. */
val MaterialTheme.expressive: ExpressiveColors
    @Composable
    get() = LocalExpressiveColors.current

// ============================================================================
//  COLOR SCHEMES
// ============================================================================
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    surfaceTint = PrimaryDark,
    scrim = ScrimDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
    surfaceTint = PrimaryLight,
    scrim = ScrimLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

@Composable
fun VTExpressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Material You
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val expressiveColors = if (darkTheme) DarkExpressiveColors else LightExpressiveColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExpressiveColors provides expressiveColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content
        )
    }
}

// ============================================================================
//  GRADIENT-HELFER ("Aurora") – adaptiert sich an Material You
// ============================================================================

/** Lebendiger, mehrstufiger Akzent-Verlauf aus den Theme-Akzentfarben. */
@Composable
fun auroraBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.linearGradient(
        colors = listOf(cs.primary, cs.tertiary, cs.secondary)
    )
}

/** Weicher Verlauf für sichere Ergebnisse. */
@Composable
fun safeBrush(): Brush {
    val e = MaterialTheme.expressive
    return Brush.linearGradient(colors = listOf(e.safe, e.safeContainer))
}

/** Auffälliger Verlauf für gefährliche Ergebnisse. */
@Composable
fun dangerBrush(): Brush {
    val e = MaterialTheme.expressive
    return Brush.linearGradient(colors = listOf(e.danger, MaterialTheme.colorScheme.error))
}

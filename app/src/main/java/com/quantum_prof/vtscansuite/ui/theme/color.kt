// ui/theme/Color.kt
package com.quantum_prof.vtscansuite.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
//  EXPRESSIVE FALLBACK PALETTE.
//  Wird verwendet, wenn Material You (Dynamic Color) nicht verfügbar ist.
//  Lebendiges Indigo · Cyan · Magenta – kräftig und "expressive".
// ============================================================================

// ---- LIGHT ----
val PrimaryLight = Color(0xFF4D54D8)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE1E0FF)
val OnPrimaryContainerLight = Color(0xFF050792)

val SecondaryLight = Color(0xFF00697A)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFA7EDFF)
val OnSecondaryContainerLight = Color(0xFF001F26)

val TertiaryLight = Color(0xFF9B2F87)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD7EE)
val OnTertiaryContainerLight = Color(0xFF3B0033)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFBF8FF)
val OnBackgroundLight = Color(0xFF1A1B22)
val SurfaceLight = Color(0xFFFBF8FF)
val OnSurfaceLight = Color(0xFF1A1B22)
val SurfaceVariantLight = Color(0xFFE3E1EC)
val OnSurfaceVariantLight = Color(0xFF46464F)
val OutlineLight = Color(0xFF777680)
val OutlineVariantLight = Color(0xFFC7C5D0)

// M3 Tonal Surfaces (Light)
val SurfaceDimLight = Color(0xFFDBD9E0)
val SurfaceBrightLight = Color(0xFFFBF8FF)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF5F2FA)
val SurfaceContainerLight = Color(0xFFF0EDF7)
val SurfaceContainerHighLight = Color(0xFFEAE7F1)
val SurfaceContainerHighestLight = Color(0xFFE4E1EB)
val InverseSurfaceLight = Color(0xFF2F3036)
val InverseOnSurfaceLight = Color(0xFFF2EFF7)
val InversePrimaryLight = Color(0xFFC0C1FF)
val ScrimLight = Color(0xFF000000)

// ---- DARK ----
val PrimaryDark = Color(0xFFC0C1FF)
val OnPrimaryDark = Color(0xFF13169E)
val PrimaryContainerDark = Color(0xFF3338BE)
val OnPrimaryContainerDark = Color(0xFFE1E0FF)

val SecondaryDark = Color(0xFF84D2E5)
val OnSecondaryDark = Color(0xFF003640)
val SecondaryContainerDark = Color(0xFF004E5B)
val OnSecondaryContainerDark = Color(0xFFA7EDFF)

val TertiaryDark = Color(0xFFFFACE4)
val OnTertiaryDark = Color(0xFF5E0052)
val TertiaryContainerDark = Color(0xFF7C156C)
val OnTertiaryContainerDark = Color(0xFFFFD7EE)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF101218)
val OnBackgroundDark = Color(0xFFE4E1E9)
val SurfaceDark = Color(0xFF101218)
val OnSurfaceDark = Color(0xFFE4E1E9)
val SurfaceVariantDark = Color(0xFF46464F)
val OnSurfaceVariantDark = Color(0xFFC7C5D0)
val OutlineDark = Color(0xFF918F9A)
val OutlineVariantDark = Color(0xFF46464F)

// M3 Tonal Surfaces (Dark)
val SurfaceDimDark = Color(0xFF101218)
val SurfaceBrightDark = Color(0xFF36383F)
val SurfaceContainerLowestDark = Color(0xFF0B0D12)
val SurfaceContainerLowDark = Color(0xFF18191F)
val SurfaceContainerDark = Color(0xFF1D1F26)
val SurfaceContainerHighDark = Color(0xFF272930)
val SurfaceContainerHighestDark = Color(0xFF32343B)
val InverseSurfaceDark = Color(0xFFE4E1E9)
val InverseOnSurfaceDark = Color(0xFF2F3036)
val InversePrimaryDark = Color(0xFF4D54D8)
val ScrimDark = Color(0xFF000000)

// ============================================================================
//  SEMANTISCHE VERDIKT-FARBEN.
//  Unabhängig vom dynamischen Theme, damit "sicher" immer grün und
//  "schädlich" immer rot wirkt – egal welche Akzentfarbe das System vorgibt.
// ============================================================================

// SAFE (grün)
val SafeLight = Color(0xFF1E7B43)
val SafeContainerLight = Color(0xFFA6F4C0)
val OnSafeContainerLight = Color(0xFF00210F)
val SafeDark = Color(0xFF77DD9B)
val SafeContainerDark = Color(0xFF0C5128)
val OnSafeContainerDark = Color(0xFF93F8B5)

// DANGER (rot)
val DangerLight = Color(0xFFC0143C)
val DangerContainerLight = Color(0xFFFFD9DD)
val OnDangerContainerLight = Color(0xFF40000C)
val DangerDark = Color(0xFFFFB2BB)
val DangerContainerDark = Color(0xFF8E1233)
val OnDangerContainerDark = Color(0xFFFFD9DD)

// WARNING (bernstein)
val WarningLight = Color(0xFF855300)
val WarningContainerLight = Color(0xFFFFDDB3)
val OnWarningContainerLight = Color(0xFF2A1700)
val WarningDark = Color(0xFFFFB951)
val WarningContainerDark = Color(0xFF633F00)
val OnWarningContainerDark = Color(0xFFFFDDB3)

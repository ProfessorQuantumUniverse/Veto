// ui/util/Format.kt
package com.quantum_prof.vtscansuite.ui.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Formatiert eine Bytegröße menschenlesbar (z. B. 12.4 MB). */
fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "–"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.GERMANY, "%.1f %s", value, units[digitGroups])
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm", Locale.GERMANY)

/** Formatiert einen Unix-Timestamp (Sekunden) als lesbares Datum. */
fun formatEpochSeconds(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "–"
    return try {
        Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(dateFormatter)
    } catch (e: Exception) {
        "–"
    }
}

/** Liest einen JsonElement-Wert defensiv als Anzeige-String. */
fun JsonElement?.asDisplayString(): String? {
    if (this == null) return null
    return (this as? JsonPrimitive)?.content
}

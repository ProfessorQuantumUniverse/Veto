// data/model/AppScanRecord.kt
package com.quantum_prof.vtscansuite.data.model

import kotlinx.serialization.Serializable

/** Ergebnis eines Hash-Lookups für eine installierte App. */
enum class AppScanOutcome {
    /** VirusTotal hat einen Bericht zu diesem Hash. */
    KNOWN,

    /** VirusTotal kennt den Hash nachweislich nicht (HTTP 404). */
    UNKNOWN
}

/**
 * Gespeichertes Ergebnis einer Hash-Prüfung. Fehlgeschlagene Abfragen werden
 * bewusst **nicht** gespeichert: ein Rate-Limit ist kein Ergebnis.
 *
 * [versionCode] und [apkSize] entscheiden, ob der Eintrag noch zur installierten
 * App passt. Ändert sich eines von beidem, gilt er als veraltet und wird neu geprüft.
 */
@Serializable
data class AppScanRecord(
    val packageName: String,
    val sha256: String,
    val versionCode: Long,
    val apkSize: Long,
    val checkedAt: Long,
    val outcome: AppScanOutcome,
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val total: Int = 0
) {
    /** Wie viele Engines etwas gemeldet haben. */
    val flagged: Int get() = malicious + suspicious

    /** Passt der Eintrag noch zur aktuell installierten Fassung? */
    fun matches(versionCode: Long, apkSize: Long): Boolean =
        this.versionCode == versionCode && this.apkSize == apkSize
}

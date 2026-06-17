// data/model/SavedScan.kt
package com.quantum_prof.vtscansuite.data.model

import kotlinx.serialization.Serializable

/**
 * Ein gespeicherter Scan: hält den vollständigen Bericht (für die Offline-Anzeige)
 * plus etwas Metadaten für die Listen-Darstellung. Beim erneuten Öffnen wird der
 * Bericht über [id]/[isUrl] frisch nachgeladen.
 */
@Serializable
data class SavedScan(
    val id: String,
    val isUrl: Boolean,
    val label: String,
    val savedAt: Long,
    val report: FileReportResponse
)

/**
 * Baut einen [SavedScan]-Snapshot aus einem Bericht.
 *
 * [labelOverride] (z. B. der lokale App-Name) hat Vorrang, weil VirusTotal für APKs
 * oft nur den generischen Dateinamen "base.apk" liefert.
 */
fun savedScanFrom(
    report: FileReportResponse,
    savedAt: Long = System.currentTimeMillis(),
    labelOverride: String? = null
): SavedScan {
    val attr = report.data.attributes
    val isUrl = report.data.type == "url" || attr.url != null

    // Generische APK-Dateinamen ignorieren, falls kein expliziter Name übergeben wurde
    val meaningful = attr.meaningfulName
        ?.takeIf { it.isNotBlank() && !it.equals("base.apk", ignoreCase = true) }

    val label = labelOverride?.takeIf { it.isNotBlank() }
        ?: meaningful
        ?: attr.url?.takeIf { it.isNotBlank() }
        ?: attr.androguard?.packageName?.takeIf { it.isNotBlank() }
        ?: attr.names.firstOrNull { it.isNotBlank() && !it.equals("base.apk", ignoreCase = true) }
        ?: attr.sha256
        ?: report.data.id

    return SavedScan(
        id = report.data.id,
        isUrl = isUrl,
        label = label,
        savedAt = savedAt,
        report = report
    )
}

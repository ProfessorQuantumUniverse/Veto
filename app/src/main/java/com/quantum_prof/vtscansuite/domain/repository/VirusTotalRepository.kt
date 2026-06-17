// domain/repository/VirusTotalRepository.kt
package com.quantum_prof.vtscansuite.domain.repository

import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import java.io.File

/** Phasen eines Scan-Vorgangs – treiben die Fortschrittsanzeige in der UI. */
enum class ScanPhase {
    CHECKING,    // Bekannten Hash/URL abfragen
    UPLOADING,   // Datei wird hochgeladen (determinierter Fortschritt)
    SUBMITTING,  // URL wird eingereicht
    ANALYZING,   // VirusTotal analysiert
    FETCHING     // Endgültigen Bericht abrufen
}

/** progress == null bedeutet "unbestimmt". */
typealias ProgressCallback = (phase: ScanPhase, progress: Float?) -> Unit

interface VirusTotalRepository {
    suspend fun getFileReport(apiKey: String, hash: String): Result<FileReportResponse>

    /** Lädt einen vorhandenen Bericht (Datei oder URL) anhand seiner ID neu. */
    suspend fun getReport(apiKey: String, id: String, isUrl: Boolean): Result<FileReportResponse>

    suspend fun uploadFile(
        apiKey: String,
        file: File,
        onProgress: ProgressCallback = { _, _ -> }
    ): Result<FileReportResponse>

    suspend fun scanUrl(
        apiKey: String,
        url: String,
        onProgress: ProgressCallback = { _, _ -> }
    ): Result<FileReportResponse>
}

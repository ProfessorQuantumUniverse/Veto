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

    /**
     * [knownHash] spart das zweite vollständige Lesen der Datei: der Aufrufer hat den
     * SHA-256 für den Lookup meist schon berechnet.
     */
    suspend fun uploadFile(
        apiKey: String,
        file: File,
        knownHash: String? = null,
        onProgress: ProgressCallback = { _, _ -> }
    ): Result<FileReportResponse>

    /**
     * Reicht die Datei nur ein: ein Upload, **keine** Statusabfragen und kein Bericht. Liefert
     * die Analyse-ID. Der Bericht kommt später über die normale Hash-Prüfung.
     *
     * Scheitert der Upload, bevor ein Byte gesendet wurde, steht im Fehler eine
     * [com.quantum_prof.vtscansuite.data.remote.UploadNotStartedException]; nur dann ist
     * sicher, dass nichts angekommen ist.
     */
    suspend fun submitFile(
        apiKey: String,
        file: File,
        onProgress: ProgressCallback = { _, _ -> }
    ): Result<String>

    suspend fun scanUrl(
        apiKey: String,
        url: String,
        onProgress: ProgressCallback = { _, _ -> }
    ): Result<FileReportResponse>
}

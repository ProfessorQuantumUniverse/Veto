// app/src/main/java/com/quantum_prof/vtscansuite/data/repository/VirusTotalRepositoryImpl.kt
package com.quantum_prof.vtscansuite.data.repository

import android.util.Base64
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.remote.PollSchedule
import com.quantum_prof.vtscansuite.data.remote.ProgressRequestBody
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.remote.VTScanApiService
import com.quantum_prof.vtscansuite.data.remote.VtApiKeyGuard
import com.quantum_prof.vtscansuite.data.remote.VtHttpException
import com.quantum_prof.vtscansuite.domain.repository.ProgressCallback
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

class VirusTotalRepositoryImpl @Inject constructor(
    private val api: VTScanApiService
) : VirusTotalRepository {

    private val sizeLimit32MB = 32 * 1024 * 1024 // 32 Megabytes
    private val analysisTimeoutMs = 6 * 60_000L     // bis zu 6 Minuten auf den Abschluss warten

    // Statusabfragen beginnen im Free-Tier-Takt und werden dann seltener (siehe PollSchedule).
    private val analysisPollInitialMs = 15_000L
    private val analysisPollMaxMs = 75_000L

    // Danach den Report nachladen, bis die Engine-Ergebnisse tatsächlich da sind.
    private val populateRetries = 5
    private val populateInitialMs = 5_000L
    private val populateMaxMs = 30_000L

    override suspend fun getFileReport(apiKey: String, hash: String): Result<FileReportResponse> {
        return try {
            val response = api.getFileReport(apiKey, hash)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(httpError(response.code(), "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReport(apiKey: String, id: String, isUrl: Boolean): Result<FileReportResponse> {
        return try {
            val response = if (isUrl) api.getUrlReport(apiKey, id) else api.getFileReport(apiKey, id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(httpError(response.code(), "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(
        apiKey: String,
        file: File,
        knownHash: String?,
        onProgress: ProgressCallback
    ): Result<FileReportResponse> {
        return try {
            // Der Aufrufer hat die Datei für den Lookup schon gehasht – eine große APK ein
            // zweites Mal komplett zu lesen, wäre reine Verschwendung.
            val fileHash = knownHash ?: calculateSha256(file)

            onProgress(ScanPhase.UPLOADING, 0f)
            val rawBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val progressBody = ProgressRequestBody(rawBody) { fraction ->
                onProgress(ScanPhase.UPLOADING, fraction)
            }
            val body = MultipartBody.Part.createFormData("file", file.name, progressBody)

            // 1. Datei hochladen
            val uploadResponse = if (file.length() <= sizeLimit32MB) {
                api.uploadFile(apiKey, body)
            } else {
                val urlResponse = api.getUploadUrl(apiKey)
                if (!urlResponse.isSuccessful || urlResponse.body() == null) {
                    return Result.failure(httpError(urlResponse.code(), "Could not obtain an upload URL"))
                }
                val uploadUrl = urlResponse.body()!!.data
                // Die Ziel-URL kommt aus einer API-Antwort. Vor dem ersten Byte prüfen, dass
                // sie wirklich zu VirusTotal gehört – sonst ginge der API-Schlüssel mit.
                if (!VtApiKeyGuard.isVirusTotalHttps(uploadUrl)) {
                    return Result.failure(
                        Exception("VirusTotal returned an upload address outside virustotal.com. Nothing was uploaded.")
                    )
                }
                api.uploadFileToUrl(uploadUrl, apiKey, body)
            }

            if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                val analysisId = uploadResponse.body()!!.data.id

                // 2. Warten, bis die Analyse abgeschlossen ist
                awaitAnalysisCompletion(apiKey, analysisId, onProgress)

                // 3. Datei-Report nachladen, bis die Engine-Ergebnisse tatsächlich vorhanden sind
                onProgress(ScanPhase.FETCHING, null)
                fetchPopulatedReport(apiKey, fileHash, isUrl = false, onProgress)
            } else {
                Result.failure(httpError(uploadResponse.code(), "Upload error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun scanUrl(
        apiKey: String,
        url: String,
        onProgress: ProgressCallback
    ): Result<FileReportResponse> {
        return try {
            val normalized = normalizeUrl(url)
            val precomputedId = vtUrlId(normalized)

            // 1. Schnellpfad: bereits bekannte (und analysierte) URL direkt abrufen
            onProgress(ScanPhase.CHECKING, null)
            val cached = runCatching { api.getUrlReport(apiKey, precomputedId) }.getOrNull()
            if (cached?.isSuccessful == true && cached.body().isPopulated()) {
                return Result.success(cached.body()!!)
            }

            // 2. URL zur Analyse einreichen
            onProgress(ScanPhase.SUBMITTING, null)
            val submit = api.submitUrl(apiKey, normalized)
            if (!submit.isSuccessful || submit.body() == null) {
                return Result.failure(httpError(submit.code(), "Could not submit the URL"))
            }
            val analysisId = submit.body()!!.data.id

            // 3. Auf Abschluss warten und zuverlässige url_id einsammeln
            val urlInfoId = awaitAnalysisCompletion(apiKey, analysisId, onProgress)

            // 4. Endgültigen URL-Bericht nachladen, bis die Engine-Ergebnisse vorhanden sind
            onProgress(ScanPhase.FETCHING, null)
            val finalId = urlInfoId ?: precomputedId
            fetchPopulatedReport(apiKey, finalId, isUrl = true, onProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pollt /analyses/{id} bis der Status "completed" ist (oder das Zeitlimit erreicht ist).
     * Der Abstand zwischen den Abfragen wächst ([PollSchedule]), und bei einem Rate-Limit
     * wird zusätzlich so lange gewartet, wie VirusTotal es per "Retry-After" vorgibt.
     * Liefert (falls vorhanden) die url_info.id aus der Analyse-Antwort zurück.
     */
    private suspend fun awaitAnalysisCompletion(
        apiKey: String,
        analysisId: String,
        onProgress: ProgressCallback
    ): String? {
        val deadline = System.currentTimeMillis() + analysisTimeoutMs
        val schedule = PollSchedule(analysisPollInitialMs, analysisPollMaxMs)
        var urlInfoId: String? = null
        while (System.currentTimeMillis() < deadline) {
            onProgress(ScanPhase.ANALYZING, null)
            delay(schedule.nextDelayMs().milliseconds)
            val response = runCatching { api.getAnalysisReport(apiKey, analysisId) }.getOrNull()
                ?: continue
            if (response.code() == 429) {
                // Rate-Limit: nicht sofort erneut fragen.
                response.retryAfterMs()?.let { delay(it.milliseconds) }
                continue
            }
            val body = response.body()
            if (response.isSuccessful && body != null) {
                urlInfoId = body.meta?.urlInfo?.id ?: urlInfoId
                if (body.data.attributes.status == "completed") {
                    return urlInfoId
                }
            }
        }
        return urlInfoId
    }

    /** Wartezeit aus dem "Retry-After"-Header (nur das Sekunden-Format, das VirusTotal sendet). */
    private fun Response<*>.retryAfterMs(): Long? =
        headers()["Retry-After"]?.trim()?.toLongOrNull()
            ?.coerceIn(0, 120)
            ?.times(1_000)

    /**
     * Lädt den Report (Datei oder URL) wiederholt nach, bis Engine-Ergebnisse vorhanden sind.
     * Nötig, weil das Objekt direkt nach Abschluss der Analyse noch leer sein kann
     * (sonst landet man fälschlich im "Kein Urteil"-Zustand). Sind nach allen Versuchen
     * keine Ergebnisse da, wird ein eindeutiger Fehler statt eines leeren Berichts geliefert.
     */
    private suspend fun fetchPopulatedReport(
        apiKey: String,
        id: String,
        isUrl: Boolean,
        onProgress: ProgressCallback
    ): Result<FileReportResponse> {
        var last = getReport(apiKey, id, isUrl)
        val schedule = PollSchedule(populateInitialMs, populateMaxMs, factor = 1.8)
        var tries = 0
        while (tries < populateRetries) {
            if (last.getOrNull().isPopulated()) return last
            onProgress(ScanPhase.ANALYZING, null) // VirusTotal stellt das Ergebnis noch fertig
            delay(schedule.nextDelayMs().milliseconds)
            tries++
            last = getReport(apiKey, id, isUrl)
        }
        return if (last.getOrNull().isPopulated()) {
            last
        } else if (last.isSuccess) {
            Result.failure(Exception("VirusTotal is still analyzing this item. Please try again in a minute."))
        } else {
            last
        }
    }

    /** Ein Bericht gilt als befüllt, sobald Engine-Ergebnisse vorliegen. */
    private fun FileReportResponse?.isPopulated(): Boolean {
        val attrs = this?.data?.attributes ?: return false
        return attrs.lastAnalysisResults.isNotEmpty() || attrs.lastAnalysisStats.total > 0
    }

    /**
     * Bildet HTTP-Fehlercodes auf Exceptions ab – 429 wird zum spezifischen Rate-Limit-Hinweis.
     * Alles andere behält seinen Status-Code, damit die Aufrufer ein echtes "nicht gefunden"
     * (404) von einem Fehlschlag unterscheiden können.
     */
    private fun httpError(code: Int, context: String): Exception =
        if (code == 429) RateLimitException() else VtHttpException(code, "$context (code $code)")

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Ergänzt ein Schema, falls keines vorhanden ist (VirusTotal erwartet eine vollständige URL). */
    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }

    /** VirusTotal URL-Identifier: base64(url) url-safe, ohne Padding. */
    private fun vtUrlId(url: String): String =
        Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
}

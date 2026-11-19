// app/src/main/java/com/quantum_prof/vtscansuite/data/repository/VirusTotalRepositoryImpl.kt
package com.quantum_prof.vtscansuite.data.repository

import android.util.Base64
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.remote.BudgetExhaustedException
import com.quantum_prof.vtscansuite.data.remote.PollSchedule
import com.quantum_prof.vtscansuite.data.remote.ProgressRequestBody
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.remote.UploadNotStartedException
import com.quantum_prof.vtscansuite.data.remote.VTScanApiService
import com.quantum_prof.vtscansuite.data.remote.VtApiKeyGuard
import com.quantum_prof.vtscansuite.data.remote.VtHttpException
import com.quantum_prof.vtscansuite.data.remote.VtUploadPath
import com.quantum_prof.vtscansuite.domain.repository.ProgressCallback
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class VirusTotalRepositoryImpl @Inject constructor(
    private val api: VTScanApiService,
    private val budget: BudgetReserver
) : VirusTotalRepository {

    /**
     * Jede Anfrage an VirusTotal geht durch diese Stelle, und hier wird auch das gemeinsame
     * Tageskontingent reserviert: **vor** dem Senden, für Einzelscans, Hash-Prüfung und
     * Upload-Stapel gleichermaßen. Die Aufrufer reservieren deshalb nicht noch einmal.
     * Ist nichts mehr frei, geht nichts raus und es kommt eine [BudgetExhaustedException].
     * Die Reservierung wird in jedem Fall wieder freigegeben, auch bei Fehler und Abbruch.
     */
    private suspend fun <T> budgeted(requests: Int = 1, block: suspend () -> T): T {
        val reservation = budget.tryReserve(requests) ?: throw BudgetExhaustedException()
        try {
            return block()
        } finally {
            reservation.release()
        }
    }

    /** Wartet die angegebenen Millisekunden. Nur für Tests austauschbar, damit sie nicht echt warten. */
    internal var sleeper: suspend (Long) -> Unit = { delay(it) }

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
            val response = budgeted { api.getFileReport(apiKey, hash) }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(httpError(response.code(), "API error"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReport(apiKey: String, id: String, isUrl: Boolean): Result<FileReportResponse> {
        return try {
            val response = budgeted {
                if (isUrl) api.getUrlReport(apiKey, id) else api.getFileReport(apiKey, id)
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(httpError(response.code(), "API error"))
            }
        } catch (e: CancellationException) {
            throw e
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

            // 1. Datei hochladen
            val analysisId = when (val posted = postFile(apiKey, file, onProgress)) {
                is PostResult.Failed -> return Result.failure(posted.error)
                is PostResult.Posted -> posted.analysisId
            }
            // 2. Warten, bis die Analyse abgeschlossen ist
            try {
                awaitAnalysisCompletion(apiKey, analysisId, onProgress)
            } catch (_: BudgetExhaustedException) {
                // Die Datei ist schon eingereicht; nur die Folgeabfragen fehlen.
                return Result.failure(BudgetExhaustedException(afterSubmission = true))
            }
            // 3. Datei-Report nachladen, bis die Engine-Ergebnisse tatsächlich vorhanden sind
            onProgress(ScanPhase.FETCHING, null)
            fetchPopulatedReport(apiKey, fileHash, isUrl = false, onProgress).let { report ->
                if (report.exceptionOrNull() is BudgetExhaustedException) {
                    Result.failure(BudgetExhaustedException(afterSubmission = true))
                } else {
                    report
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitFile(
        apiKey: String,
        file: File,
        onProgress: ProgressCallback
    ): Result<String> {
        return try {
            when (val posted = postFile(apiKey, file, onProgress)) {
                is PostResult.Posted -> Result.success(posted.analysisId)
                is PostResult.Failed -> Result.failure(
                    if (posted.bodySent) posted.error else UploadNotStartedException(posted.error)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private sealed interface PostResult {
        data class Posted(val analysisId: String) : PostResult

        /** [bodySent] false: der Upload ist gescheitert, bevor ein Byte der Datei raus ging. */
        data class Failed(val error: Throwable, val bodySent: Boolean) : PostResult
    }

    /**
     * Der Upload selbst, von [uploadFile] und [submitFile] gemeinsam genutzt: bis 32 MB direkt
     * an `files`, darüber über eine eigens angeforderte Upload-Adresse (zwei Anfragen).
     */
    private suspend fun postFile(
        apiKey: String,
        file: File,
        onProgress: ProgressCallback
    ): PostResult {
        // Ein Upload kostet eine Anfrage, über 32 MB zwei (Upload-Adresse und Upload). Beide
        // werden zusammen reserviert: reicht das Kontingent nur für eine, geht keine raus.
        val reservation = budget.tryReserve(VtUploadPath.requestsFor(file.length()))
            ?: return PostResult.Failed(BudgetExhaustedException(), bodySent = false)
        try {
            return postFileReserved(apiKey, file, onProgress)
        } finally {
            reservation.release()
        }
    }

    private suspend fun postFileReserved(
        apiKey: String,
        file: File,
        onProgress: ProgressCallback
    ): PostResult {
        onProgress(ScanPhase.UPLOADING, 0f)
        val rawBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val progressBody = ProgressRequestBody(rawBody) { fraction ->
            onProgress(ScanPhase.UPLOADING, fraction)
        }
        val body = MultipartBody.Part.createFormData("file", file.name, progressBody)

        val uploadResponse = if (!VtUploadPath.needsUploadUrl(file.length())) {
            api.uploadFile(apiKey, body)
        } else {
            val urlResponse = try {
                api.getUploadUrl(apiKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return PostResult.Failed(e, bodySent = false)
            }
            if (!urlResponse.isSuccessful || urlResponse.body() == null) {
                return PostResult.Failed(
                    httpError(urlResponse.code(), "Could not obtain an upload URL", urlResponse.retryAfterSeconds()),
                    bodySent = false
                )
            }
            val uploadUrl = urlResponse.body()!!.data
            // Die Ziel-URL kommt aus einer API-Antwort. Vor dem ersten Byte prüfen, dass
            // sie wirklich zu VirusTotal gehört – sonst ginge der API-Schlüssel mit.
            if (!VtApiKeyGuard.isVirusTotalHttps(uploadUrl)) {
                return PostResult.Failed(
                    Exception("VirusTotal returned an upload address outside virustotal.com. Nothing was uploaded."),
                    bodySent = false
                )
            }
            api.uploadFileToUrl(uploadUrl, apiKey, body)
        }
        val responseBody = uploadResponse.body()
        return if (uploadResponse.isSuccessful && responseBody != null) {
            PostResult.Posted(responseBody.data.id)
        } else {
            PostResult.Failed(
                httpError(uploadResponse.code(), "Upload error", uploadResponse.retryAfterSeconds()),
                bodySent = true
            )
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
            val cached = try {
                budgeted { api.getUrlReport(apiKey, precomputedId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BudgetExhaustedException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (cached?.isSuccessful == true && cached.body().isPopulated()) {
                return Result.success(cached.body()!!)
            }

            // 2. URL zur Analyse einreichen
            onProgress(ScanPhase.SUBMITTING, null)
            val submit = budgeted { api.submitUrl(apiKey, normalized) }
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
        } catch (e: CancellationException) {
            throw e
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
            sleeper(schedule.nextDelayMs())
            val response = try {
                budgeted { api.getAnalysisReport(apiKey, analysisId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BudgetExhaustedException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: continue
            if (response.code() == 429) {
                // Rate-Limit: nicht sofort erneut fragen.
                response.retryAfterMs()?.let { sleeper(it) }
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
            // Ohne Kontingent bringt Warten nichts: der nächste Versuch scheitert genauso.
            if (last.exceptionOrNull() is BudgetExhaustedException) return last
            onProgress(ScanPhase.ANALYZING, null) // VirusTotal stellt das Ergebnis noch fertig
            sleeper(schedule.nextDelayMs())
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
    private fun httpError(code: Int, context: String, retryAfterSeconds: Long? = null): Exception =
        if (code == 429) RateLimitException(retryAfterSeconds) else VtHttpException(code, "$context (code $code)")

    private fun Response<*>.retryAfterSeconds(): Long? = headers()["Retry-After"]?.trim()?.toLongOrNull()

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

package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.model.AnalysisAttributes
import com.quantum_prof.vtscansuite.data.model.AnalysisData
import com.quantum_prof.vtscansuite.data.model.AnalysisResponse
import com.quantum_prof.vtscansuite.data.model.AnalysisStats
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.FileUploadResponse
import com.quantum_prof.vtscansuite.data.model.ReportAttributes
import com.quantum_prof.vtscansuite.data.model.ReportData
import com.quantum_prof.vtscansuite.data.model.UploadData
import com.quantum_prof.vtscansuite.data.model.UploadUrlResponse
import com.quantum_prof.vtscansuite.data.model.UrlSubmitResponse
import com.quantum_prof.vtscansuite.data.remote.BudgetExhaustedException
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.remote.UploadNotStartedException
import com.quantum_prof.vtscansuite.data.remote.VTScanApiService
import com.quantum_prof.vtscansuite.data.remote.VtHttpException
import com.quantum_prof.vtscansuite.data.repository.BudgetReserver
import com.quantum_prof.vtscansuite.data.repository.RequestBudget
import com.quantum_prof.vtscansuite.data.repository.VirusTotalRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException

/**
 * Prüft den Upload-Weg des Repositorys gegen eine Attrappe der API. Es gibt keine echte
 * Anfrage, keinen echten Schlüssel und keine echte Datei von Wert.
 *
 * Schwerpunkt: der bestehende Einzel-Upload ([VirusTotalRepositoryImpl.uploadFile]), der sich
 * seit Stufe 6 den Upload-Teil mit [VirusTotalRepositoryImpl.submitFile] teilt.
 */
class VirusTotalRepositoryUploadTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanUp() {
        tempFiles.forEach { it.delete() }
    }

    private fun tempFile(sizeBytes: Long): File =
        File.createTempFile("veto-test", ".apk").also {
            tempFiles += it
            RandomAccessFile(it, "rw").use { raf -> raf.setLength(sizeBytes) }
        }

    // ---- Attrappe --------------------------------------------------------------------------

    private class FakeApi : VTScanApiService {
        val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        var uploadedPart: MultipartBody.Part? = null
        var uploadToUrlTarget: String? = null
        val apiKeysSeen: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

        var directUpload: suspend () -> Response<FileUploadResponse> = { ok(FileUploadResponse(UploadData("analysis", "A1"))) }
        var uploadUrl: suspend () -> Response<UploadUrlResponse> =
            { ok(UploadUrlResponse("https://upload.virustotal.com/_ah/upload/xyz")) }
        var urlUpload: suspend () -> Response<FileUploadResponse> = { ok(FileUploadResponse(UploadData("analysis", "A2"))) }
        var analysis: suspend () -> Response<AnalysisResponse> = {
            ok(AnalysisResponse(AnalysisData("A1", "analysis", AnalysisAttributes(status = "completed"))))
        }
        var report: suspend () -> Response<FileReportResponse> = { ok(populatedReport("hash")) }

        override suspend fun getFileReport(apiKey: String, fileHash: String): Response<FileReportResponse> {
            calls += "getFileReport"
            apiKeysSeen += apiKey
            return report()
        }

        override suspend fun submitUrl(apiKey: String, url: String): Response<UrlSubmitResponse> =
            error("not used")

        override suspend fun getUrlReport(apiKey: String, urlId: String): Response<FileReportResponse> =
            error("not used")

        override suspend fun uploadFile(apiKey: String, file: MultipartBody.Part): Response<FileUploadResponse> {
            calls += "uploadFile"
            apiKeysSeen += apiKey
            uploadedPart = file
            return directUpload()
        }

        override suspend fun getUploadUrl(apiKey: String): Response<UploadUrlResponse> {
            calls += "getUploadUrl"
            apiKeysSeen += apiKey
            return uploadUrl()
        }

        override suspend fun uploadFileToUrl(
            url: String,
            apiKey: String,
            file: MultipartBody.Part
        ): Response<FileUploadResponse> {
            calls += "uploadFileToUrl"
            uploadToUrlTarget = url
            uploadedPart = file
            apiKeysSeen += apiKey
            return urlUpload()
        }

        override suspend fun getAnalysisReport(apiKey: String, analysisId: String): Response<AnalysisResponse> {
            calls += "getAnalysisReport"
            apiKeysSeen += apiKey
            return analysis()
        }
    }

    private companion object {
        fun <T> ok(body: T): Response<T> = Response.success(body)

        fun <T> err(code: Int, retryAfter: String? = null): Response<T> {
            val raw = okhttp3.Response.Builder()
                .code(code)
                .message("error")
                .protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("https://www.virustotal.com/api/v3/").build())
                .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
                .build()
            return Response.error(ByteArray(0).toResponseBody(null), raw)
        }

        fun populatedReport(hash: String) = FileReportResponse(
            ReportData(
                id = hash,
                type = "file",
                attributes = ReportAttributes(lastAnalysisStats = AnalysisStats(harmless = 60, undetected = 10))
            )
        )

        const val BIG = 33L * 1024 * 1024
    }

    /** Ein Kontingent, das mitschreibt, wie viel reserviert wurde, und frei steuerbar ist. */
    private class FakeBudget(limit: Int = 500) : BudgetReserver {
        val inner = RequestBudget(limit) { spent }
        @Volatile
        var spent = 0
        val reservations: MutableList<Int> = java.util.Collections.synchronizedList(mutableListOf())

        override suspend fun tryReserve(requests: Int): RequestBudget.Reservation? {
            reservations += requests
            return inner.tryReserve(requests)
        }
    }

    private fun repo(
        api: FakeApi,
        slept: MutableList<Long> = mutableListOf(),
        budget: BudgetReserver = FakeBudget()
    ) = VirusTotalRepositoryImpl(api, budget).apply { sleeper = { slept += it } }

    // ---- uploadFile: bestehender Einzel-Upload -------------------------------------------------

    @Test
    fun `uploadFile sends a small file directly, waits for the analysis and returns the report`() = runBlocking {
        val api = FakeApi()
        val slept = mutableListOf<Long>()
        val file = tempFile(2048)

        val result = repo(api, slept).uploadFile("KEY", file, knownHash = "hash")

        assertTrue(result.isSuccess)
        assertEquals("hash", result.getOrNull()!!.data.id)
        assertEquals(listOf("uploadFile", "getAnalysisReport", "getFileReport"), api.calls)
        assertFalse("getUploadUrl" in api.calls)
        assertEquals(2048L, api.uploadedPart!!.body.contentLength())
        assertEquals(setOf("KEY"), api.apiKeysSeen.toSet())
        // Die Statusabfrage wartet (hier nur gemerkt, nicht abgesessen).
        assertEquals(1, slept.size)
    }

    @Test
    fun `uploadFile sends a file over 32 MB through the upload url`() = runBlocking {
        val api = FakeApi()
        val file = tempFile(BIG)

        val result = repo(api).uploadFile("KEY", file, knownHash = "hash")

        assertTrue(result.isSuccess)
        assertEquals(listOf("getUploadUrl", "uploadFileToUrl", "getAnalysisReport", "getFileReport"), api.calls)
        assertEquals("https://upload.virustotal.com/_ah/upload/xyz", api.uploadToUrlTarget)
        assertFalse("uploadFile" in api.calls)
    }

    @Test
    fun `uploadFile refuses an upload address outside virustotal and sends nothing`() = runBlocking {
        val api = FakeApi()
        api.uploadUrl = { ok(UploadUrlResponse("https://evil.example.com/upload")) }

        val result = repo(api).uploadFile("KEY", tempFile(BIG), knownHash = "hash")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Nothing was uploaded"))
        assertFalse("uploadFileToUrl" in api.calls)
        assertFalse("uploadFile" in api.calls)
    }

    @Test
    fun `uploadFile refuses a plain http upload address`() = runBlocking {
        val api = FakeApi()
        api.uploadUrl = { ok(UploadUrlResponse("http://upload.virustotal.com/x")) }

        val result = repo(api).uploadFile("KEY", tempFile(BIG), knownHash = "hash")

        assertTrue(result.isFailure)
        assertFalse("uploadFileToUrl" in api.calls)
    }

    @Test
    fun `uploadFile keeps the status code of a rejected upload`() = runBlocking {
        val api = FakeApi()
        api.directUpload = { err(413) }

        val error = repo(api).uploadFile("KEY", tempFile(10), knownHash = "hash").exceptionOrNull()

        assertEquals(413, (error as VtHttpException).code)
        assertFalse("getAnalysisReport" in api.calls)
    }

    @Test
    fun `uploadFile maps 429 to the rate limit error and keeps Retry-After`() = runBlocking {
        val api = FakeApi()
        api.directUpload = { err(429, retryAfter = "42") }

        val error = repo(api).uploadFile("KEY", tempFile(10), knownHash = "hash").exceptionOrNull()

        assertEquals(42L, (error as RateLimitException).retryAfterSeconds)
    }

    @Test
    fun `uploadFile reports a failed upload url request without sending the file`() = runBlocking {
        val api = FakeApi()
        api.uploadUrl = { err(403) }

        val error = repo(api).uploadFile("KEY", tempFile(BIG), knownHash = "hash").exceptionOrNull()

        assertEquals(403, (error as VtHttpException).code)
        // Der bestehende Weg wirft hier keine UploadNotStartedException: der Typ bleibt erhalten.
        assertFalse("uploadFileToUrl" in api.calls)
    }

    @Test
    fun `uploadFile hashes the file itself when no hash is passed`() = runBlocking {
        val api = FakeApi()
        // Der Report wird unter dem berechneten Hash geholt; die Attrappe liefert für jeden einen.
        val result = repo(api).uploadFile("KEY", tempFile(16))
        assertTrue(result.isSuccess)
        assertEquals("getFileReport", api.calls.last())
    }

    @Test
    fun `uploadFile rethrows cancellation instead of returning a failure`() {
        val api = FakeApi()
        api.directUpload = { throw CancellationException("stop") }
        assertThrows(CancellationException::class.java) {
            runBlocking { repo(api).uploadFile("KEY", tempFile(10), knownHash = "hash") }
        }
    }

    // ---- submitFile -----------------------------------------------------------------------------

    @Test
    fun `submitFile only uploads and returns the analysis id`() = runBlocking {
        val api = FakeApi()

        val result = repo(api).submitFile("KEY", tempFile(512))

        assertEquals("A1", result.getOrNull())
        assertEquals(listOf("uploadFile"), api.calls) // keine Statusabfrage, kein Bericht
    }

    @Test
    fun `submitFile uses the upload url for large files and nothing else`() = runBlocking {
        val api = FakeApi()

        val result = repo(api).submitFile("KEY", tempFile(BIG))

        assertEquals("A2", result.getOrNull())
        assertEquals(listOf("getUploadUrl", "uploadFileToUrl"), api.calls)
    }

    @Test
    fun `submitFile marks a failure before the first byte as not started`() = runBlocking {
        val api = FakeApi()
        api.uploadUrl = { throw IOException("offline") }

        val error = repo(api).submitFile("KEY", tempFile(BIG)).exceptionOrNull()

        assertTrue(error is UploadNotStartedException)
        assertTrue(error!!.cause is IOException)
        assertFalse("uploadFileToUrl" in api.calls)
    }

    @Test
    fun `submitFile does not claim nothing was sent when the upload itself breaks`() = runBlocking {
        val api = FakeApi()
        api.directUpload = { throw SocketTimeoutException("read timed out") }

        val error = repo(api).submitFile("KEY", tempFile(512)).exceptionOrNull()

        assertTrue(error is SocketTimeoutException)
    }

    @Test
    fun `submitFile keeps 429 with Retry-After and an http error after sending as sent`() = runBlocking {
        val limited = FakeApi().apply { directUpload = { err(429, retryAfter = "7") } }
        assertEquals(
            7L,
            (repo(limited).submitFile("KEY", tempFile(10)).exceptionOrNull() as RateLimitException).retryAfterSeconds
        )

        val broken = FakeApi().apply { directUpload = { err(503) } }
        val error = repo(broken).submitFile("KEY", tempFile(10)).exceptionOrNull()
        assertEquals(503, (error as VtHttpException).code)
    }

    @Test
    fun `submitFile rethrows cancellation`() {
        val api = FakeApi()
        api.directUpload = { throw CancellationException("stop") }
        assertThrows(CancellationException::class.java) {
            runBlocking { repo(api).submitFile("KEY", tempFile(10)) }
        }
    }

    @Test
    fun `the api key is only passed on as the header argument and never appears in errors`() = runBlocking {
        val api = FakeApi()
        api.directUpload = { err(500) }
        val error = repo(api).submitFile("SECRET-KEY-123", tempFile(10)).exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains("SECRET-KEY-123"))
    }

    // ---- Gemeinsames Tageskontingent: das Repository reserviert vor jeder Anfrage --------------

    @Test
    fun `a hash lookup reserves one request and releases it`() = runBlocking {
        val api = FakeApi()
        val budget = FakeBudget()
        repo(api, budget = budget).getFileReport("KEY", "hash")
        assertEquals(listOf(1), budget.reservations)
        assertEquals(500, budget.inner.available())
    }

    @Test
    fun `an exhausted budget sends no hash lookup`() = runBlocking {
        val api = FakeApi()
        val budget = FakeBudget().apply { spent = 500 }
        val error = repo(api, budget = budget).getFileReport("KEY", "hash").exceptionOrNull()
        assertTrue(error is BudgetExhaustedException)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `an exhausted budget is not mistaken for an unknown file`() = runBlocking {
        val budget = FakeBudget().apply { spent = 500 }
        val error = repo(FakeApi(), budget = budget).getFileReport("KEY", "hash").exceptionOrNull()
        assertFalse(com.quantum_prof.vtscansuite.scan.UploadGate.isVerifiedUnknown(error))
    }

    @Test
    fun `a small single upload reserves exactly one request per call, never double`() = runBlocking {
        val budget = FakeBudget()
        repo(FakeApi(), budget = budget).uploadFile("KEY", tempFile(10), knownHash = "hash")
        // Upload, eine Statusabfrage, ein Bericht: drei Anfragen, je einmal reserviert.
        assertEquals(listOf(1, 1, 1), budget.reservations)
        assertEquals(500, budget.inner.available())
    }

    @Test
    fun `a large upload reserves both of its requests up front`() = runBlocking {
        val budget = FakeBudget()
        repo(FakeApi(), budget = budget).submitFile("KEY", tempFile(BIG))
        assertEquals(listOf(2), budget.reservations)
    }

    @Test
    fun `a large upload with budget for only one request sends nothing`() = runBlocking {
        val api = FakeApi()
        val budget = FakeBudget(limit = 1)
        val error = repo(api, budget = budget).submitFile("KEY", tempFile(BIG)).exceptionOrNull()
        assertTrue(error is UploadNotStartedException)
        assertTrue(error!!.cause is BudgetExhaustedException)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `a single upload without budget sends nothing and is not a rate limit`() = runBlocking {
        val api = FakeApi()
        val budget = FakeBudget().apply { spent = 500 }
        val error = repo(api, budget = budget).uploadFile("KEY", tempFile(10), knownHash = "hash").exceptionOrNull()
        assertTrue(error is BudgetExhaustedException)
        assertFalse((error as BudgetExhaustedException).afterSubmission)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `a single upload that runs out of budget after submitting says so`() = runBlocking {
        val api = FakeApi()
        // Genau eine Anfrage frei: der Upload geht raus, die Statusabfrage nicht mehr.
        val budget = FakeBudget(limit = 1)
        api.directUpload = {
            budget.spent = 1
            ok(FileUploadResponse(UploadData("analysis", "A1")))
        }
        val error = repo(api, budget = budget).uploadFile("KEY", tempFile(10), knownHash = "hash").exceptionOrNull()
        assertTrue((error as BudgetExhaustedException).afterSubmission)
        assertEquals(listOf("uploadFile"), api.calls)
    }

    @Test
    fun `reservations are released after http errors and exceptions`() = runBlocking {
        val budget = FakeBudget()
        val failing = FakeApi().apply { directUpload = { err(500) } }
        repo(failing, budget = budget).submitFile("KEY", tempFile(10))
        val throwing = FakeApi().apply { directUpload = { throw IOException("reset") } }
        repo(throwing, budget = budget).submitFile("KEY", tempFile(10))
        val urlFails = FakeApi().apply { uploadUrl = { throw IOException("offline") } }
        repo(urlFails, budget = budget).submitFile("KEY", tempFile(BIG))
        assertEquals(500, budget.inner.available())
    }

    @Test
    fun `a reservation is released when the upload is cancelled`() = runBlocking {
        val budget = FakeBudget()
        val started = CompletableDeferred<Unit>()
        val api = FakeApi().apply {
            directUpload = {
                started.complete(Unit)
                CompletableDeferred<Unit>().await() // hängt, bis abgebrochen wird
                error("unreachable")
            }
        }
        val job = launch(Dispatchers.Default) { repo(api, budget = budget).submitFile("KEY", tempFile(10)) }
        started.await()
        assertEquals(499, budget.inner.available()) // reserviert, solange der Upload läuft
        job.cancelAndJoin()
        assertEquals(500, budget.inner.available())
    }

    @Test
    fun `concurrent single and bulk lookups never exceed the budget`() = runBlocking {
        val budget = FakeBudget(limit = 10)
        val api = FakeApi()
        val repository = repo(api, budget = budget)
        val gate = CompletableDeferred<Unit>()
        api.report = {
            gate.await() // alle Anfragen gleichzeitig offen halten
            ok(populatedReport("hash"))
        }
        val calls = (1..40).map {
            async(Dispatchers.Default) { repository.getFileReport("KEY", "h$it") }
        }
        // Bis alle Reservierungen versucht sind: höchstens 10 dürfen gleichzeitig unterwegs sein.
        while (budget.reservations.size < 40) kotlinx.coroutines.delay(5)
        assertEquals(0, budget.inner.available())
        gate.complete(Unit)
        val results = calls.awaitAll()
        assertEquals(10, results.count { it.isSuccess })
        assertEquals(30, results.count { it.exceptionOrNull() is BudgetExhaustedException })
        assertEquals(10, api.calls.size)
    }
}
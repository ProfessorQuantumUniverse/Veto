package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.remote.BudgetExhaustedException
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.remote.UploadNotStartedException
import com.quantum_prof.vtscansuite.data.remote.VtHttpException
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.scan.ConsentedItem
import com.quantum_prof.vtscansuite.scan.SkipReason
import com.quantum_prof.vtscansuite.scan.UploadEnvironment
import com.quantum_prof.vtscansuite.scan.UploadItem
import com.quantum_prof.vtscansuite.scan.UploadItemStatus
import com.quantum_prof.vtscansuite.scan.UploadQueueRunner
import com.quantum_prof.vtscansuite.scan.UploadQueueState
import com.quantum_prof.vtscansuite.scan.UploadStopReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Der Upload-Ablauf gegen eine Attrappe der Umgebung. Das Tageskontingent reserviert das
 * Repository (siehe VirusTotalRepositoryUploadTest); der Ablauf bekommt hier nur dessen
 * Ergebnis, etwa [BudgetExhaustedException].
 */
class UploadQueueRunnerTest {

    // ---- Testaufbau ----------------------------------------------------------------------

    private companion object {
        fun consented(pkg: String, size: Long = 1000, sha: String = "hash-$pkg") =
            ConsentedItem(pkg, pkg, "1.0", 7, size, sha)

        fun installed(pkg: String, size: Long = 1000, system: Boolean = false, webApk: Boolean = false, version: Long = 7) =
            InstalledApp(pkg, pkg, File("/fake/$pkg.apk"), system, webApk, "1.0", version, size)
    }

    private class FakeEnv : UploadEnvironment {
        val apps = mutableMapOf<String, InstalledApp>()
        val records = mutableMapOf<String, AppScanRecord>()
        val hashes = mutableMapOf<String, String>()
        var readable = true
        val submitted = mutableListOf<String>()
        val pauses = mutableListOf<Long>()
        val forgotten = mutableListOf<String>()

        /** Pro Aufruf von submit() ein Ergebnis; das letzte wird wiederholt. */
        val results = ArrayDeque<Result<String>>()
        var submitHook: (suspend (File) -> Unit)? = null
        var resolveThrows: Exception? = null
        var submittedThrows: Exception? = null
        var submitThrows: Exception? = null

        override suspend fun resolveApp(packageName: String): InstalledApp? {
            resolveThrows?.let { throw it }
            return apps[packageName]
        }

        override suspend fun currentRecord(packageName: String) = records[packageName]
        override suspend fun hash(file: File): String = hashes[file.nameWithoutExtension] ?: "unset"
        override fun isReadable(file: File) = readable

        override suspend fun submit(file: File): Result<String> {
            submitted += file.nameWithoutExtension
            submitHook?.invoke(file)
            submitThrows?.let { throw it }
            return if (results.size > 1) results.removeFirst() else results.firstOrNull() ?: Result.success("id")
        }

        override suspend fun pause(millis: Long) {
            pauses += millis
        }

        override suspend fun onSubmitted(packageName: String) {
            forgotten += packageName
            submittedThrows?.let { throw it }
        }

        fun addApp(pkg: String, size: Long = 1000, system: Boolean = false, webApk: Boolean = false) {
            apps[pkg] = installed(pkg, size, system, webApk)
            records[pkg] = AppScanRecord(pkg, "hash-$pkg", 7, size, 0L, AppScanOutcome.UNKNOWN)
            hashes[pkg] = "hash-$pkg"
        }
    }

    private fun items(vararg pkgs: String) = pkgs.map { UploadItem(consented(it)) }.toMutableList()

    private fun run(env: FakeEnv, items: MutableList<UploadItem>, updates: MutableList<List<UploadItem>> = mutableListOf()) =
        runBlocking { UploadQueueRunner(env) { updates += it }.run(items) }

    private fun List<UploadItem>.statuses() = map { it.status }

    // ---- Ablauf -------------------------------------------------------------------------

    @Test
    fun `selected apps are submitted one at a time and counted as submitted`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.COMPLETED, outcome.reason)
        assertEquals(listOf("a", "b"), env.submitted)
        assertEquals(listOf(UploadItemStatus.SUBMITTED, UploadItemStatus.SUBMITTED), list.statuses())
        assertEquals(listOf("a", "b"), env.forgotten)
    }

    @Test
    fun `an unselected app can never reach the upload call`() {
        // "x" ist installiert und bestätigt unbekannt, steht aber nicht in der Einwilligung.
        val env = FakeEnv().apply { addApp("a"); addApp("x") }
        run(env, items("a"))
        assertEquals(listOf("a"), env.submitted)
        assertFalse("x" in env.submitted)
    }

    @Test
    fun `an app changed after consent is skipped, not uploaded`() {
        val env = FakeEnv().apply { addApp("a") }
        env.hashes["a"] = "different"
        val list = items("a")
        run(env, list)
        assertTrue(env.submitted.isEmpty())
        assertEquals(UploadItemStatus.SKIPPED, list[0].status)
        assertEquals(SkipReason.HASH_CHANGED, list[0].skipReason)
    }

    @Test
    fun `system apps, web apks, gone, updated and no longer unknown apps are skipped`() {
        val env = FakeEnv().apply {
            addApp("sys", system = true)
            addApp("web", webApk = true)
            addApp("upd")
            apps["upd"] = installed("upd", version = 8)
            addApp("known")
            records["known"] = records["known"]!!.copy(outcome = AppScanOutcome.KNOWN)
        }
        val list = items("sys", "web", "gone", "upd", "known")
        run(env, list)
        assertTrue(env.submitted.isEmpty())
        assertEquals(
            listOf(
                SkipReason.NOT_ELIGIBLE, SkipReason.NOT_ELIGIBLE, SkipReason.APP_GONE,
                SkipReason.CHANGED, SkipReason.NOT_ELIGIBLE
            ),
            list.map { it.skipReason }
        )
    }

    @Test
    fun `an unreadable apk is skipped`() {
        val env = FakeEnv().apply { addApp("unread"); readable = false }
        val list = items("unread")
        run(env, list)
        assertEquals(SkipReason.UNREADABLE, list[0].skipReason)
        assertTrue(env.submitted.isEmpty())
    }

    // ---- Kontingent (das Repository reserviert; der Ablauf bekommt nur das Ergebnis) -----------

    @Test
    fun `an exhausted budget stops the batch before anything is sent and keeps the item pending`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.results += Result.failure(BudgetExhaustedException())
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.DAILY_LIMIT, outcome.reason)
        assertEquals(listOf(UploadItemStatus.PENDING, UploadItemStatus.PENDING), list.statuses())
        assertTrue(env.forgotten.isEmpty())
        assertTrue(UploadQueueState.Finished(list, outcome.reason).canResume)
    }

    @Test
    fun `the budget runs out in the middle of the batch`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b"); addApp("c") }
        env.results += Result.success("id")
        env.results += Result.failure(BudgetExhaustedException())
        val list = items("a", "b", "c")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.DAILY_LIMIT, outcome.reason)
        assertEquals(
            listOf(UploadItemStatus.SUBMITTED, UploadItemStatus.PENDING, UploadItemStatus.PENDING),
            list.statuses()
        )
        assertEquals(listOf("a"), env.forgotten)
    }

    @Test
    fun `an exhausted budget wrapped as not started still stops the batch`() {
        val env = FakeEnv().apply { addApp("a") }
        env.results += Result.failure(UploadNotStartedException(BudgetExhaustedException()))
        val list = items("a")
        assertEquals(UploadStopReason.DAILY_LIMIT, run(env, list).reason)
        assertEquals(UploadItemStatus.PENDING, list[0].status)
    }

    @Test
    fun `a rate limit stops the batch, keeps the item pending and reports Retry-After`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.results += Result.failure(RateLimitException(retryAfterSeconds = 42))
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.RATE_LIMITED, outcome.reason)
        assertEquals(42L, outcome.retryAfterSeconds)
        assertEquals(listOf("a"), env.submitted)
        assertEquals(listOf(UploadItemStatus.PENDING, UploadItemStatus.PENDING), list.statuses())
        val finished = UploadQueueState.Finished(list, outcome.reason, outcome.retryAfterSeconds)
        assertTrue(finished.canResume)
    }

    @Test
    fun `a rejected api key stops the batch without marking the item failed`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.results += Result.failure(VtHttpException(401, "no"))
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.AUTH_FAILED, outcome.reason)
        assertEquals(listOf(UploadItemStatus.PENDING, UploadItemStatus.PENDING), list.statuses())
        assertFalse(UploadQueueState.Finished(list, outcome.reason).canResume)
    }

    @Test
    fun `a busy batch can be continued but a stopped one with a rejected key cannot`() {
        val pending = items("a")
        assertTrue(UploadQueueState.Finished(pending, UploadStopReason.BUSY).canResume)
        assertFalse(UploadQueueState.Finished(pending, UploadStopReason.CANCELLED).canResume)
        assertFalse(UploadQueueState.Finished(pending, UploadStopReason.ERROR).canResume)
    }

    @Test
    fun `uploads are spaced by the free tier interval but not before the first`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b"); addApp("c") }
        run(env, items("a", "b", "c"))
        assertEquals(listOf(16_000L, 16_000L), env.pauses)
    }

    // ---- Wiederholungen und unklare Ausgänge -----------------------------------------------

    @Test
    fun `a connection failure before sending is retried twice at most, then failed`() {
        val env = FakeEnv().apply { addApp("a") }
        env.results += Result.failure(ConnectException("refused"))
        val list = items("a")
        run(env, list)
        assertEquals(3, env.submitted.size) // 1 Versuch + 2 Wiederholungen
        assertEquals(UploadItemStatus.FAILED, list[0].status)
        assertEquals(listOf(30_000L, 60_000L), env.pauses)
        assertTrue(env.forgotten.isEmpty())
    }

    @Test
    fun `a retry that succeeds ends as submitted`() {
        val env = FakeEnv().apply { addApp("a") }
        env.results += Result.failure(UnknownHostException("dns"))
        env.results += Result.success("id")
        val list = items("a")
        run(env, list)
        assertEquals(2, env.submitted.size)
        assertEquals(UploadItemStatus.SUBMITTED, list[0].status)
    }

    @Test
    fun `a timeout after sending is uncertain and never retried automatically`() {
        val env = FakeEnv().apply { addApp("a") }
        env.results += Result.failure(SocketTimeoutException("read timed out"))
        val list = items("a")
        run(env, list)
        assertEquals(1, env.submitted.size)
        assertEquals(UploadItemStatus.UNCERTAIN, list[0].status)
        // Das alte "unbekannt" gilt nicht mehr: die nächste Hash-Prüfung klärt es.
        assertEquals(listOf("a"), env.forgotten)
    }

    @Test
    fun `a server error is uncertain, a client error is a confirmed failure`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.results += Result.failure(VtHttpException(503, "down"))
        env.results += Result.failure(VtHttpException(413, "too big"))
        val list = items("a", "b")
        run(env, list)
        assertEquals(listOf(UploadItemStatus.UNCERTAIN, UploadItemStatus.FAILED), list.statuses())
        assertEquals(listOf("a"), env.forgotten)
    }

    @Test
    fun `a failure before the first byte is a confirmed failure even for io errors`() {
        val env = FakeEnv().apply { addApp("a") }
        env.results += Result.failure(UploadNotStartedException(IOException("upload url unreachable")))
        val list = items("a")
        run(env, list)
        assertEquals(1, env.submitted.size)
        assertEquals(UploadItemStatus.FAILED, list[0].status)
    }

    // ---- Zustände und Fortschritt ---------------------------------------------------------

    @Test
    fun `progress counts only resolved items, never the one that just started`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        val updates = mutableListOf<List<UploadItem>>()
        run(env, items("a", "b"), updates)
        val fractions = updates.map { UploadQueueState.Running(it).fraction }
        // Kein Rückschritt, und ein Eintrag im Zustand "UPLOADING" erhöht nichts.
        assertEquals(fractions.sorted(), fractions)
        assertEquals(1f, fractions.last(), 0f)
        val whileUploadingFirst = updates.first { it[0].status == UploadItemStatus.UPLOADING }
        assertEquals(0f, UploadQueueState.Running(whileUploadingFirst).fraction, 0f)
    }

    @Test
    fun `counters add up to the total`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b"); addApp("c") }
        env.results += Result.success("id")
        env.results += Result.failure(VtHttpException(400, "bad"))
        val list = items("a", "b", "gone")
        run(env, list)
        val s = UploadQueueState.Finished(list, UploadStopReason.COMPLETED)
        assertEquals(1, s.submitted)
        assertEquals(1, s.failed)
        assertEquals(1, s.skipped)
        assertEquals(s.total, s.submitted + s.failed + s.uncertain + s.skipped + s.cancelled + s.pending)
    }

    // ---- Abbruch --------------------------------------------------------------------------

    @Test
    fun `cancelling during an upload cancels the item and prevents later uploads`() = runBlocking {
        val env = FakeEnv().apply { addApp("a"); addApp("b"); addApp("c") }
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        env.submitHook = {
            started.complete(Unit)
            never.await() // hängt, bis der Lauf abgebrochen wird
        }
        val list = items("a", "b", "c")
        val updates = mutableListOf<List<UploadItem>>()
        val job = launch(Dispatchers.Default) { UploadQueueRunner(env) { updates += it }.run(list) }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf("a"), env.submitted)
        assertEquals(List(3) { UploadItemStatus.CANCELLED }, list.statuses())
        assertEquals(UploadItemStatus.CANCELLED, updates.last()[0].status)
        assertTrue(env.forgotten.isEmpty())
    }

    @Test
    fun `cancel keeps what was already submitted`() {
        val items = mutableListOf(
            UploadItem(consented("a"), UploadItemStatus.SUBMITTED),
            UploadItem(consented("b"), UploadItemStatus.UPLOADING),
            UploadItem(consented("c")),
            UploadItem(consented("d"), UploadItemStatus.FAILED)
        )
        UploadQueueRunner.cancelOpen(items)
        assertEquals(
            listOf(
                UploadItemStatus.SUBMITTED, UploadItemStatus.CANCELLED,
                UploadItemStatus.CANCELLED, UploadItemStatus.FAILED
            ),
            items.statuses()
        )
    }

    // ---- Einordnung der Fehler ---------------------------------------------------------------

    @Test
    fun `classification only retries errors where nothing can have been sent`() {
        val retry = UploadQueueRunner.Verdict.SafeRetry
        assertEquals(retry, UploadQueueRunner.classify(ConnectException()))
        assertEquals(retry, UploadQueueRunner.classify(UnknownHostException()))
        assertTrue(UploadQueueRunner.classify(SocketTimeoutException()) is UploadQueueRunner.Verdict.Uncertain)
        assertTrue(UploadQueueRunner.classify(IOException("reset")) is UploadQueueRunner.Verdict.Uncertain)
        assertTrue(UploadQueueRunner.classify(IllegalStateException()) is UploadQueueRunner.Verdict.Uncertain)
        assertTrue(UploadQueueRunner.classify(IOException("Blocked: the API key")) is UploadQueueRunner.Verdict.Failed)
    }

    // ---- Fehler im Ablauf selbst ------------------------------------------------------------------

    @Test
    fun `a failure in the queue itself ends the run with ERROR and does not hang`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.resolveThrows = IllegalStateException("package manager unavailable")
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.ERROR, outcome.reason)
        assertEquals(listOf(UploadItemStatus.PENDING, UploadItemStatus.PENDING), list.statuses())
        assertTrue(env.submitted.isEmpty())
    }

    @Test
    fun `an unexpected exception during submit leaves that item uncertain`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.submitThrows = IllegalStateException("boom")
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.ERROR, outcome.reason)
        assertEquals(listOf(UploadItemStatus.UNCERTAIN, UploadItemStatus.PENDING), list.statuses())
        assertEquals(listOf("a"), env.submitted)
    }

    @Test
    fun `a failure while forgetting the old result keeps the item submitted and stops the run`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b") }
        env.submittedThrows = IllegalStateException("store unavailable")
        val list = items("a", "b")
        val outcome = run(env, list)
        assertEquals(UploadStopReason.ERROR, outcome.reason)
        assertEquals(listOf(UploadItemStatus.SUBMITTED, UploadItemStatus.PENDING), list.statuses())
        assertEquals(listOf("a"), env.submitted)
        assertFalse(UploadQueueState.Finished(list, outcome.reason).canResume)
    }

    @Test
    fun `a queue never uploads more than the consented items`() {
        val env = FakeEnv().apply { addApp("a"); addApp("b"); addApp("c") }
        env.results += Result.success("id")
        run(env, items("a", "b"))
        assertEquals(setOf("a", "b"), env.submitted.toSet())
        assertFalse("c" in env.submitted)
    }
}

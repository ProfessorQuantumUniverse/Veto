package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.scan.UploadCandidate
import com.quantum_prof.vtscansuite.scan.UploadConsent
import com.quantum_prof.vtscansuite.scan.UploadEligibility
import com.quantum_prof.vtscansuite.scan.UploadEligibilityRules
import com.quantum_prof.vtscansuite.scan.UploadIneligible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class UploadEligibilityTest {

    private fun app(
        pkg: String = "com.example.a",
        system: Boolean = false,
        webApk: Boolean = false,
        version: Long = 7,
        size: Long = 1000
    ) = InstalledApp(
        name = pkg,
        packageName = pkg,
        apkFile = File("/nonexistent/$pkg.apk"),
        isSystem = system,
        isWebApk = webApk,
        versionName = "1.$version",
        versionCode = version,
        apkSize = size
    )

    private fun record(
        pkg: String = "com.example.a",
        outcome: AppScanOutcome = AppScanOutcome.UNKNOWN,
        version: Long = 7,
        size: Long = 1000,
        sha: String = "abc"
    ) = AppScanRecord(pkg, sha, version, size, 0L, outcome)

    private val readable: (File) -> Boolean = { true }

    private fun reason(app: InstalledApp, record: AppScanRecord?, read: (File) -> Boolean = readable) =
        (UploadEligibilityRules.evaluate(app, record, read) as? UploadEligibility.Ineligible)?.reason

    @Test
    fun `a confirmed unknown, current, readable user app is eligible`() {
        val result = UploadEligibilityRules.evaluate(app(), record(), readable)
        val candidate = (result as UploadEligibility.Eligible).candidate
        assertEquals("abc", candidate.sha256)
        assertEquals(1000L, candidate.apkSize)
    }

    @Test
    fun `no stored result is not treated as unknown`() {
        assertEquals(UploadIneligible.NO_RESULT, reason(app(), null))
    }

    @Test
    fun `a known result is not eligible`() {
        assertEquals(
            UploadIneligible.NOT_CONFIRMED_UNKNOWN,
            reason(app(), record(outcome = AppScanOutcome.KNOWN))
        )
    }

    @Test
    fun `a result for another version or size is stale`() {
        assertEquals(UploadIneligible.STALE_RESULT, reason(app(version = 8), record(version = 7)))
        assertEquals(UploadIneligible.STALE_RESULT, reason(app(size = 2000), record(size = 1000)))
    }

    @Test
    fun `system apps and web apks are never eligible, even with an unknown result`() {
        assertEquals(UploadIneligible.SYSTEM_APP, reason(app(system = true), record()))
        assertEquals(UploadIneligible.WEB_APK, reason(app(webApk = true), record()))
    }

    @Test
    fun `an unreadable apk is not eligible`() {
        assertEquals(UploadIneligible.UNREADABLE, reason(app(), record()) { false })
    }

    @Test
    fun `a blank hash is not eligible`() {
        assertEquals(UploadIneligible.NOT_CONFIRMED_UNKNOWN, reason(app(), record(sha = "")))
    }

    @Test
    fun `candidates keep only eligible apps in list order`() {
        val apps = listOf(
            app("a.one"), app("b.system", system = true), app("c.known"), app("d.two"), app("e.nores")
        )
        val records = mapOf(
            "a.one" to record("a.one"),
            "b.system" to record("b.system"),
            "c.known" to record("c.known", outcome = AppScanOutcome.KNOWN),
            "d.two" to record("d.two")
        )
        val names = UploadEligibilityRules.candidates(apps, records, readable).map { it.packageName }
        assertEquals(listOf("a.one", "d.two"), names)
    }

    private fun candidate(n: Int) = UploadCandidate("pkg.$n", "App $n", "1.0", 1, 100, "hash$n")

    @Test
    fun `consent is a snapshot and cannot be changed through the source list`() {
        val source = mutableListOf(candidate(1), candidate(2))
        val consent = UploadConsent.create(source, nowMillis = 5)
        source.add(candidate(3))
        source.removeAt(0)
        assertEquals(listOf("pkg.1", "pkg.2"), consent.items.map { it.packageName })
        assertEquals(5L, consent.grantedAtMillis)
        assertEquals("hash1", consent.items.first().sha256)
    }

    @Test
    fun `consent drops duplicate packages`() {
        val consent = UploadConsent.create(listOf(candidate(1), candidate(1), candidate(2)))
        assertEquals(2, consent.items.size)
    }

    @Test
    fun `consent enforces the batch limit of 25`() {
        assertEquals(25, UploadConsent.MAX_ITEMS)
        assertEquals(25, UploadConsent.create((1..25).map(::candidate)).items.size)
        assertThrows(IllegalArgumentException::class.java) {
            UploadConsent.create((1..26).map(::candidate))
        }
    }

    @Test
    fun `consent refuses an empty selection`() {
        assertThrows(IllegalArgumentException::class.java) { UploadConsent.create(emptyList()) }
    }
}

package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.scan.BulkCheckPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die beiden Regeln des Sammellaufs: System-Apps werden nie mitgeprüft, und das
 * Tageskontingent ist die harte Obergrenze.
 */
class BulkCheckPlanTest {

    private fun app(
        packageName: String,
        isSystem: Boolean = false,
        versionCode: Long = 1L,
        apkSize: Long = 1_000L
    ) = InstalledApp(
        name = packageName,
        packageName = packageName,
        icon = null,
        apkFile = File("/does/not/exist/$packageName.apk"),
        isSystem = isSystem,
        versionCode = versionCode,
        apkSize = apkSize
    )

    private fun record(
        packageName: String,
        versionCode: Long = 1L,
        apkSize: Long = 1_000L
    ) = AppScanRecord(
        packageName = packageName,
        sha256 = "a".repeat(64),
        versionCode = versionCode,
        apkSize = apkSize,
        checkedAt = 0L,
        outcome = AppScanOutcome.KNOWN
    )

    @Test
    fun `system apps are never part of a bulk check`() {
        val apps = listOf(app("com.user.one"), app("com.system.one", isSystem = true))

        val pending = BulkCheckPlan.pending(apps, emptyMap())

        assertEquals(listOf("com.user.one"), pending.map { it.packageName })
    }

    @Test
    fun `an app with a current result is skipped`() {
        val apps = listOf(app("com.user.one"))
        val known = mapOf("com.user.one" to record("com.user.one"))

        assertTrue(BulkCheckPlan.pending(apps, known).isEmpty())
    }

    @Test
    fun `an update makes the app pending again`() {
        val apps = listOf(app("com.user.one", versionCode = 2L, apkSize = 1_000L))
        val known = mapOf("com.user.one" to record("com.user.one", versionCode = 1L))

        assertEquals(1, BulkCheckPlan.pending(apps, known).size)
    }

    @Test
    fun `a changed apk size makes the app pending again`() {
        // Gleiche Versionsnummer, andere Datei: der gemerkte Hash passt nicht mehr.
        val apps = listOf(app("com.user.one", versionCode = 1L, apkSize = 2_000L))
        val known = mapOf("com.user.one" to record("com.user.one", apkSize = 1_000L))

        assertEquals(1, BulkCheckPlan.pending(apps, known).size)
    }

    @Test
    fun `a run never takes more than its own limit`() {
        assertEquals(ApiBudgetRepository.MAX_PER_RUN, BulkCheckPlan.allowance(usedToday = 0))
    }

    @Test
    fun `the daily budget caps the run`() {
        val used = ApiBudgetRepository.DAILY_LIMIT - 7
        assertEquals(7, BulkCheckPlan.allowance(used))
    }

    @Test
    fun `an exhausted budget allows nothing`() {
        assertEquals(0, BulkCheckPlan.allowance(ApiBudgetRepository.DAILY_LIMIT))
        assertEquals(0, BulkCheckPlan.allowance(ApiBudgetRepository.DAILY_LIMIT + 50))
    }

    @Test
    fun `the pace stays inside the free tier`() {
        // 4 Anfragen pro Minute sind erlaubt; der Abstand muss darüber liegen.
        val minSafeGap = 60_000L / ApiBudgetRepository.PER_MINUTE_LIMIT
        assertTrue(
            "gap of ${ApiBudgetRepository.MIN_INTERVAL_MS} ms is too tight",
            ApiBudgetRepository.MIN_INTERVAL_MS > minSafeGap
        )
    }
}

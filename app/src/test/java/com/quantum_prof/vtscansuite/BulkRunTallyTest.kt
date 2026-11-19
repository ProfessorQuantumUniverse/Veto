package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.scan.BulkCheckState
import com.quantum_prof.vtscansuite.scan.BulkCheckText
import com.quantum_prof.vtscansuite.scan.BulkRunTally
import com.quantum_prof.vtscansuite.scan.CheckOutcome
import com.quantum_prof.vtscansuite.scan.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkRunTallyTest {

    @Test
    fun `nothing done at the start`() {
        val running = BulkRunTally(total = 4, queued = 0).running("A")
        assertEquals(0, running.done)
        assertEquals(0f, running.fraction, 0f)
        assertEquals(1, running.position)
    }

    @Test
    fun `failed and unknown items count as done, rate limit does not`() {
        val tally = BulkRunTally(total = 5, queued = 0)
        tally.record(CheckOutcome.KNOWN)
        tally.record(CheckOutcome.FLAGGED)
        tally.record(CheckOutcome.UNKNOWN)
        tally.record(CheckOutcome.FAILED)
        tally.record(CheckOutcome.RATE_LIMITED)
        assertEquals(4, tally.done)
        assertEquals(3, tally.checked)
        assertEquals(1, tally.flagged)
        assertEquals(1, tally.unknown)
        assertEquals(1, tally.failed)
    }

    @Test
    fun `fraction stays between zero and one and position never exceeds total`() {
        val over = BulkCheckState.Running(done = 12, total = 10, currentApp = "x")
        assertEquals(1f, over.fraction, 0f)
        assertEquals(10, over.position)
        val empty = BulkCheckState.Running(done = 0, total = 0, currentApp = "")
        assertEquals(0f, empty.fraction, 0f)
    }

    @Test
    fun `a running snapshot is never complete before the last item finished`() {
        val tally = BulkRunTally(total = 3, queued = 0)
        tally.record(CheckOutcome.KNOWN)
        tally.record(CheckOutcome.KNOWN)
        assertTrue(tally.running("last").fraction < 1f)
    }

    @Test
    fun `finished after a rate limit keeps the open items and the queued ones`() {
        val tally = BulkRunTally(total = 5, queued = 7)
        tally.record(CheckOutcome.KNOWN)
        tally.record(CheckOutcome.UNKNOWN)
        tally.record(CheckOutcome.RATE_LIMITED)
        val finished = tally.finished(StopReason.RATE_LIMITED)
        assertEquals(2, finished.checked)
        assertEquals(1, finished.unknown)
        // 3 of this run are still open (the rate-limited one included) plus 7 queued.
        assertEquals(10, finished.remainingApps)
        assertEquals(StopReason.RATE_LIMITED, finished.reason)
    }

    @Test
    fun `cancelling keeps the real counters`() {
        val tally = BulkRunTally(total = 10, queued = 0)
        repeat(3) { tally.record(CheckOutcome.KNOWN) }
        tally.record(CheckOutcome.FLAGGED)
        tally.record(CheckOutcome.FAILED)
        val finished = tally.running("in flight").toFinished(StopReason.CANCELLED)
        assertEquals(4, finished.checked)
        assertEquals(1, finished.flagged)
        assertEquals(1, finished.failed)
        assertEquals(5, finished.remainingApps)
        assertEquals(StopReason.CANCELLED, finished.reason)
    }

    @Test
    fun `a completed run has nothing left`() {
        val tally = BulkRunTally(total = 2, queued = 0)
        tally.record(CheckOutcome.KNOWN)
        tally.record(CheckOutcome.FAILED)
        assertEquals(0, tally.finished(StopReason.COMPLETED).remainingApps)
    }

    @Test
    fun `progress text names the position and the queued apps`() {
        val text = BulkCheckText.progress(BulkCheckState.Running(2, 100, "x", queued = 40))
        assertEquals("Checking 3 of 100 (40 more after this run)", text)
        assertEquals(
            "Checking 1 of 3",
            BulkCheckText.progress(BulkCheckState.Running(0, 3, "x"))
        )
    }

    @Test
    fun `summary lists only non-zero parts`() {
        val summary = BulkCheckText.summary(
            BulkCheckState.Finished(
                checked = 5, unknown = 0, flagged = 1, failed = 0,
                remainingApps = 0, reason = StopReason.COMPLETED
            )
        )
        assertEquals("5 checked, 1 with detections.", summary)
    }
}

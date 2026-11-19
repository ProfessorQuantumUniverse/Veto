package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.scan.BulkWorkGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das gemeinsame Tor für Einzelscan, Hash-Prüfung und Upload-Stapel: wer es nicht bekommt,
 * erfährt warum, und zwischen zwei Arbeiten gilt der Mindestabstand der Anfragen.
 */
class BulkWorkGateTest {

    private class FakeTime {
        var now = 1_000_000L
        val slept = mutableListOf<Long>()
    }

    private fun gate(time: FakeTime) = BulkWorkGate().apply {
        clock = { time.now }
        sleeper = { time.slept += it; time.now += it }
    }

    @Test
    fun `a single scan cannot start while a hash check or an upload holds the gate`() {
        val g = BulkWorkGate()
        assertTrue(g.tryAcquire(BulkWorkGate.HASH_CHECK))
        assertFalse(g.tryAcquire(BulkWorkGate.SINGLE_SCAN))
        g.release(BulkWorkGate.HASH_CHECK)
        assertTrue(g.tryAcquire(BulkWorkGate.UPLOAD))
        assertFalse(g.tryAcquire(BulkWorkGate.SINGLE_SCAN))
        g.release(BulkWorkGate.UPLOAD)
        assertTrue(g.tryAcquire(BulkWorkGate.SINGLE_SCAN))
    }

    @Test
    fun `bulk work cannot start while a single scan holds the gate`() {
        val g = BulkWorkGate()
        assertTrue(g.tryAcquire(BulkWorkGate.SINGLE_SCAN))
        assertFalse(g.tryAcquire(BulkWorkGate.HASH_CHECK))
        assertFalse(g.tryAcquire(BulkWorkGate.UPLOAD))
    }

    @Test
    fun `the busy message names who holds the gate`() {
        val g = BulkWorkGate()
        g.tryAcquire(BulkWorkGate.HASH_CHECK)
        assertTrue(g.describeBusy().contains("hash check"))
        g.release(BulkWorkGate.HASH_CHECK)
        g.tryAcquire(BulkWorkGate.UPLOAD)
        assertTrue(g.describeBusy().contains("upload"))
        g.release(BulkWorkGate.UPLOAD)
        g.tryAcquire(BulkWorkGate.SINGLE_SCAN)
        assertTrue(g.describeBusy().contains("scan"))
    }

    @Test
    fun `a release by someone else does not free the gate`() {
        val g = BulkWorkGate()
        g.tryAcquire(BulkWorkGate.SINGLE_SCAN)
        g.release(BulkWorkGate.UPLOAD)
        assertEquals(BulkWorkGate.SINGLE_SCAN, g.currentOwner)
    }

    @Test
    fun `the next operation waits the remaining request interval after the last release`() = runBlocking {
        val time = FakeTime()
        val g = gate(time)
        g.tryAcquire(BulkWorkGate.HASH_CHECK)
        g.release(BulkWorkGate.HASH_CHECK)
        time.now += 6_000
        g.tryAcquire(BulkWorkGate.SINGLE_SCAN)
        g.awaitSpacing(16_000)
        assertEquals(listOf(10_000L), time.slept)
    }

    @Test
    fun `no waiting before the very first operation or after the interval has passed`() = runBlocking {
        val time = FakeTime()
        val g = gate(time)
        g.awaitSpacing(16_000)
        assertTrue(time.slept.isEmpty())
        g.tryAcquire(BulkWorkGate.UPLOAD)
        g.release(BulkWorkGate.UPLOAD)
        time.now += 20_000
        g.awaitSpacing(16_000)
        assertTrue(time.slept.isEmpty())
    }

    @Test
    fun `a failed release does not reset the spacing clock`() = runBlocking {
        val time = FakeTime()
        val g = gate(time)
        g.tryAcquire(BulkWorkGate.HASH_CHECK)
        g.release(BulkWorkGate.HASH_CHECK)
        time.now += 15_000
        g.release(BulkWorkGate.UPLOAD) // gehört ihm nicht
        g.awaitSpacing(16_000)
        assertEquals(listOf(1_000L), time.slept)
    }
}
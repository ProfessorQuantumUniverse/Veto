package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.scan.BulkCheckText
import com.quantum_prof.vtscansuite.scan.BulkWorkGate
import com.quantum_prof.vtscansuite.scan.ConsentedItem
import com.quantum_prof.vtscansuite.scan.StopReason
import com.quantum_prof.vtscansuite.scan.UploadItem
import com.quantum_prof.vtscansuite.scan.UploadItemStatus
import com.quantum_prof.vtscansuite.scan.UploadQueueState
import com.quantum_prof.vtscansuite.scan.UploadStopReason
import com.quantum_prof.vtscansuite.scan.UploadText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class UploadTextAndGateTest {

    private fun item(status: UploadItemStatus) =
        UploadItem(ConsentedItem("p", "p", "1", 1, 10, "h"), status)

    private val banned = listOf("safe", "clean", "harmless", "scanned", "analysed", "analyzed", "verified")

    @Test
    fun `texts never claim an analysis, a verdict or safety`() {
        val states = UploadStopReason.entries.map { reason ->
            UploadQueueState.Finished(
                listOf(
                    item(UploadItemStatus.SUBMITTED), item(UploadItemStatus.UNCERTAIN),
                    item(UploadItemStatus.FAILED), item(UploadItemStatus.SKIPPED),
                    item(UploadItemStatus.CANCELLED), item(UploadItemStatus.PENDING)
                ),
                reason
            )
        }
        for (state in states) {
            val text = (UploadText.title(state.reason) + " " + UploadText.summary(state)).lowercase()
            for (word in banned) assertFalse("'$word' in: $text", text.contains(word))
        }
    }

    @Test
    fun `a submitted file is described as submitted with no verdict yet`() {
        val summary = UploadText.summary(
            UploadQueueState.Finished(listOf(item(UploadItemStatus.SUBMITTED)), UploadStopReason.COMPLETED)
        )
        assertTrue(summary.startsWith("1 submitted to VirusTotal"))
        assertTrue(summary.contains("No verdict yet"))
    }

    @Test
    fun `nothing submitted means no promise of a later result`() {
        val summary = UploadText.summary(
            UploadQueueState.Finished(listOf(item(UploadItemStatus.CANCELLED)), UploadStopReason.CANCELLED)
        )
        assertFalse(summary.contains("No verdict yet"))
    }

    @Test
    fun `running text counts resolved items and never exceeds the total`() {
        val running = UploadQueueState.Running(
            listOf(item(UploadItemStatus.SUBMITTED), item(UploadItemStatus.UPLOADING), item(UploadItemStatus.PENDING))
        )
        assertEquals("Submitting 2 of 3", UploadText.progress(running))
        val done = UploadQueueState.Running(listOf(item(UploadItemStatus.SUBMITTED)))
        assertEquals("Submitting 1 of 1", UploadText.progress(done))
    }

    @Test
    fun `the unexpected error reason has its own title in both flows`() {
        assertEquals("Upload stopped unexpectedly", UploadText.title(UploadStopReason.ERROR))
        assertEquals("App check stopped unexpectedly", BulkCheckText.title(StopReason.ERROR))
    }

    @Test
    fun `the gate lets one owner work at a time and only the owner can release it`() {
        val gate = BulkWorkGate()
        assertTrue(gate.tryAcquire(BulkWorkGate.UPLOAD))
        assertFalse(gate.tryAcquire(BulkWorkGate.HASH_CHECK))
        gate.release(BulkWorkGate.HASH_CHECK) // gehört ihm nicht: ohne Wirkung
        assertTrue(gate.isBusy)
        gate.release(BulkWorkGate.UPLOAD)
        assertFalse(gate.isBusy)
        assertTrue(gate.tryAcquire(BulkWorkGate.HASH_CHECK))
    }

    @Test
    fun `simultaneous starts produce exactly one winner`() {
        val gate = BulkWorkGate()
        val winners = AtomicInteger(0)
        val threads = 16
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) { i ->
            pool.execute {
                ready.countDown()
                go.await()
                val who = if (i % 2 == 0) BulkWorkGate.UPLOAD else BulkWorkGate.HASH_CHECK
                if (gate.tryAcquire(who)) winners.incrementAndGet()
            }
        }
        ready.await()
        go.countDown()
        pool.shutdown()
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(1, winners.get())
    }
}
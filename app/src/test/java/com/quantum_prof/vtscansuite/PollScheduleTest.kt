package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.remote.PollSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Kontingent des Free-Tiers (4 Anfragen/Minute, 500/Tag) ist das knappe Gut. Diese
 * Tests halten fest, dass ein einzelner Scan davon nur einen kleinen Teil verbraucht.
 */
class PollScheduleTest {

    @Test
    fun `the gap grows and then stays at the maximum`() {
        val schedule = PollSchedule(initialMs = 15_000, maxMs = 75_000)
        val delays = (1..8).map { schedule.nextDelayMs() }

        assertEquals(15_000L, delays.first())
        assertTrue("must never shrink: $delays", delays.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("must stay capped: $delays", delays.all { it <= 75_000L })
        assertEquals(75_000L, delays.last())
    }

    @Test
    fun `an initial value above the maximum is capped`() {
        val schedule = PollSchedule(initialMs = 90_000, maxMs = 30_000)
        assertEquals(30_000L, schedule.nextDelayMs())
        assertEquals(30_000L, schedule.nextDelayMs())
    }

    @Test
    fun `a six minute analysis costs a third of the old fixed poll`() {
        val budgetMs = 6 * 60_000L
        val schedule = PollSchedule(initialMs = 15_000, maxMs = 75_000)

        var elapsed = 0L
        var requests = 0
        while (elapsed < budgetMs) {
            elapsed += schedule.nextDelayMs()
            requests++
        }

        // Vorher: fester 15-Sekunden-Takt, also 24 Statusabfragen pro Scan.
        assertTrue("$requests requests is still too many", requests <= 8)
    }

    @Test
    fun `reloading the report stays within a handful of requests`() {
        val schedule = PollSchedule(initialMs = 5_000, maxMs = 30_000, factor = 1.8)
        val delays = (1..5).map { schedule.nextDelayMs() }

        // Fünf Versuche decken rund 90 Sekunden ab, in denen VirusTotal fertig werden kann.
        assertTrue("covers too little time: $delays", delays.sum() >= 80_000L)
        assertTrue("waits too long overall: $delays", delays.sum() <= 120_000L)
    }
}

package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.remote.VtUploadPath
import com.quantum_prof.vtscansuite.data.repository.RequestBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RequestBudgetTest {

    private fun budget(limit: Int, used: Int) = RequestBudget(limit) { used }

    @Test
    fun `reserving fails when the request would exceed the limit`() = runBlocking {
        val b = budget(limit = 10, used = 9)
        assertNull(b.tryReserve(2))
        assertNotNull(b.tryReserve(1))
    }

    @Test
    fun `open reservations count against what is available`() = runBlocking {
        val b = budget(limit = 10, used = 6)
        val first = b.tryReserve(3)
        assertNotNull(first)
        assertEquals(1, b.available())
        assertNull(b.tryReserve(2))
        first!!.release()
        assertEquals(4, b.available())
    }

    @Test
    fun `release is idempotent`() = runBlocking {
        val b = budget(limit = 5, used = 0)
        val r = b.tryReserve(5)!!
        r.release()
        r.release()
        assertEquals(5, b.available())
    }

    @Test
    fun `requests not yet persisted still count`() = runBlocking {
        val b = budget(limit = 10, used = 8)
        b.requestStarted()
        b.requestStarted()
        assertEquals(10, b.used())
        assertNull(b.tryReserve(1))
        b.requestPersisted()
        b.requestPersisted()
    }

    @Test
    fun `concurrent reservations never overspend`() = runBlocking {
        val b = budget(limit = 20, used = 5)
        val granted = AtomicInteger(0)
        (1..100).map {
            async(Dispatchers.Default) {
                if (b.tryReserve(1) != null) granted.incrementAndGet()
            }
        }.awaitAll()
        assertEquals(15, granted.get())
        assertEquals(0, b.available())
    }

    @Test
    fun `hash checks and uploads share one budget`() = runBlocking {
        // Der Hash-Lauf reserviert je 1, der Upload je 1 oder 2 (große Datei). Zusammen
        // dürfen sie das Limit nie überschreiten.
        val b = budget(limit = 12, used = 0)
        val spent = AtomicInteger(0)
        val jobs = (1..30).map { i ->
            async(Dispatchers.Default) {
                val need = if (i % 3 == 0) VtUploadPath.requestsFor(40L * 1024 * 1024) else 1
                if (b.tryReserve(need) != null) spent.addAndGet(need)
            }
        }
        jobs.awaitAll()
        assertTrue("spent ${spent.get()}", spent.get() <= 12)
        assertEquals(12 - spent.get(), b.available())
    }

    @Test
    fun `an exhausted budget reserves nothing`() = runBlocking {
        val b = budget(limit = 500, used = 500)
        assertNull(b.tryReserve(1))
        assertEquals(0, b.available())
    }

    @Test
    fun `upload path counts one request up to 32 MB and two above`() {
        val limit = 32L * 1024 * 1024
        assertEquals(1, VtUploadPath.requestsFor(1))
        assertEquals(1, VtUploadPath.requestsFor(limit))
        assertEquals(2, VtUploadPath.requestsFor(limit + 1))
        assertEquals(false, VtUploadPath.needsUploadUrl(limit))
        assertEquals(true, VtUploadPath.needsUploadUrl(limit + 1))
    }
}

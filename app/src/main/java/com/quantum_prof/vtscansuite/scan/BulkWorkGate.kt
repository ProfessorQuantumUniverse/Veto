// scan/BulkWorkGate.kt
package com.quantum_prof.vtscansuite.scan

import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lässt immer nur **eine** Arbeit zugleich an VirusTotal laufen: Einzelscan, Hash-Prüfung oder
 * Upload-Stapel. Alle hängen am Free-Tier mit vier Anfragen pro Minute; liefen sie
 * gleichzeitig, verdoppelte sich die Rate, und beide liefen ins Rate-Limit.
 *
 * Wer das Tor nicht bekommt, sagt es dem Nutzer ([describeBusy]) und kann es später
 * erneut versuchen; nichts wird stillschweigend verworfen.
 *
 * Zwischen zwei Arbeiten gilt derselbe Mindestabstand wie zwischen zwei Anfragen
 * ([awaitSpacing]), gemessen ab dem Moment, in dem die vorige das Tor freigab.
 */
@Singleton
class BulkWorkGate @Inject constructor() {
    private val owner = AtomicReference<String?>(null)

    @Volatile
    private var lastReleaseMillis = 0L

    /** Uhr und Wartefunktion, nur für Tests austauschbar. */
    internal var clock: () -> Long = System::currentTimeMillis
    internal var sleeper: suspend (Long) -> Unit = { delay(it) }

    /** True, wenn [who] jetzt arbeiten darf. */
    fun tryAcquire(who: String): Boolean = owner.compareAndSet(null, who)

    fun release(who: String) {
        if (owner.compareAndSet(who, null)) lastReleaseMillis = clock()
    }

    fun isHeldBy(who: String): Boolean = owner.get() == who

    val isBusy: Boolean get() = owner.get() != null

    /** Wer das Tor gerade hält, oder null. */
    val currentOwner: String? get() = owner.get()

    /**
     * Wartet, bis seit der letzten Freigabe der Mindestabstand zwischen zwei Anfragen
     * ([ApiBudgetRepository.MIN_INTERVAL_MS]) vergangen ist. Vor der ersten Anfrage einer
     * neuen Arbeit aufrufen, solange das Tor gehalten wird.
     */
    suspend fun awaitSpacing(minIntervalMillis: Long = ApiBudgetRepository.MIN_INTERVAL_MS) {
        val wait = lastReleaseMillis + minIntervalMillis - clock()
        if (lastReleaseMillis > 0 && wait > 0) sleeper(wait.coerceAtMost(minIntervalMillis))
    }

    /** Ein Satz für die Oberfläche: was gerade läuft und was der Nutzer tun kann. */
    fun describeBusy(): String = when (owner.get()) {
        HASH_CHECK -> "An app hash check is running. Stop it or wait until it finishes, then try again."
        UPLOAD -> "An upload batch is running. Stop it or wait until it finishes, then try again."
        SINGLE_SCAN -> "Another scan is running. Wait until it finishes, then try again."
        else -> "Another VirusTotal operation is running. Try again in a moment."
    }

    companion object {
        const val HASH_CHECK = "hash-check"
        const val UPLOAD = "upload"
        const val SINGLE_SCAN = "single-scan"
    }
}
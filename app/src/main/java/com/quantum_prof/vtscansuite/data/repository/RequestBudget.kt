// data/repository/RequestBudget.kt
package com.quantum_prof.vtscansuite.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Die Rechenlogik hinter dem gemeinsamen Tageskontingent, ohne Android und ohne Speicher,
 * damit sie testbar ist. [ApiBudgetRepository] hängt den echten Zähler (DataStore) an.
 *
 * Bisher zählte [ApiBudgetRepository] nur nachträglich: der Netzwerk-Interceptor schreibt
 * jede abgesendete Anfrage, ohne vorher zu fragen. Zwei Abläufe zugleich (Hash-Prüfung und
 * Upload) lesen dann denselben Stand und geben das Kontingent doppelt aus. Dieser Teil
 * ergänzt zweierlei:
 *
 * - **Noch nicht gespeicherte Anfragen** ([requestStarted] / [requestPersisted]): der
 *   Schreibvorgang läuft asynchron, bis er fertig ist, fehlt die Anfrage im gespeicherten
 *   Zähler. Sie wird deshalb im Speicher mitgezählt. Gelesen wird erst der Merker, dann der
 *   Speicher; so kann eine Anfrage kurz doppelt, aber nie gar nicht gezählt sein.
 * - **Reservierungen** ([tryReserve]): wer mehrere Anfragen braucht, reserviert sie vorab.
 *   Die Prüfung läuft unter einer Sperre, so kann das Kontingent nicht überbucht werden.
 *   Solange die Reservierung offen ist, zählt eine schon gesendete Anfrage zweimal. Das ist
 *   Absicht: zu vorsichtig ist hier die sichere Richtung.
 *
 * Es bleibt eine lokale Schätzung: Anfragen anderer Geräte mit demselben Schlüssel und
 * interne Wiederholungen von OkHttp (Verbindungsfehler) sieht Veto nicht.
 */
class RequestBudget(
    private val limit: Int,
    private val persistedUsed: suspend () -> Int
) {
    private val unpersisted = AtomicInteger(0)
    private val reserved = AtomicInteger(0)
    private val mutex = Mutex()

    /** Eine Anfrage geht gerade raus; ihr Schreibvorgang ist noch offen. */
    fun requestStarted() {
        unpersisted.incrementAndGet()
    }

    /** Der Schreibvorgang zu einer Anfrage ist abgeschlossen. */
    fun requestPersisted() {
        unpersisted.decrementAndGet()
    }

    /** Bisher verbrauchte Anfragen, einschließlich der noch nicht gespeicherten. */
    suspend fun used(): Int {
        val inFlight = unpersisted.get() // zuerst den Merker, dann den Speicher
        return persistedUsed() + inFlight
    }

    /** Was noch frei ist: Limit minus Verbrauch minus offene Reservierungen. Nie negativ. */
    suspend fun available(): Int = (limit - used() - reserved.get()).coerceAtLeast(0)

    /**
     * Reserviert [requests] Anfragen oder liefert null, wenn das Kontingent dafür nicht
     * mehr reicht. Die Reservierung muss mit [Reservation.release] zurückgegeben werden,
     * sobald die Anfragen abgesendet (oder verworfen) sind.
     */
    suspend fun tryReserve(requests: Int): Reservation? {
        require(requests > 0) { "requests must be positive" }
        return mutex.withLock {
            if (available() < requests) {
                null
            } else {
                reserved.addAndGet(requests)
                Reservation(requests) { reserved.addAndGet(-it) }
            }
        }
    }

    /** Eine offene Reservierung. [release] ist mehrfach aufrufbar und gibt nur einmal frei. */
    class Reservation internal constructor(
        val requests: Int,
        private val onRelease: (Int) -> Unit
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) onRelease(requests)
        }
    }
}

/** Wer Anfragen vom gemeinsamen Tageskontingent reservieren kann. Trennt das Repository vom Speicher. */
fun interface BudgetReserver {
    /** Reserviert [requests] Anfragen oder liefert null, wenn das Kontingent nicht reicht. */
    suspend fun tryReserve(requests: Int): RequestBudget.Reservation?
}

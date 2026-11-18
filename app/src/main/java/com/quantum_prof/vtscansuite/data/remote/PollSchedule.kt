// data/remote/PollSchedule.kt
package com.quantum_prof.vtscansuite.data.remote

/**
 * Wartezeiten für das Nachfragen bei VirusTotal: der Abstand wächst mit jedem Versuch und
 * bleibt dann beim Maximum stehen.
 *
 * Hintergrund: ein fester Takt verbrennt Kontingent. Das Free-Tier erlaubt 4 Anfragen pro
 * Minute und 500 pro Tag – mit 15-Sekunden-Takten kostete ein einzelner Upload-Scan bis zu
 * 24 Statusabfragen, also fast 5 % des Tagesbudgets. Mit wachsendem Abstand sind es bei
 * gleichem Zeitfenster rund ein Drittel davon, ohne dass ein schnell fertiges Ergebnis
 * später erkannt wird (der erste Abstand bleibt gleich).
 */
class PollSchedule(
    initialMs: Long,
    maxMs: Long,
    factor: Double = 1.6
) {
    private val delays = generateSequence(initialMs.coerceAtMost(maxMs)) { previous ->
        (previous * factor).toLong().coerceAtMost(maxMs)
    }.iterator()

    /** Wartezeit vor der nächsten Anfrage. */
    fun nextDelayMs(): Long = delays.next()
}

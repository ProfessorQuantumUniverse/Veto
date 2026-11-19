// scan/BulkRunTally.kt
package com.quantum_prof.vtscansuite.scan

/** Ergebnis einer einzelnen Hash-Prüfung. */
/** [BUDGET_EXHAUSTED]: das lokale Tageskontingent war aufgebraucht, die Anfrage ging nicht raus. */
internal enum class CheckOutcome { KNOWN, FLAGGED, UNKNOWN, RATE_LIMITED, BUDGET_EXHAUSTED, FAILED }

/**
 * Die Buchführung eines Sammellaufs, frei von Android und Netzwerk, damit sie testbar ist.
 *
 * Eine App ist **erledigt**, sobald ihre Prüfung ein Ergebnis hat oder endgültig
 * fehlgeschlagen ist. Ein Rate-Limit erledigt sie nicht: die App bleibt offen und wird
 * beim nächsten Lauf erneut versucht. Der Nenner [total] steht zu Beginn fest.
 */
internal class BulkRunTally(val total: Int, val queued: Int) {
    var checked = 0
        private set
    var unknown = 0
        private set
    var flagged = 0
        private set
    var failed = 0
        private set

    val done: Int get() = checked + failed

    fun record(outcome: CheckOutcome) {
        when (outcome) {
            CheckOutcome.KNOWN -> checked++
            CheckOutcome.FLAGGED -> {
                checked++
                flagged++
            }

            CheckOutcome.UNKNOWN -> {
                checked++
                unknown++
            }

            CheckOutcome.FAILED -> failed++
            CheckOutcome.RATE_LIMITED, CheckOutcome.BUDGET_EXHAUSTED -> Unit
        }
    }

    fun running(currentApp: String) = BulkCheckState.Running(
        done = done,
        total = total,
        currentApp = currentApp,
        checked = checked,
        unknown = unknown,
        flagged = flagged,
        failed = failed,
        queued = queued
    )

    fun finished(reason: StopReason): BulkCheckState.Finished = running("").toFinished(reason)
}

/** Die Texte für Benachrichtigung und Abschluss, an einer Stelle und ohne Android. */
internal object BulkCheckText {

    /** "Checking 3 of 100", dazu bei weiteren Läufen, wie viele danach noch warten. */
    fun progress(state: BulkCheckState.Running): String = buildString {
        append("Checking ${state.position} of ${state.total}")
        if (state.queued > 0) append(" (${state.queued} more after this run)")
    }

    fun title(reason: StopReason): String = when (reason) {
        StopReason.COMPLETED -> "App check complete"
        StopReason.RUN_LIMIT -> "App check paused"
        StopReason.DAILY_LIMIT -> "Daily request limit reached"
        StopReason.RATE_LIMITED -> "VirusTotal asked us to slow down"
        StopReason.NO_API_KEY -> "No API key saved"
        StopReason.CANCELLED -> "App check stopped"
        StopReason.ERROR -> "App check stopped unexpectedly"
        StopReason.BUSY -> "Another VirusTotal operation is running"
    }

    fun summary(state: BulkCheckState.Finished): String = buildString {
        append("${state.checked} checked")
        if (state.unknown > 0) append(", ${state.unknown} unknown to VirusTotal")
        if (state.flagged > 0) append(", ${state.flagged} with detections")
        if (state.failed > 0) append(", ${state.failed} could not be read")
        if (state.remainingApps > 0) append(". ${state.remainingApps} left")
        append(".")
    }
}
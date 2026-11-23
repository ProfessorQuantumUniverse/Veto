// scan/UploadText.kt
package com.quantum_prof.vtscansuite.scan

/** Die Texte für Benachrichtigung und Abschluss des Upload-Stapels, ohne Android. */
internal object UploadText {

    /** Während des Laufs. Keine App-Namen: die Meldung kann auf dem Sperrbildschirm stehen. */
    fun progress(state: UploadSnapshot): String =
        "Submitting ${(state.resolved + 1).coerceAtMost(state.total)} of ${state.total}"

    fun title(reason: UploadStopReason): String = when (reason) {
        UploadStopReason.COMPLETED -> "Upload finished"
        UploadStopReason.DAILY_LIMIT -> "Upload paused: daily request limit"
        UploadStopReason.RATE_LIMITED -> "Upload paused: VirusTotal asked us to slow down"
        UploadStopReason.AUTH_FAILED -> "Upload stopped: API key rejected"
        UploadStopReason.NO_API_KEY -> "No API key saved"
        UploadStopReason.CANCELLED -> "Upload stopped"
        UploadStopReason.ERROR -> "Upload stopped unexpectedly"
        UploadStopReason.BUSY -> "Waiting: another VirusTotal operation is running"
    }

    /**
     * Sagt, was geschehen ist, und ausdrücklich, was noch nicht: "abgeschickt" ist weder eine
     * fertige Analyse noch ein Urteil.
     */
    fun summary(state: UploadSnapshot): String = buildString {
        append("${state.submitted} submitted to VirusTotal")
        if (state.uncertain > 0) append(", ${state.uncertain} unclear whether they arrived")
        if (state.failed > 0) append(", ${state.failed} failed")
        if (state.skipped > 0) append(", ${state.skipped} skipped")
        if (state.cancelled > 0) append(", ${state.cancelled} cancelled")
        if (state.pending > 0) append(", ${state.pending} not uploaded")
        append(".")
        if (state.submitted > 0 || state.uncertain > 0) {
            append(" No verdict yet: run the app check again later to fetch the results.")
        }
    }
}
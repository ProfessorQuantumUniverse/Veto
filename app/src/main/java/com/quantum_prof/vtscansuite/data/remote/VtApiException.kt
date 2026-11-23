// data/remote/VtApiException.kt
package com.quantum_prof.vtscansuite.data.remote

/**
 * Wird geworfen, wenn die VirusTotal-API mit HTTP 429 antwortet (Rate-Limit erreicht).
 * Das Free-Tier erlaubt 4 Anfragen/Minute und 500/Tag. Die UI zeigt dafür einen
 * eigenen, freundlichen Hinweis statt einer generischen Fehlermeldung.
 */
class RateLimitException(
    /** Wartezeit aus dem "Retry-After"-Header, falls VirusTotal eine genannt hat. */
    val retryAfterSeconds: Long? = null
) : Exception(
    "Rate limit reached. The free VirusTotal API allows 4 requests per minute and 500 per day. " +
        "Please wait about a minute and try again."
)

/**
 * Fehlerantwort der VirusTotal-API mit erhaltenem HTTP-Status.
 *
 * Der Code muss erhalten bleiben, weil nur ein 404 ("NotFoundError") bedeutet, dass
 * VirusTotal die Datei wirklich nicht kennt. Vorher wurde jeder Fehlschlag gleich
 * behandelt, wodurch z. B. ein Rate-Limit wie "unbekannte Datei" aussah – und einen
 * Upload auslöste (siehe [com.quantum_prof.vtscansuite.scan.UploadGate]).
 */
open class VtHttpException(val code: Int, message: String) : Exception(message)

/**
 * Der Upload ist gescheitert, **bevor** ein Byte der Datei gesendet wurde (etwa weil die
 * Upload-Adresse nicht zu bekommen war). Es ist also sicher, dass nichts angekommen ist.
 */
class UploadNotStartedException(cause: Throwable) : Exception(cause.message, cause)

/**
 * Veto hat das lokale Tageskontingent aufgebraucht und sendet deshalb nichts mehr. Das ist eine
 * Schätzung von Veto, keine Auskunft von VirusTotal: Anfragen anderer Geräte mit demselben
 * Schlüssel sieht Veto nicht. Es ist **vor** dem Senden entschieden worden; die betroffene
 * Anfrage ist nicht rausgegangen.
 */
class BudgetExhaustedException(
    /** True, wenn die Datei schon eingereicht war und nur noch Folgeabfragen fehlen. */
    val afterSubmission: Boolean = false
) : Exception(
    if (afterSubmission) {
        "The file was submitted, but Veto's daily request budget is used up, so the result can't be fetched now. " +
            "Check again after midnight UTC."
    } else {
        "Veto's daily request budget is used up, so nothing was sent. It resets at midnight UTC."
    }
)

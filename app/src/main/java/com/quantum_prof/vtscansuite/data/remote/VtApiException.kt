// data/remote/VtApiException.kt
package com.quantum_prof.vtscansuite.data.remote

/**
 * Wird geworfen, wenn die VirusTotal-API mit HTTP 429 antwortet (Rate-Limit erreicht).
 * Das Free-Tier erlaubt 4 Anfragen/Minute und 500/Tag. Die UI zeigt dafür einen
 * eigenen, freundlichen Hinweis statt einer generischen Fehlermeldung.
 */
class RateLimitException : Exception(
    "Rate limit reached. The free VirusTotal API allows 4 requests per minute and 500 per day. " +
        "Please wait about a minute and try again."
)

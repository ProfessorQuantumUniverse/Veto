// data/remote/VtRequestCounter.kt
package com.quantum_prof.vtscansuite.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Zählt jede abgesendete VirusTotal-Anfrage, damit die App das Tageskontingent anzeigen
 * kann. Als Application-Interceptor registriert: ein Aufruf zählt einmal, auch wenn
 * OkHttp intern einen Redirect folgt oder die Verbindung wiederholt.
 */
class VtRequestCounter(
    private val onRequest: () -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(VtApiKeyGuard.HEADER) != null &&
            VtApiKeyGuard.isVirusTotalHttps(request.url)
        ) {
            onRequest()
        }
        return chain.proceed(request)
    }
}

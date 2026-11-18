// data/remote/VtApiKeyGuard.kt
package com.quantum_prof.vtscansuite.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Lässt den "x-apikey"-Header grundsätzlich nur an VirusTotal über HTTPS hinaus.
 *
 * Wichtig als *Network*-Interceptor registriert: so läuft die Prüfung auch bei jedem
 * Redirect-Hop. OkHttp entfernt bei einem Host-Wechsel nur den "Authorization"-Header,
 * eigene Header wie "x-apikey" wandern mit – ein Redirect könnte den Schlüssel des
 * Nutzers also an einen fremden Host tragen. Betrifft vor allem [VTScanApiService.uploadFileToUrl],
 * dessen Ziel-URL aus einer API-Antwort stammt und damit nicht von uns kontrolliert wird.
 */
class VtApiKeyGuard : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (blocks(request)) {
            // Fehlertext ohne URL und ohne Schlüssel – er landet in der UI.
            throw IOException("Blocked: the API key is only ever sent to virustotal.com over HTTPS.")
        }
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "x-apikey"

        /** True, sobald eine Anfrage den Schlüssel an ein nicht erlaubtes Ziel tragen würde. */
        fun blocks(request: Request): Boolean =
            request.header(HEADER) != null && !isVirusTotalHttps(request.url)

        /** Nur virustotal.com und echte Subdomains, ausschließlich über HTTPS. */
        fun isVirusTotalHttps(url: HttpUrl): Boolean =
            url.isHttps && (url.host == "virustotal.com" || url.host.endsWith(".virustotal.com"))

        /** Variante für URLs, die als String aus einer API-Antwort kommen. */
        fun isVirusTotalHttps(url: String): Boolean =
            url.toHttpUrlOrNull()?.let { isVirusTotalHttps(it) } == true
    }
}

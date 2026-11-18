package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.remote.VtApiKeyGuard
import com.quantum_prof.vtscansuite.data.remote.VtHttpException
import com.quantum_prof.vtscansuite.scan.UploadGate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Zwei Regeln, die nie wieder kaputtgehen dürfen:
 *
 * 1. Eine Datei wird nur hochgeladen, wenn VirusTotal sie nachweislich nicht kennt.
 * 2. Der API-Schlüssel verlässt das Gerät nur in Richtung virustotal.com über HTTPS.
 */
class UploadSafetyTest {

    // ---- 1. Upload nur bei verifiziertem "nicht gefunden" ----

    @Test
    fun `only a verified 404 counts as unknown`() {
        assertTrue(UploadGate.isVerifiedUnknown(VtHttpException(404, "not found")))
    }

    @Test
    fun `api failures are never treated as unknown`() {
        // Genau das war der Fehler: jeder Fehlschlag sah wie "unbekannte Datei" aus.
        assertFalse(UploadGate.isVerifiedUnknown(RateLimitException()))
        assertFalse(UploadGate.isVerifiedUnknown(VtHttpException(429, "rate limited")))
        assertFalse(UploadGate.isVerifiedUnknown(VtHttpException(401, "bad key")))
        assertFalse(UploadGate.isVerifiedUnknown(VtHttpException(403, "forbidden")))
        assertFalse(UploadGate.isVerifiedUnknown(VtHttpException(500, "server error")))
        assertFalse(UploadGate.isVerifiedUnknown(VtHttpException(503, "unavailable")))
        assertFalse(UploadGate.isVerifiedUnknown(IOException("offline")))
        assertFalse(UploadGate.isVerifiedUnknown(Exception("something odd")))
        assertFalse(UploadGate.isVerifiedUnknown(null))
    }

    // ---- 2. Der API-Schlüssel bleibt bei VirusTotal ----

    @Test
    fun `virustotal over https is allowed`() {
        assertTrue(VtApiKeyGuard.isVirusTotalHttps("https://www.virustotal.com/api/v3/files".toHttpUrl()))
        assertTrue(VtApiKeyGuard.isVirusTotalHttps("https://virustotal.com/api/v3/files".toHttpUrl()))
        assertTrue(VtApiKeyGuard.isVirusTotalHttps("https://www.virustotal.com/_ah/upload/abc"))
    }

    @Test
    fun `anything else is rejected`() {
        assertFalse(VtApiKeyGuard.isVirusTotalHttps("http://www.virustotal.com/api/v3/files".toHttpUrl()))
        assertFalse(VtApiKeyGuard.isVirusTotalHttps("https://example.com/upload".toHttpUrl()))
        // Lookalike-Hosts dürfen nicht durch einen naiven "contains"-Test schlüpfen.
        assertFalse(VtApiKeyGuard.isVirusTotalHttps("https://virustotal.com.example.com/upload".toHttpUrl()))
        assertFalse(VtApiKeyGuard.isVirusTotalHttps("https://notvirustotal.com/upload".toHttpUrl()))
        assertFalse(VtApiKeyGuard.isVirusTotalHttps("not a url"))
    }

    @Test
    fun `a request carrying the key to a foreign host is blocked`() {
        val foreign = Request.Builder()
            .url("https://example.com/_ah/upload/abc")
            .header(VtApiKeyGuard.HEADER, "secret")
            .build()
        assertTrue(VtApiKeyGuard.blocks(foreign))
    }

    @Test
    fun `requests without the key and requests to virustotal pass`() {
        val noKey = Request.Builder().url("https://example.com/anything").build()
        assertFalse(VtApiKeyGuard.blocks(noKey))

        val vt = Request.Builder()
            .url("https://www.virustotal.com/api/v3/files/abc")
            .header(VtApiKeyGuard.HEADER, "secret")
            .build()
        assertFalse(VtApiKeyGuard.blocks(vt))
    }
}

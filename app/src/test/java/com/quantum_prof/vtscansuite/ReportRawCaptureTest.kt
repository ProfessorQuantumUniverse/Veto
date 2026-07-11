package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stellt sicher, dass die Ergebnisseite im Detailed-Modus wirklich ALLES anzeigen kann:
 * das typisierte Modell bleibt funktionsfähig, und unbekannte Felder gehen weder beim
 * Parsen noch beim Speichern/Laden (gespeicherte Scans) verloren.
 */
class ReportRawCaptureTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val sample = """
        {"data":{"id":"abc","type":"file","attributes":{
            "sha256":"abc",
            "meaningful_name":"x.apk",
            "last_analysis_stats":{"malicious":1,"harmless":60},
            "packers":{"PEiD":"UPX"},
            "sigma_analysis_results":[{"rule_title":"suspicious behaviour"}],
            "capabilities_tags":["persistence","network"],
            "some_future_field":42
        }}}
    """.trimIndent()

    @Test
    fun typedModelStillParses() {
        val r = json.decodeFromString<FileReportResponse>(sample)
        assertEquals("abc", r.data.attributes.sha256)
        assertEquals("x.apk", r.data.attributes.meaningfulName)
        assertEquals(1, r.data.attributes.lastAnalysisStats.malicious)
    }

    @Test
    fun rawKeepsUnmappedFields() {
        val raw = json.decodeFromString<FileReportResponse>(sample).data.attributesRaw
        assertTrue(raw.containsKey("packers"))
        assertTrue(raw.containsKey("sigma_analysis_results"))
        assertTrue(raw.containsKey("capabilities_tags"))
        assertTrue(raw.containsKey("some_future_field"))
    }

    @Test
    fun savedScanRoundTripPreservesUnmappedFields() {
        val original = json.decodeFromString<FileReportResponse>(sample)
        val reloaded = json.decodeFromString<FileReportResponse>(json.encodeToString(original))
        assertEquals("abc", reloaded.data.attributes.sha256)
        assertTrue(reloaded.data.attributesRaw.containsKey("packers"))
        assertTrue(reloaded.data.attributesRaw.containsKey("some_future_field"))
    }
}

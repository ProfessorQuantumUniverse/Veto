// app/src/main/java/com/quantum_prof/vtscansuite/data/model/VirusTotalModels.kt
package com.quantum_prof.vtscansuite.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class FileReportResponse(
    val data: ReportData
)

@Serializable(with = ReportDataSerializer::class)
data class ReportData(
    val id: String, // SHA-256 Hash der Datei
    val type: String,
    val attributes: ReportAttributes,
    /**
     * Die vollständige, unveränderte `attributes`-JSON – enthält AUCH Felder, die das
     * typisierte [ReportAttributes]-Modell (noch) nicht kennt. So kann die Ergebnisseite
     * im Detailed-Modus wirklich ALLES anzeigen, was die API liefert.
     */
    val attributesRaw: JsonObject = JsonObject(emptyMap())
)

/**
 * Liest `attributes` genau einmal als [JsonObject] und leitet daraus sowohl das typisierte
 * [ReportAttributes] als auch das rohe Objekt ab. Beim Serialisieren (gespeicherte Scans)
 * wird das Roh-Objekt bevorzugt, damit auch dort nichts verloren geht.
 */
object ReportDataSerializer : KSerializer<ReportData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ReportData") {
        element<String>("id")
        element<String>("type")
        element("attributes", ReportAttributes.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): ReportData {
        val input = decoder as? JsonDecoder
            ?: error("ReportData is only deserializable from JSON")
        val root = input.decodeJsonElement().jsonObject
        val id = root["id"]?.jsonPrimitive?.content ?: ""
        val type = root["type"]?.jsonPrimitive?.content ?: ""
        val rawAttrs = root["attributes"] as? JsonObject ?: JsonObject(emptyMap())
        val typed = input.json.decodeFromJsonElement(ReportAttributes.serializer(), rawAttrs)
        return ReportData(id, type, typed, rawAttrs)
    }

    override fun serialize(encoder: Encoder, value: ReportData) {
        val output = encoder as? JsonEncoder
            ?: error("ReportData is only serializable to JSON")
        val attrs: JsonElement = if (value.attributesRaw.isNotEmpty()) {
            value.attributesRaw
        } else {
            output.json.encodeToJsonElement(ReportAttributes.serializer(), value.attributes)
        }
        output.encodeJsonElement(
            buildJsonObject {
                put("id", value.id)
                put("type", value.type)
                put("attributes", attrs)
            }
        )
    }
}

/**
 * Vollständige Abbildung der von der VirusTotal /files Schnittstelle gelieferten Attribute.
 *
 * WICHTIG: Jedes Feld ist optional (nullable bzw. mit Default), damit ein unerwartetes
 * oder fehlendes Feld niemals die gesamte Deserialisierung des Reports zerstört.
 * Für strukturell variable Bereiche (androguard, signature_info, …) wird JsonObject/JsonElement
 * verwendet, das jede beliebige Form aufnehmen kann.
 */
@Serializable
data class ReportAttributes(
    // ---- Erkennungs-Statistik & Engines ----
    @SerialName("last_analysis_stats")
    val lastAnalysisStats: AnalysisStats = AnalysisStats(),

    @SerialName("last_analysis_results")
    val lastAnalysisResults: Map<String, EngineResult> = emptyMap(),

    // ---- Datei-Identität ----
    @SerialName("meaningful_name")
    val meaningfulName: String? = null,
    val names: List<String> = emptyList(),
    val size: Long? = null,
    val magic: String? = null,

    @SerialName("type_description")
    val typeDescription: String? = null,
    @SerialName("type_tag")
    val typeTag: String? = null,
    @SerialName("type_extension")
    val typeExtension: String? = null,
    @SerialName("type_tags")
    val typeTags: List<String> = emptyList(),
    val trid: List<TridResult> = emptyList(),

    // ---- Hashes / Signaturen ----
    val md5: String? = null,
    val sha1: String? = null,
    val sha256: String? = null,
    val ssdeep: String? = null,
    val tlsh: String? = null,
    val vhash: String? = null,
    val authentihash: String? = null,
    val permhash: String? = null,

    // ---- Reputation & Community ----
    val reputation: Int? = null,
    @SerialName("total_votes")
    val totalVotes: Votes? = null,
    val tags: List<String> = emptyList(),

    // ---- Historie ----
    @SerialName("first_submission_date")
    val firstSubmissionDate: Long? = null,
    @SerialName("last_submission_date")
    val lastSubmissionDate: Long? = null,
    @SerialName("last_analysis_date")
    val lastAnalysisDate: Long? = null,
    @SerialName("last_modification_date")
    val lastModificationDate: Long? = null,
    @SerialName("creation_date")
    val creationDate: Long? = null,
    @SerialName("times_submitted")
    val timesSubmitted: Int? = null,
    @SerialName("unique_sources")
    val uniqueSources: Int? = null,

    // ---- Bedrohungs-Klassifizierung ----
    @SerialName("popular_threat_classification")
    val popularThreatClassification: PopularThreatClassification? = null,

    // ---- Sandbox / Crowdsourced ----
    @SerialName("sandbox_verdicts")
    val sandboxVerdicts: Map<String, SandboxVerdict> = emptyMap(),
    @SerialName("crowdsourced_yara_results")
    val crowdsourcedYaraResults: List<YaraResult> = emptyList(),
    @SerialName("sigma_analysis_stats")
    val sigmaAnalysisStats: SigmaStats? = null,
    @SerialName("crowdsourced_ids_stats")
    val crowdsourcedIdsStats: SigmaStats? = null,

    // ---- Android (APK) ----
    val androguard: AndroguardInfo? = null,

    // ---- Weitere strukturell variable Detail-Blöcke ----
    @SerialName("signature_info")
    val signatureInfo: JsonObject? = null,
    @SerialName("pe_info")
    val peInfo: JsonObject? = null,
    val exiftool: JsonObject? = null,
    @SerialName("bundle_info")
    val bundleInfo: JsonObject? = null,

    // ---- URL-spezifisch (/urls Endpunkt) ----
    val url: String? = null,
    @SerialName("final_url")
    val finalUrl: String? = null,
    val title: String? = null,
    val categories: Map<String, String> = emptyMap(),
    @SerialName("last_http_response_code")
    val lastHttpResponseCode: Int? = null,
    @SerialName("last_http_response_content_length")
    val lastHttpResponseContentLength: Long? = null,
    @SerialName("redirection_chain")
    val redirectionChain: List<String> = emptyList(),
    @SerialName("outgoing_links")
    val outgoingLinks: List<String> = emptyList(),
    @SerialName("threat_names")
    val threatNames: List<String> = emptyList()
)

@Serializable
data class AnalysisStats(
    val harmless: Int = 0,
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val undetected: Int = 0,
    @SerialName("type-unsupported")
    val typeUnsupported: Int = 0,
    val timeout: Int = 0,
    @SerialName("confirmed-timeout")
    val confirmedTimeout: Int = 0,
    val failure: Int = 0
) {
    /** Anzahl der Engines, die ein verwertbares Urteil gefällt haben. */
    val total: Int get() = harmless + malicious + suspicious + undetected
}

@Serializable
data class EngineResult(
    @SerialName("engine_name")
    val engineName: String = "Unbekannt",
    val category: String = "undetected", // z. B. "malicious", "harmless", "undetected", "type-unsupported"
    val result: String? = null, // Signaturname, falls erkannt
    val method: String? = null, // z. B. "blacklist"
    @SerialName("engine_version")
    val engineVersion: String? = null,
    @SerialName("engine_update")
    val engineUpdate: String? = null
)

@Serializable
data class Votes(
    val harmless: Int = 0,
    val malicious: Int = 0
)

@Serializable
data class TridResult(
    @SerialName("file_type")
    val fileType: String = "",
    val probability: Double = 0.0
)

@Serializable
data class PopularThreatClassification(
    @SerialName("suggested_threat_label")
    val suggestedThreatLabel: String? = null,
    @SerialName("popular_threat_category")
    val popularThreatCategory: List<ThreatCount> = emptyList(),
    @SerialName("popular_threat_name")
    val popularThreatName: List<ThreatCount> = emptyList()
)

@Serializable
data class ThreatCount(
    val count: Int = 0,
    val value: String = ""
)

@Serializable
data class SandboxVerdict(
    val category: String? = null,
    @SerialName("sandbox_name")
    val sandboxName: String? = null,
    val confidence: Int? = null,
    @SerialName("malware_classification")
    val malwareClassification: List<String> = emptyList(),
    @SerialName("malware_names")
    val malwareNames: List<String> = emptyList()
)

@Serializable
data class YaraResult(
    @SerialName("rule_name")
    val ruleName: String? = null,
    @SerialName("ruleset_name")
    val rulesetName: String? = null,
    val description: String? = null,
    val author: String? = null,
    val source: String? = null
)

@Serializable
data class SigmaStats(
    val critical: Int = 0,
    val high: Int = 0,
    val medium: Int = 0,
    val low: Int = 0
)

/**
 * Androguard-Block für APK-Dateien. Versionsfelder werden als JsonElement geführt,
 * da VirusTotal sie mal als Zahl, mal als String liefert. Strukturell variable Teile
 * (Permissions, certificate, RiskIndicator) bleiben als JsonObject erhalten.
 */
@Serializable
data class AndroguardInfo(
    @SerialName("Package")
    val packageName: String? = null,
    @SerialName("main_activity")
    val mainActivity: String? = null,
    @SerialName("internal_version")
    val internalVersionCode: JsonElement? = null,
    @SerialName("MinSdkVersion")
    val minSdkVersion: JsonElement? = null,
    @SerialName("TargetSdkVersion")
    val targetSdkVersion: JsonElement? = null,
    @SerialName("Activities")
    val activities: List<String> = emptyList(),
    @SerialName("Services")
    val services: List<String> = emptyList(),
    @SerialName("Receivers")
    val receivers: List<String> = emptyList(),
    @SerialName("Providers")
    val providers: List<String> = emptyList(),
    @SerialName("Libraries")
    val libraries: List<String> = emptyList(),
    @SerialName("Permissions")
    val permissions: JsonObject? = null,
    @SerialName("RiskIndicator")
    val riskIndicator: JsonObject? = null,
    val certificate: JsonObject? = null
)

@Serializable
data class UploadUrlResponse(
    val data: String
)

@Serializable
data class FileUploadResponse(
    val data: UploadData
)

@Serializable
data class UploadData(
    val type: String,
    val id: String
)

@Serializable
data class AnalysisResponse(
    val data: AnalysisData,
    val meta: AnalysisMeta? = null
)

@Serializable
data class AnalysisData(
    val id: String,
    val type: String,
    val attributes: AnalysisAttributes = AnalysisAttributes()
)

@Serializable
data class AnalysisAttributes(
    val status: String = "", // "queued", "in-progress", "completed"
    val stats: AnalysisStats? = null
)

@Serializable
data class AnalysisMeta(
    @SerialName("url_info")
    val urlInfo: UrlInfo? = null
)

@Serializable
data class UrlInfo(
    val id: String? = null,
    val url: String? = null
)

/** Antwort von POST /urls – enthält nur die Analyse-ID. */
@Serializable
data class UrlSubmitResponse(
    val data: UrlSubmitData
)

@Serializable
data class UrlSubmitData(
    val id: String,
    val type: String = "analysis"
)

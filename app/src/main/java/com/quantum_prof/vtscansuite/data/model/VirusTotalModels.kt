// app/src/main/java/com/quantum_prof/vtscansuite/data/model/VirusTotalModels.kt
package com.quantum_prof.vtscansuite.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileReportResponse(
    val data: ReportData
)

@Serializable
data class ReportData(
    val id: String, // SHA-256 Hash der Datei
    val type: String,
    val attributes: ReportAttributes
)

@Serializable
data class ReportAttributes(
    // Ordnet "last_analysis_stats" aus dem JSON korrekt zu
    @SerialName("last_analysis_stats")
    val lastAnalysisStats: AnalysisStats,

    // Ordnet "last_analysis_results" aus dem JSON korrekt zu
    @SerialName("last_analysis_results")
    val lastAnalysisResults: Map<String, EngineResult>
)

@Serializable
data class AnalysisStats(
    val harmless: Int,

    // Ordnet "type-unsupported" mit Bindestrich korrekt zu
    @SerialName("type-unsupported")
    val typeUnsupported: Int = 0,

    val malicious: Int,
    val suspicious: Int,
    val undetected: Int
)

@Serializable
data class EngineResult(
    @SerialName("engine_name")
    val engineName: String,

    val category: String, // z. B. "malicious", "harmless"
    val result: String? = null // Signaturname, falls erkannt
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
    val data: AnalysisData
)

@Serializable
data class AnalysisData(
    val id: String,
    val type: String,
    val attributes: AnalysisAttributes
)

@Serializable
data class AnalysisAttributes(
    val status: String // "queued", "in-progress", "completed"
)
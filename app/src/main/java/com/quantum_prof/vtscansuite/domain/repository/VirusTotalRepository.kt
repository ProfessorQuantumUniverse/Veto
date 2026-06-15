// domain/repository/VirusTotalRepository.kt
package com.quantum_prof.vtscansuite.domain.repository

import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import java.io.File

interface VirusTotalRepository {
    suspend fun getFileReport(apiKey: String, hash: String): Result<FileReportResponse>
    suspend fun uploadFile(apiKey: String, file: File): Result<FileReportResponse>
}
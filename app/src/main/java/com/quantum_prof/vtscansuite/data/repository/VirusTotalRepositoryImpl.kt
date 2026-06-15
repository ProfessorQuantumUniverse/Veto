// app/src/main/java/com/quantum_prof/vtscansuite/data/repository/VirusTotalRepositoryImpl.kt
package com.quantum_prof.vtscansuite.data.repository

import com.quantum_prof.vtscansuite.data.model.AnalysisResponse
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.remote.VTScanApiService
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class VirusTotalRepositoryImpl @Inject constructor(
    private val api: VTScanApiService
) : VirusTotalRepository {

    private val sizeLimit32MB = 32 * 1024 * 1024 // 32 Megabytes

    override suspend fun getFileReport(apiKey: String, hash: String): Result<FileReportResponse> {
        return try {
            val response = api.getFileReport(apiKey, hash)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error Code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(apiKey: String, file: File): Result<FileReportResponse> {
        return try {
            val fileHash = calculateSha256(file)

            val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            // 1. Datei hochladen
            val uploadResponse = if (file.length() <= sizeLimit32MB) {
                api.uploadFile(apiKey, body)
            } else {
                val urlResponse = api.getUploadUrl(apiKey)
                if (!urlResponse.isSuccessful || urlResponse.body() == null) {
                    return Result.failure(Exception("Upload-URL konnte nicht bezogen werden: Code ${urlResponse.code()}"))
                }
                val uploadUrl = urlResponse.body()!!.data
                api.uploadFileToUrl(uploadUrl, apiKey, body)
            }

            if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                val analysisId = uploadResponse.body()!!.data.id

                // 2. Polling-Schleife: Warte, bis die Analyse fertiggestellt ist
                var isCompleted = false
                var attempts = 0
                val maxAttempts = 15 // Maximal 75 Sekunden warten (15 * 5s)

                while (!isCompleted && attempts < maxAttempts) {
                    delay(5000) // 5 Sekunden Pause zwischen den Anfragen
                    attempts++

                    val analysisResult = api.getAnalysisReport(apiKey, analysisId)
                    if (analysisResult.isSuccessful && analysisResult.body() != null) {
                        val status = analysisResult.body()!!.data.attributes.status
                        if (status == "completed") {
                            isCompleted = true
                        }
                    }
                }

                // 3. Finalen Report via Hash abfragen, da die Datei nun analysiert wurde
                getFileReport(apiKey, fileHash)
            } else {
                Result.failure(Exception("Upload-Fehler: Code ${uploadResponse.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
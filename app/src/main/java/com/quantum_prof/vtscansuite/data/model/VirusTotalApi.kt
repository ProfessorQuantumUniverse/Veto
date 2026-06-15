// data/remote/VirusTotalApi.kt
package com.quantum_prof.vtscansuite.data.model

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface VirusTotalApi {

    @GET("files/{id}")
    suspend fun getFileReport(
        @Header("x-apikey") apiKey: String,
        @Path("id") fileHash: String
    ): Response<FileReportResponse>

    @Multipart
    @POST("files")
    suspend fun uploadFile(
        @Header("x-apikey") apiKey: String,
        @Part file: MultipartBody.Part
    ): Response<FileReportResponse> // Alternativ ein UploadResponse-Objekt
}
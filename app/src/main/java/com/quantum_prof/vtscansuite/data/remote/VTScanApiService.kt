// app/src/main/java/com/quantum_prof/vtscansuite/data/remote/VTScanApiService.kt
package com.quantum_prof.vtscansuite.data.remote

import com.quantum_prof.vtscansuite.data.model.AnalysisResponse
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.FileUploadResponse
import com.quantum_prof.vtscansuite.data.model.UploadUrlResponse
import com.quantum_prof.vtscansuite.data.model.UrlSubmitResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface VTScanApiService {

    @GET("files/{id}")
    suspend fun getFileReport(
        @Header("x-apikey") apiKey: String,
        @Path("id") fileHash: String
    ): Response<FileReportResponse>

    // ---- URL-Analyse ----
    @FormUrlEncoded
    @POST("urls")
    suspend fun submitUrl(
        @Header("x-apikey") apiKey: String,
        @Field("url") url: String
    ): Response<UrlSubmitResponse>

    @GET("urls/{id}")
    suspend fun getUrlReport(
        @Header("x-apikey") apiKey: String,
        @Path("id") urlId: String
    ): Response<FileReportResponse>

    @Multipart
    @POST("files")
    suspend fun uploadFile(
        @Header("x-apikey") apiKey: String,
        @Part file: MultipartBody.Part
    ): Response<FileUploadResponse>

    @GET("files/upload_url")
    suspend fun getUploadUrl(
        @Header("x-apikey") apiKey: String
    ): Response<UploadUrlResponse>

    @Multipart
    @POST
    suspend fun uploadFileToUrl(
        @Url url: String,
        @Header("x-apikey") apiKey: String,
        @Part file: MultipartBody.Part
    ): Response<FileUploadResponse>

    // NEU: Endpunkt zur Abfrage des aktuellen Analyse-Status
    @GET("analyses/{id}")
    suspend fun getAnalysisReport(
        @Header("x-apikey") apiKey: String,
        @Path("id") analysisId: String
    ): Response<AnalysisResponse>
}
// ui/dashboard/DashboardViewModel.kt
package com.quantum_prof.vtscansuite.ui.dashboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantum_prof.vtscansuite.data.repository.PrefsRepository
import com.quantum_prof.vtscansuite.domain.repository.VirusTotalRepository
import com.quantum_prof.vtscansuite.domain.usecase.ExtractApkUseCase
import com.quantum_prof.vtscansuite.domain.usecase.HashFileUseCase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: VirusTotalRepository,
    private val extractApkUseCase: ExtractApkUseCase,
    private val hashFileUseCase: HashFileUseCase,
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardState>(DashboardState.Idle)
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl.asStateFlow()

    // Beobachtet den API-Key aus dem DataStore und stellt ihn als reaktiven StateFlow bereit
    val apiKey: StateFlow<String> = prefsRepository.apiKeyFlow
        .map { it ?: "" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _installedApps.value = extractApkUseCase.getInstalledApps()
            } catch (e: Exception) {
                _installedApps.value = emptyList()
            }
        }
    }

    fun updateClipboardUrl(url: String?) {
        _clipboardUrl.value = url
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.saveApiKey(key)
        }
    }

    fun resetState() {
        _uiState.value = DashboardState.Idle
    }

    fun scanUrl(url: String) {
        val currentKey = apiKey.value
        if (currentKey.isBlank()) {
            _uiState.value = DashboardState.Error("API-Key fehlt oder ist ungültig.")
            return
        }
        if (url.isBlank()) return

        _uiState.value = DashboardState.Loading("Prüfe URL auf VirusTotal...")
        viewModelScope.launch(Dispatchers.IO) {
            val cleanUrl = url.trim()
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(cleanUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }

            repository.getFileReport(currentKey, sha256)
                .onSuccess { report ->
                    _uiState.value = DashboardState.Success(report)
                }
                .onFailure { error ->
                    _uiState.value = DashboardState.Error("Fehler beim Scannen der URL: ${error.localizedMessage}")
                }
        }
    }

    fun scanApp(app: InstalledApp) {
        val currentKey = apiKey.value
        if (currentKey.isBlank()) {
            _uiState.value = DashboardState.Error("API-Key fehlt oder ist ungültig.")
            return
        }

        _uiState.value = DashboardState.Loading("Berechne lokalen APK-Hash...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = app.apkFile
                val hash = hashFileUseCase(file)

                _uiState.value = DashboardState.Loading("Frage bekannten Hash ab...")

                repository.getFileReport(currentKey, hash)
                    .onSuccess { report ->
                        _uiState.value = DashboardState.Success(report)
                    }
                    .onFailure { error ->
                        if (error.message?.contains("404") == true) {
                            uploadApkFile(currentKey, file)
                        } else {
                            _uiState.value = DashboardState.Error("Fehler beim Abruf: ${error.localizedMessage}")
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = DashboardState.Error("Fehler beim APK-Scannen: ${e.localizedMessage}")
            }
        }
    }

    fun scanUri(context: Context, uri: Uri) {
        val currentKey = apiKey.value
        if (currentKey.isBlank()) {
            _uiState.value = DashboardState.Error("API-Key fehlt oder ist ungültig.")
            return
        }

        _uiState.value = DashboardState.Loading("Kopiere Datei...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val fileName = getFileName(context, uri) ?: "temp_scan_file"
                val tempFile = File(context.cacheDir, fileName)

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                _uiState.value = DashboardState.Loading("Berechne Dateihash...")
                val hash = hashFileUseCase(tempFile)

                _uiState.value = DashboardState.Loading("Frage bekannten Hash ab...")

                repository.getFileReport(currentKey, hash)
                    .onSuccess { report ->
                        _uiState.value = DashboardState.Success(report)
                        tempFile.delete()
                    }
                    .onFailure { error ->
                        if (error.message?.contains("404") == true) {
                            uploadCustomFile(currentKey, tempFile)
                        } else {
                            _uiState.value = DashboardState.Error("Fehler beim Abruf: ${error.localizedMessage}")
                            tempFile.delete()
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = DashboardState.Error("Fehler beim Datei-Scannen: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun uploadApkFile(key: String, file: File) {
        _uiState.value = DashboardState.Loading("Datei unbekannt. Lade APK hoch (${(file.length() / 1024 / 1024)} MB)...")
        repository.uploadFile(key, file)
            .onSuccess { report ->
                _uiState.value = DashboardState.Success(report)
            }
            .onFailure { error ->
                _uiState.value = DashboardState.Error("Upload fehlgeschlagen: ${error.localizedMessage}")
            }
    }

    private suspend fun uploadCustomFile(key: String, file: File) {
        _uiState.value = DashboardState.Loading("Datei unbekannt. Lade Datei hoch (${(file.length() / 1024 / 1024)} MB)...")
        repository.uploadFile(key, file)
            .onSuccess { report ->
                _uiState.value = DashboardState.Success(report)
                file.delete()
            }
            .onFailure { error ->
                _uiState.value = DashboardState.Error("Upload fehlgeschlagen: ${error.localizedMessage}")
                file.delete()
            }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = cursor.getString(columnIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
// MainActivity.kt
package com.quantum_prof.vtscansuite

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.quantum_prof.vtscansuite.scan.ScanNotifications
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardScreen
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardViewModel
import com.quantum_prof.vtscansuite.ui.results.ResultsScreen
import com.quantum_prof.vtscansuite.ui.theme.VTExpressTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            VTExpressTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val apps by viewModel.installedApps.collectAsState()
                    val clipboardUrl by viewModel.clipboardUrl.collectAsState()
                    val apiKey by viewModel.apiKey.collectAsState()
                    val savedScans by viewModel.savedScans.collectAsState()

                    // Lokaler State zur Steuerung der Bildschirmnavigation
                    var activeReport by remember { mutableStateOf<FileReportResponse?>(null) }
                    var activeLabel by remember { mutableStateOf("") }

                    val report = activeReport
                    if (report != null) {
                        ResultsScreen(
                            report = report,
                            isSaved = savedScans.any { it.id == report.data.id },
                            onToggleSave = { viewModel.toggleSave(report, activeLabel) },
                            onBackClick = {
                                activeReport = null
                                viewModel.resetState() // Setzt Dashboard zurück auf Idle
                            }
                        )
                    } else {
                        DashboardScreen(
                            state = uiState,
                            installedApps = apps,
                            clipboardUrl = clipboardUrl,
                            apiKeySaved = apiKey.isNotEmpty(),
                            savedScans = savedScans,
                            onAppSelected = { app -> viewModel.scanApp(app) },
                            onManualScan = { url -> viewModel.scanUrl(url) },
                            onCustomFileSelected = { uri -> viewModel.scanUri(uri) },
                            onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                            onOpenSavedScan = { saved -> viewModel.openSavedScan(saved) },
                            onDeleteSavedScan = { saved -> viewModel.deleteSavedScan(saved) },
                            onNavigateToResults = { r, label ->
                                activeReport = r
                                activeLabel = label
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForUrl()
        // Ergebnis-Notification entfernen, sobald der Nutzer in der App ist
        ScanNotifications.cancelResult(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank() && Patterns.WEB_URL.matcher(sharedText).matches()) {
                viewModel.scanUrl(sharedText)
            }
        }
    }

    private fun checkClipboardForUrl() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                if (Patterns.WEB_URL.matcher(text).matches()) {
                    viewModel.updateClipboardUrl(text)
                    return
                }
            }
        }
        viewModel.updateClipboardUrl(null)
    }
}
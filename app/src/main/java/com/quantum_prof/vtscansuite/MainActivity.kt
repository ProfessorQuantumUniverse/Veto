// MainActivity.kt
package com.quantum_prof.vtscansuite

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

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

                    // Lokaler State zur Steuerung der Bildschirmnavigation
                    var activeReport by remember { mutableStateOf<FileReportResponse?>(null) }

                    if (activeReport != null) {
                        ResultsScreen(
                            report = activeReport!!,
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
                            onAppSelected = { app -> viewModel.scanApp(app) },
                            onManualScan = { url -> viewModel.scanUrl(url) },
                            onCustomFileSelected = { uri -> viewModel.scanUri(this@MainActivity, uri) },
                            onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                            onNavigateToResults = { report ->
                                activeReport = report
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
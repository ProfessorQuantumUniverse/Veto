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
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardScreen
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardState
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardViewModel
import com.quantum_prof.vtscansuite.ui.intro.IntroScreen
import com.quantum_prof.vtscansuite.ui.results.ResultsScreen
import com.quantum_prof.vtscansuite.ui.theme.VTExpressTheme
import com.quantum_prof.vtscansuite.ui.theme.fadeThrough
import com.quantum_prof.vtscansuite.ui.util.triggerScanComplete
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
                    val context = LocalContext.current

                    // Deutliches Abschluss-Feedback, sobald ein Scan fertig ist (einmalig pro Wechsel)
                    LaunchedEffect(uiState) {
                        when (uiState) {
                            is DashboardState.Success -> triggerScanComplete(context, success = true)
                            is DashboardState.Error -> triggerScanComplete(context, success = false)
                            else -> {}
                        }
                    }

                    // Lokaler State zur Steuerung der Bildschirmnavigation
                    var showIntro by rememberSaveable { mutableStateOf(true) }
                    var activeReport by remember { mutableStateOf<FileReportResponse?>(null) }
                    var activeLabel by remember { mutableStateOf("") }

                    val report = activeReport
                    val topScreen: TopScreen = when {
                        showIntro -> TopScreen.Intro
                        report != null -> TopScreen.Results(report, activeLabel)
                        else -> TopScreen.Dashboard
                    }

                    AnimatedContent(
                        targetState = topScreen,
                        contentKey = { it.order }, // Übergang nur bei echtem Screen-Wechsel
                        transitionSpec = { fadeThrough() },
                        label = "topNav"
                    ) { screen ->
                        when (screen) {
                            TopScreen.Intro -> IntroScreen(
                                apiKeySaved = apiKey.isNotEmpty(),
                                onSaveApiKey = { viewModel.saveApiKey(it) },
                                onContinue = { showIntro = false }
                            )
                            is TopScreen.Results -> ResultsScreen(
                                report = screen.report,
                                isSaved = savedScans.any { it.id == screen.report.data.id },
                                onToggleSave = { viewModel.toggleSave(screen.report, screen.label) },
                                onBackClick = {
                                    activeReport = null
                                    viewModel.resetState() // Setzt Dashboard zurück auf Idle
                                }
                            )
                            TopScreen.Dashboard -> DashboardScreen(
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

/** Top-Level-Ziele für den animierten Bildschirmwechsel. `order` dient als Übergangs-Key. */
private sealed interface TopScreen {
    val order: Int

    data object Intro : TopScreen {
        override val order = 0
    }

    data object Dashboard : TopScreen {
        override val order = 1
    }

    data class Results(val report: FileReportResponse, val label: String) : TopScreen {
        override val order = 2
    }
}
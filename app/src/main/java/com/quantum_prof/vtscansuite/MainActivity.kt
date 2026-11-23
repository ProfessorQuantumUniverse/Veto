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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardScreen
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardSubScreen
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardState
import com.quantum_prof.vtscansuite.ui.dashboard.DashboardViewModel
import com.quantum_prof.vtscansuite.ui.dashboard.rememberDashboardViewState
import com.quantum_prof.vtscansuite.ui.intro.IntroScreen
import com.quantum_prof.vtscansuite.ui.results.ResultsScreen
import com.quantum_prof.vtscansuite.ui.theme.VTExpressTheme
import com.quantum_prof.vtscansuite.ui.theme.fadeThrough
import com.quantum_prof.vtscansuite.ui.util.triggerScanComplete
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    /** Hochzählen heißt: zur Liste der installierten Apps wechseln. */
    private var openAppListRequest by mutableIntStateOf(0)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        // Nur beim ersten Start: nach einer Drehung steht dieselbe Absicht noch im Intent,
        // und die Liste soll nicht jedes Mal erneut aufspringen.
        if (savedInstanceState == null) handleOpenAppList(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            VTExpressTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val appList by viewModel.appList.collectAsState()
                    val clipboardUrl by viewModel.clipboardUrl.collectAsState()
                    val apiKey by viewModel.apiKey.collectAsState()
                    val savedScans by viewModel.savedScans.collectAsState()
                    val showHelp by viewModel.showHelp.collectAsState()
                    val askBeforeUpload by viewModel.askBeforeUpload.collectAsState()
                    val appRecords by viewModel.appRecords.collectAsState()
                    val bulkCheck by viewModel.bulkCheck.collectAsState()
                    val uploadState by viewModel.uploadState.collectAsState()
                    val uploadCandidates by viewModel.uploadCandidates.collectAsState()
                    val requestsUsedToday by viewModel.requestsUsedToday.collectAsState()
                    val showSystemApps by viewModel.showSystemApps.collectAsState()
                    val hideWebApks by viewModel.hideWebApks.collectAsState()
                    val webApkCount by viewModel.webApkCount.collectAsState()
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
                    // Über dem Bildschirmwechsel angelegt: Unterbildschirm, Suche, Filter und
                    // Scrollposition der Liste überstehen den Besuch der Ergebnisansicht.
                    val dashboardViewState = rememberDashboardViewState()
                    LaunchedEffect(openAppListRequest) {
                        if (openAppListRequest > 0) {
                            showIntro = false
                            dashboardViewState.screenState.value = DashboardSubScreen.InstalledApps
                        }
                    }
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
                                },
                                showHelp = showHelp
                            )
                            TopScreen.Dashboard -> DashboardScreen(
                                state = uiState,
                                appList = appList,
                                clipboardUrl = clipboardUrl,
                                apiKeySaved = apiKey.isNotEmpty(),
                                savedScans = savedScans,
                                onAppSelected = { app -> viewModel.scanApp(app) },
                                loadIcon = viewModel.loadAppIcon,
                                hideWebApks = hideWebApks,
                                onSetHideWebApks = { viewModel.setHideWebApks(it) },
                                webApkCount = webApkCount,
                                onManualScan = { url -> viewModel.scanUrl(url) },
                                onCustomFileSelected = { uri -> viewModel.scanUri(uri) },
                                onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                                onOpenSavedScan = { saved -> viewModel.openSavedScan(saved) },
                                onDeleteSavedScan = { saved -> viewModel.deleteSavedScan(saved) },
                                onNavigateToResults = { r, label ->
                                    activeReport = r
                                    activeLabel = label
                                },
                                onDismissError = { viewModel.resetState() },
                                showHelp = showHelp,
                                onSetShowHelp = { viewModel.setShowHelp(it) },
                                onConfirmUpload = { viewModel.confirmUpload() },
                                onDeclineUpload = { viewModel.declineUpload() },
                                askBeforeUpload = askBeforeUpload,
                                onSetAskBeforeUpload = { viewModel.setAskBeforeUpload(it) },
                                appRecords = appRecords,
                                bulkCheck = bulkCheck,
                                requestsUsedToday = requestsUsedToday,
                                showSystemApps = showSystemApps,
                                onSetShowSystemApps = { viewModel.setShowSystemApps(it) },
                                onCheckAllHashes = { viewModel.checkAllHashes() },
                                onCancelBulkCheck = { viewModel.cancelBulkCheck() },
                                onAcknowledgeBulkCheck = { viewModel.acknowledgeBulkCheck() },
                                onRefreshApps = { viewModel.refreshApps() },
                                uploadCandidates = uploadCandidates,
                                uploadState = uploadState,
                                onStartUpload = { viewModel.startUpload(it) },
                                onCancelUpload = { viewModel.cancelUpload() },
                                onResumeUpload = { viewModel.resumeUpload() },
                                onAcknowledgeUpload = { viewModel.acknowledgeUpload() },
                                viewState = dashboardViewState
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ergebnis-Notification entfernen, sobald der Nutzer in der App ist
        ScanNotifications.cancelResult(this)
    }

    /**
     * Die Zwischenablage erst lesen, wenn das Fenster den Fokus hat. In onResume ist er
     * noch nicht da, und Android verweigert den Zugriff dann wortlos ("application is not
     * in focus") – der Vorschlag aus der Zwischenablage blieb deshalb manchmal aus.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboardForUrl()
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
        handleOpenAppList(intent)
    }

    /** Die Benachrichtigung des Sammellaufs öffnet die Liste der installierten Apps. */
    private fun handleOpenAppList(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_APP_LIST, false) == true) {
            intent.removeExtra(EXTRA_OPEN_APP_LIST)
            openAppListRequest++
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank() && Patterns.WEB_URL.matcher(sharedText).matches()) {
                viewModel.scanUrl(sharedText)
            }
        }
    }

    companion object {
        /** Intent-Extra: die Liste der installierten Apps öffnen (aus der Benachrichtigung). */
        const val EXTRA_OPEN_APP_LIST = "com.quantum_prof.vtscansuite.OPEN_APP_LIST"
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
// ui/dashboard/DashboardScreen.kt
package com.quantum_prof.vtscansuite.ui.dashboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.scan.BulkCheckState
import com.quantum_prof.vtscansuite.scan.StopReason
import com.quantum_prof.vtscansuite.ui.components.Pill
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.SavedScan
import com.quantum_prof.vtscansuite.domain.repository.ScanPhase
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.ui.components.ExpressiveProgressBar
import com.quantum_prof.vtscansuite.ui.components.GradientButton
import com.quantum_prof.vtscansuite.ui.components.StaggeredReveal
import com.quantum_prof.vtscansuite.ui.dashboard.components.ClipboardBanner
import com.quantum_prof.vtscansuite.ui.theme.CookieShape
import com.quantum_prof.vtscansuite.ui.theme.auroraBrush
import com.quantum_prof.vtscansuite.ui.theme.expressive
import com.quantum_prof.vtscansuite.ui.theme.fadeThrough
import com.quantum_prof.vtscansuite.ui.util.HapticType
import com.quantum_prof.vtscansuite.ui.util.VT_API_KEY_URL
import com.quantum_prof.vtscansuite.ui.util.formatEpochSeconds
import com.quantum_prof.vtscansuite.ui.util.formatFileSize
import com.quantum_prof.vtscansuite.ui.util.openUrl
import com.quantum_prof.vtscansuite.ui.util.pressScale
import com.quantum_prof.vtscansuite.ui.util.scaleOnPress
import com.quantum_prof.vtscansuite.ui.util.triggerHaptic
import kotlinx.coroutines.delay
import java.util.Locale

enum class DashboardSubScreen {
    Home,
    UrlScan,
    CustomFileScan,
    InstalledApps,
    SavedScans,
    Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    installedApps: List<InstalledApp>,
    clipboardUrl: String?,
    apiKeySaved: Boolean,
    savedScans: List<SavedScan>,
    onAppSelected: (InstalledApp) -> Unit,
    onManualScan: (String) -> Unit,
    onCustomFileSelected: (Uri) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onOpenSavedScan: (SavedScan) -> Unit,
    onDeleteSavedScan: (SavedScan) -> Unit,
    onNavigateToResults: (FileReportResponse, String) -> Unit,
    onDismissError: () -> Unit,
    showHelp: Boolean,
    onSetShowHelp: (Boolean) -> Unit,
    /** Gibt den Upload genau dieser einen Datei frei. */
    onConfirmUpload: () -> Unit = {},
    /** Bricht den Scan ab, ohne die Datei hochzuladen. */
    onDeclineUpload: () -> Unit = {},
    /** Ob vor jedem Upload zurückgefragt wird (Einstellung). */
    askBeforeUpload: Boolean = true,
    onSetAskBeforeUpload: (Boolean) -> Unit = {},
    /** Gemerkte Hash-Ergebnisse der installierten Apps. */
    appRecords: Map<String, AppScanRecord> = emptyMap(),
    bulkCheck: BulkCheckState = BulkCheckState.Idle,
    requestsUsedToday: Int = 0,
    showSystemApps: Boolean = false,
    onSetShowSystemApps: (Boolean) -> Unit = {},
    onCheckAllHashes: () -> Unit = {},
    onCancelBulkCheck: () -> Unit = {},
    onAcknowledgeBulkCheck: () -> Unit = {}
) {
    val view = LocalView.current
    var currentScreen by remember { mutableStateOf(DashboardSubScreen.Home) }

    // Systemzurück auf einem Unterbildschirm -> zurück zur Startseite (App nicht schließen)
    BackHandler(enabled = currentScreen != DashboardSubScreen.Home) {
        triggerHaptic(view, HapticType.CLICK)
        currentScreen = DashboardSubScreen.Home
    }

    // Steuert, ob das Vollbild-Overlay angezeigt oder der Scan im Hintergrund läuft
    var backgrounded by remember { mutableStateOf(false) }
    // Bei jedem neuen Scan (neue Startzeit) das Overlay wieder einblenden
    LaunchedEffect((state as? DashboardState.Loading)?.startedAt) {
        if (state is DashboardState.Loading) backgrounded = false
    }

    // Navigationstrigger bei erfolgreichem Scan-Durchlauf
    LaunchedEffect(state) {
        if (state is DashboardState.Success) {
            onNavigateToResults(state.report, state.label)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CenterAlignedTopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = {
                        Text(
                            when (currentScreen) {
                                DashboardSubScreen.Home -> "Veto"
                                DashboardSubScreen.UrlScan -> "Link Scanner"
                                DashboardSubScreen.CustomFileScan -> "File Scanner"
                                DashboardSubScreen.InstalledApps -> "App Scanner"
                                DashboardSubScreen.SavedScans -> "Saved Scans"
                                DashboardSubScreen.Settings -> "Settings"
                            },
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (currentScreen != DashboardSubScreen.Home) {
                            IconButton(onClick = {
                                triggerHaptic(view, HapticType.CLICK)
                                currentScreen = DashboardSubScreen.Home
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == DashboardSubScreen.Home) {
                            IconButton(onClick = {
                                triggerHaptic(view, HapticType.CLICK)
                                currentScreen = DashboardSubScreen.Settings
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeThrough() },
                label = "subScreenNav",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { screen ->
                // Navigations-Routing für Sub-Screens
                when (screen) {
                    DashboardSubScreen.Home -> HomeScreen(
                        apiKeySaved = apiKeySaved,
                        clipboardUrl = clipboardUrl,
                        savedCount = savedScans.size,
                        onScanClipboard = { url ->
                            onManualScan(url)
                        },
                        onNavigateTo = { screen ->
                            currentScreen = screen
                        }
                    )
                    DashboardSubScreen.UrlScan -> UrlScanScreen(
                        apiKeySaved = apiKeySaved,
                        clipboardUrl = clipboardUrl,
                        onScan = { onManualScan(it) }
                    )
                    DashboardSubScreen.CustomFileScan -> CustomFileScanScreen(
                        apiKeySaved = apiKeySaved,
                        onFileSelected = { onCustomFileSelected(it) }
                    )
                    DashboardSubScreen.InstalledApps -> InstalledAppsScreen(
                        installedApps = installedApps,
                        apiKeySaved = apiKeySaved,
                        onAppSelected = { onAppSelected(it) },
                        records = appRecords,
                        bulkCheck = bulkCheck,
                        requestsUsedToday = requestsUsedToday,
                        showSystemApps = showSystemApps,
                        onSetShowSystemApps = onSetShowSystemApps,
                        onCheckAllHashes = onCheckAllHashes,
                        onCancelBulkCheck = onCancelBulkCheck,
                        onAcknowledgeBulkCheck = onAcknowledgeBulkCheck
                    )
                    DashboardSubScreen.SavedScans -> SavedScansScreen(
                        savedScans = savedScans,
                        apiKeySaved = apiKeySaved,
                        onOpenSavedScan = { onOpenSavedScan(it) },
                        onDeleteSavedScan = { onDeleteSavedScan(it) }
                    )
                    DashboardSubScreen.Settings -> SettingsScreen(
                        apiKeySaved = apiKeySaved,
                        onSaveApiKey = {
                            onSaveApiKey(it)
                            triggerHaptic(view, HapticType.SUCCESS)
                        },
                        showHelp = showHelp,
                        onSetShowHelp = onSetShowHelp,
                        askBeforeUpload = askBeforeUpload,
                        onSetAskBeforeUpload = onSetAskBeforeUpload
                    )
                }
            }
        }

        // 1. SATISFYING FULL SCREEN LOADING OVERLAY
        AnimatedVisibility(
            visible = state is DashboardState.Loading && !backgrounded,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            val loading = state as? DashboardState.Loading
            ScanningOverlay(
                loading = loading,
                onRunInBackground = { backgrounded = true }
            )
        }

        // 1b. Minimierter Hinweis, während der Scan im Hintergrund weiterläuft
        AnimatedVisibility(
            visible = state is DashboardState.Loading && backgrounded,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                onClick = {
                    triggerHaptic(view, HapticType.CLICK)
                    backgrounded = false
                },
                modifier = Modifier.padding(20.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Scanning in background…",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 2a. UPLOAD-RÜCKFRAGE: VirusTotal kennt die Datei nicht. Ohne ausdrückliche
        // Zustimmung verlässt sie das Gerät nicht – bei APKs fremder Entwickler ist das
        // nicht nur Privatsache, sondern auch eine Frage der Rechte.
        if (state is DashboardState.UploadConsent) {
            val sizeText = formatFileSize(state.sizeBytes)
            AlertDialog(
                onDismissRequest = {
                    triggerHaptic(view, HapticType.CLICK)
                    onDeclineUpload()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Upload to analyze?") },
                text = {
                    Text(
                        buildString {
                            append("VirusTotal has no report for ")
                            append(state.label.ifBlank { "this file" })
                            append(" ($sizeText), so analyzing it means uploading the whole file.\n\n")
                            if (state.isInstalledApp) {
                                append("This APK belongs to its developer, not to you. ")
                            }
                            append(
                                "Uploaded files are shared with VirusTotal's security partners " +
                                    "and cannot be withdrawn. Only upload what you are entitled to share."
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            triggerHaptic(view, HapticType.CLICK)
                            onConfirmUpload()
                        }
                    ) { Text("Upload and scan") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            triggerHaptic(view, HapticType.CLICK)
                            onDeclineUpload()
                        }
                    ) { Text("Don't upload") }
                },
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        // 2. ERROR STATE DIALOG (Sehr sauber & abfangbar)
        if (state is DashboardState.Error) {
            val isRateLimit = state.isRateLimit
            AlertDialog(
                onDismissRequest = {
                    triggerHaptic(view, HapticType.CLICK)
                    onDismissError()
                },
                confirmButton = {
                    Button(
                        onClick = {
                            triggerHaptic(view, HapticType.CLICK)
                            onDismissError()
                        }
                    ) {
                        Text(if (isRateLimit) "OK, I'll wait" else "Got it")
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isRateLimit) Icons.Default.HourglassTop else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isRateLimit) MaterialTheme.expressive.warning else MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        if (isRateLimit) "Rate limit reached" else "Scan failed",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        state.errorMsg,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = MaterialTheme.shapes.extraLarge
            )
        }
    }
}

// ====================================================================================
// SCANNING OVERLAY (mit Zeitschätzung & Hintergrund-Option)
// ====================================================================================
@Composable
private fun ScanningOverlay(
    loading: DashboardState.Loading?,
    onRunInBackground: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(enabled = false) {}, // Interaktionen blockieren
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                val infinite = rememberInfiniteTransition(label = "scanGlow")
                val pulse by infinite.animateFloat(
                    initialValue = 0.85f, targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
                    label = "pulse"
                )
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            // graphicsLayer statt scale(): animierter Wert wird erst in der
                            // Draw-Phase gelesen → keine Recomposition/Relayout pro Frame
                            .graphicsLayer { scaleX = pulse; scaleY = pulse }
                            .clip(CookieShape(scallops = 12, amplitude = 0.06f))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    )
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    text = loading?.message ?: "Working…",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Tickender Timer für die verstrichene Zeit
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(loading?.startedAt) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        delay(1000)
                    }
                }
                val startedAt = loading?.startedAt?.takeIf { it > 0 } ?: nowMs
                val elapsedSec = ((nowMs - startedAt) / 1000).coerceAtLeast(0)
                val mmss = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)

                // M3 Fortschrittsbalken: determiniert beim Upload, sonst gleitend
                ExpressiveProgressBar(progress = loading?.progress)

                Text(
                    text = loading?.progress?.let { "$mmss · ${(it * 100).toInt()}% uploaded" }
                        ?: "$mmss · ${estimateText(loading?.phase, elapsedSec)}",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "VirusTotal scans can take a while for new files. You can close the app — we'll send a notification when the results are ready.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = onRunInBackground,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run in background", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Grobe, ehrliche Zeitschätzung je nach Phase. */
private fun estimateText(phase: ScanPhase?, elapsedSec: Long): String = when (phase) {
    ScanPhase.UPLOADING -> "uploading…"
    ScanPhase.SUBMITTING -> "submitting URL…"
    ScanPhase.ANALYZING ->
        if (elapsedSec < 120) "estimated wait: up to ~2 min"
        else "still analyzing — large or brand-new files can take several minutes"
    ScanPhase.FETCHING -> "almost done…"
    else -> "checking database…"
}

// ====================================================================================
// SUB-SCREEN 1: HOMEPAGE (Satisfying Grid & Overview)
// ====================================================================================
@Composable
fun HomeScreen(
    apiKeySaved: Boolean,
    clipboardUrl: String?,
    savedCount: Int,
    onScanClipboard: (String) -> Unit,
    onNavigateTo: (DashboardSubScreen) -> Unit
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status Card mit lebendigem "Aurora"-Verlauf
        val statusGradient = if (apiKeySaved) {
            auroraBrush()
        } else {
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                )
            )
        }
        val onGradient = if (apiKeySaved) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(modifier = Modifier.background(statusGradient)) {
                // Dekoratives Cookie-Siegel im Hintergrund
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(120.dp)
                        .clip(CookieShape(scallops = 12, amplitude = 0.06f))
                        .background(onGradient.copy(alpha = 0.10f))
                )
                Row(
                    modifier = Modifier.padding(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(onGradient.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (apiKeySaved) Icons.Default.VerifiedUser else Icons.Default.Key,
                            contentDescription = null,
                            tint = onGradient,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (apiKeySaved) "Ready to scan" else "API key required",
                            color = onGradient,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                        Text(
                            text = if (apiKeySaved) {
                                "Pick a module below to deep-scan links, files or installed apps against 70+ antivirus engines."
                            } else {
                                "Add your VirusTotal API key in Settings to activate the scan engine."
                            },
                            color = onGradient.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Clipboard Banner (Schnittstelle zu URL)
        ClipboardBanner(
            visible = !clipboardUrl.isNullOrBlank() && apiKeySaved,
            url = clipboardUrl ?: "",
            onScanClick = {
                triggerHaptic(view, HapticType.CLICK)
                onScanClipboard(clipboardUrl ?: "")
            }
        )

        Text(
            "SCAN METHODS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        // Expressive Card 1: URL / Links
        HomeMenuCard(
            title = "Scan a URL / link",
            subtitle = "Check web addresses & downloads",
            icon = Icons.Default.TravelExplore,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onNavigateTo(DashboardSubScreen.UrlScan) }
        )

        // Expressive Card 2: Custom File
        HomeMenuCard(
            title = "Check your own file",
            subtitle = "Analyze documents, images or APKs",
            icon = Icons.Default.Description,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onNavigateTo(DashboardSubScreen.CustomFileScan) }
        )

        // Expressive Card 3: Installed Apps
        HomeMenuCard(
            title = "Installed apps",
            subtitle = "Security check for your installed apps",
            icon = Icons.Default.Android,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onNavigateTo(DashboardSubScreen.InstalledApps) }
        )

        // Expressive Card 4: Saved Scans
        HomeMenuCard(
            title = "Saved scans",
            subtitle = if (savedCount > 0) "$savedCount saved · refreshed on reopen" else "Bookmark results to revisit them",
            icon = Icons.Default.Bookmark,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { onNavigateTo(DashboardSubScreen.SavedScans) }
        )
    }
}

@Composable
fun HomeMenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, scaleDown = 0.96f)
    Card(
        onClick = {
            triggerHaptic(view, HapticType.CLICK)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scaleOnPress(scale),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CookieShape(scallops = 8, amplitude = 0.08f))
                    .background(contentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ====================================================================================
// SUB-SCREEN 2: URL SCAN SCREEN
// ====================================================================================
@Composable
fun UrlScanScreen(
    apiKeySaved: Boolean,
    clipboardUrl: String?,
    onScan: (String) -> Unit
) {
    val view = LocalView.current
    var urlText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Scan suspicious web addresses",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            "Enter any web address. VirusTotal checks it against 90+ URL/domain blocklists and security engines, then returns a full verdict.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Enter a URL (e.g. google.com)") },
            placeholder = { Text("https://example.com/suspicious-file") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            trailingIcon = {
                if (urlText.isNotEmpty()) {
                    IconButton(onClick = { urlText = "" }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            }
        )

        // Clipboard Helper Card
        if (!clipboardUrl.isNullOrBlank()) {
            val clipInteraction = remember { MutableInteractionSource() }
            val clipScale = pressScale(clipInteraction)
            Card(
                onClick = {
                    triggerHaptic(view, HapticType.CLICK)
                    urlText = clipboardUrl
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .scaleOnPress(clipScale),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                interactionSource = clipInteraction
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Paste from clipboard",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            clipboardUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(
            text = "Analyze link 🚀",
            icon = Icons.AutoMirrored.Filled.Send,
            enabled = urlText.isNotBlank() && apiKeySaved,
            onClick = { onScan(urlText) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ====================================================================================
// SUB-SCREEN 3: CUSTOM FILE SCAN SCREEN
// ====================================================================================
@Composable
fun CustomFileScanScreen(
    apiKeySaved: Boolean,
    onFileSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            triggerHaptic(view, HapticType.CLICK)
            selectedFileUri = uri
            selectedFileName = getFileName(context, uri) ?: "Selected file"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Check a local file",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            "Pick a document, image, PDF or APK. The file is hashed first — if a report already exists it loads instantly, otherwise it's uploaded and analyzed live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Satisfying Drag & Drop Styled Select Card
        val pickerInteraction = remember { MutableInteractionSource() }
        val pickerScale = pressScale(pickerInteraction, scaleDown = 0.98f)
        Card(
            onClick = {
                triggerHaptic(view, HapticType.CLICK)
                filePickerLauncher.launch("*/*")
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .scaleOnPress(pickerScale),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (selectedFileUri != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ),
            interactionSource = pickerInteraction
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                if (selectedFileUri != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selectedFileUri != null) Icons.Default.Done else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (selectedFileUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    if (selectedFileUri != null) {
                        Text(
                            "Selected file:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            selectedFileName,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Tap to choose a different file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Tap to select a file 🔍",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "All file formats are supported",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        GradientButton(
            text = "Start security check",
            icon = Icons.Default.Shield,
            enabled = selectedFileUri != null && apiKeySaved,
            onClick = { selectedFileUri?.let { onFileSelected(it) } },
            modifier = Modifier.fillMaxWidth()
        )
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

// ====================================================================================
// SUB-SCREEN 4: INSTALLED APPS SCAN SCREEN
// ====================================================================================
@Composable
fun InstalledAppsScreen(
    installedApps: List<InstalledApp>,
    apiKeySaved: Boolean,
    onAppSelected: (InstalledApp) -> Unit,
    /** Gemerkte Hash-Ergebnisse, nach Paketnamen. */
    records: Map<String, AppScanRecord> = emptyMap(),
    bulkCheck: BulkCheckState = BulkCheckState.Idle,
    requestsUsedToday: Int = 0,
    showSystemApps: Boolean = false,
    onSetShowSystemApps: (Boolean) -> Unit = {},
    onCheckAllHashes: () -> Unit = {},
    onCancelBulkCheck: () -> Unit = {},
    onAcknowledgeBulkCheck: () -> Unit = {}
) {
    val view = LocalView.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    // Merkt sich, welche Listen-Elemente bereits eingeblendet wurden (kein Re-Animieren beim Scrollen)
    val revealed = remember { mutableStateMapOf<String, Boolean>() }

    // Zahlen für die Kopfzeile: System-Apps zählen nie mit, sie werden nie gesammelt geprüft.
    val userApps = remember(installedApps) { installedApps.filterNot { it.isSystem } }
    val fresh = remember(userApps, records) {
        userApps.filter { records[it.packageName]?.matches(it.versionCode, it.apkSize) == true }
    }
    val unknownCount = remember(fresh, records) {
        fresh.count { records[it.packageName]?.outcome == AppScanOutcome.UNKNOWN }
    }
    val flaggedCount = remember(fresh, records) {
        fresh.count { (records[it.packageName]?.flagged ?: 0) > 0 }
    }
    val pendingCount = userApps.size - fresh.size
    val estimateMinutes = ((pendingCount * ApiBudgetRepository.MIN_INTERVAL_MS) / 60_000L).toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Scan installed apps",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    buildString {
                        append("${userApps.size} apps")
                        append(" · ${fresh.size} checked")
                        if (unknownCount > 0) append(" · $unknownCount unknown")
                        if (flaggedCount > 0) append(" · $flaggedCount flagged")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    buildString {
                        append("$requestsUsedToday of ${ApiBudgetRepository.DAILY_LIMIT} requests today")
                        append(", counted locally")
                        if (pendingCount > 0) {
                            append(" · $pendingCount left")
                            if (estimateMinutes >= 1) append(", about $estimateMinutes min")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) triggerHaptic(view, HapticType.CLICK) },
                shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Platz für die schwebende Pille am unteren Rand
                contentPadding = PaddingValues(bottom = 108.dp)
            ) {
                item(key = "system-toggle") {
                    SystemAppsToggle(
                        checked = showSystemApps,
                        onCheckedChange = {
                            triggerHaptic(view, HapticType.CLICK)
                            onSetShowSystemApps(it)
                        }
                    )
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No apps found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        StaggeredReveal(
                            alreadyRevealed = revealed[app.packageName] == true,
                            onRevealed = { revealed[app.packageName] = true }
                        ) {
                            AppScanRow(
                                app = app,
                                record = records[app.packageName]
                                    ?.takeIf { it.matches(app.versionCode, app.apkSize) },
                                enabled = apiKeySaved,
                                onCheck = { onAppSelected(app) }
                            )
                        }
                    }
                }
            }
        }

        // Schwebende Pille: Sammellauf starten, verfolgen, abbrechen.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
        ) {
            when (bulkCheck) {
                is BulkCheckState.Running -> BulkRunningPill(
                    state = bulkCheck,
                    onCancel = {
                        triggerHaptic(view, HapticType.CLICK)
                        onCancelBulkCheck()
                    }
                )

                is BulkCheckState.Finished -> BulkFinishedBanner(
                    state = bulkCheck,
                    onDismiss = {
                        triggerHaptic(view, HapticType.CLICK)
                        onAcknowledgeBulkCheck()
                    }
                )

                // Eigene Fläche darunter: die Pille schwebt über der Liste und muss auch
                // im deaktivierten Zustand als Fläche lesbar bleiben.
                BulkCheckState.Idle -> Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    GradientButton(
                        text = when {
                            !apiKeySaved -> "API key required"
                            pendingCount > 0 -> "Check hash of all apps"
                            else -> "All apps checked"
                        },
                        icon = Icons.Default.TravelExplore,
                        enabled = apiKeySaved && pendingCount > 0,
                        onClick = onCheckAllHashes
                    )
                }
            }
        }
    }
}

/**
 * System-Apps sind nur zum Ansehen: der Sammellauf überspringt sie immer, und ihre APKs
 * werden nie hochgeladen.
 */
@Composable
private fun SystemAppsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Show system apps",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Only for looking. They are never part of a bulk check, and a system APK " +
                        "is never uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** Darstellung eines Prüfergebnisses in der Liste. */
private data class AppStatus(
    val label: String,
    val container: Color,
    val content: Color,
    val icon: ImageVector,
    /** Wie stark die Karte eingefärbt wird – nur Auffälliges soll herausstechen. */
    val tint: Float
)

@Composable
private fun statusFor(record: AppScanRecord?, isSystem: Boolean): AppStatus {
    val e = MaterialTheme.expressive
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    val onNeutral = MaterialTheme.colorScheme.onSurfaceVariant
    return when {
        record == null && isSystem ->
            AppStatus("System app", neutral, onNeutral, Icons.Default.Shield, 0f)

        record == null ->
            AppStatus("Not checked", neutral, onNeutral, Icons.Default.Shield, 0f)

        record.outcome == AppScanOutcome.UNKNOWN -> AppStatus(
            "Not on VirusTotal",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.Info,
            0.3f
        )

        record.malicious > 0 -> AppStatus(
            "${record.malicious} of ${record.total} malicious",
            e.dangerContainer, e.onDangerContainer, Icons.Default.GppBad, 0.85f
        )

        record.suspicious > 0 -> AppStatus(
            "${record.suspicious} of ${record.total} suspicious",
            e.warningContainer, e.onWarningContainer, Icons.Default.GppMaybe, 0.7f
        )

        // Bekannt, aber (noch) ohne Engine-Ergebnisse.
        record.total == 0 ->
            AppStatus("No verdict yet", neutral, onNeutral, Icons.Default.Shield, 0f)

        else -> AppStatus(
            "No detections",
            e.safeContainer, e.onSafeContainer, Icons.Default.GppGood, 0.22f
        )
    }
}

@Composable
private fun AppScanRow(
    app: InstalledApp,
    record: AppScanRecord?,
    enabled: Boolean,
    onCheck: () -> Unit
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val status = statusFor(record, app.isSystem)

    val container = if (status.tint > 0f) {
        status.container.copy(alpha = status.tint)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val iconBitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }

    Card(
        onClick = {
            triggerHaptic(view, HapticType.CLICK)
            onCheck()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .scaleOnPress(scale),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    Icons.Default.Android,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    app.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Pill(
                    text = status.label,
                    container = status.container,
                    content = status.content,
                    icon = status.icon
                )
                Text(
                    buildString {
                        append(app.packageName)
                        app.versionName?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (record?.outcome == AppScanOutcome.UNKNOWN) {
                    Text(
                        if (app.isSystem) {
                            "System APKs are never uploaded, so there is nothing more to check."
                        } else {
                            "Checking it means uploading the APK. You will be asked first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Check this app",
                    tint = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BulkRunningPill(
    state: BulkCheckState.Running,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        "Checking ${state.done + 1} of ${state.total}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.currentApp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Clear, contentDescription = "Stop checking")
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (state.total == 0) 0f else state.done.toFloat() / state.total.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun BulkFinishedBanner(
    state: BulkCheckState.Finished,
    onDismiss: () -> Unit
) {
    val headline = when (state.reason) {
        StopReason.COMPLETED -> "All apps checked"
        StopReason.RUN_LIMIT -> "Paused after ${state.checked} apps"
        StopReason.DAILY_LIMIT -> "Daily request limit reached"
        StopReason.RATE_LIMITED -> "VirusTotal asked us to slow down"
        StopReason.NO_API_KEY -> "No API key saved"
        StopReason.CANCELLED -> "Stopped"
    }
    val detail = when (state.reason) {
        StopReason.NO_API_KEY -> "Add your VirusTotal key in Settings first."
        StopReason.DAILY_LIMIT ->
            "The free key allows ${ApiBudgetRepository.DAILY_LIMIT} requests a day. " +
                "${state.remainingApps} apps are left for tomorrow."
        StopReason.RATE_LIMITED ->
            "Nothing was lost. ${state.remainingApps} apps are left, try again in a minute."
        else -> buildString {
            append("${state.checked} checked")
            if (state.unknown > 0) append(", ${state.unknown} unknown to VirusTotal")
            if (state.flagged > 0) append(", ${state.flagged} flagged")
            if (state.failed > 0) append(", ${state.failed} could not be read")
            if (state.remainingApps > 0) append(". ${state.remainingApps} left")
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    headline,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}

// ====================================================================================
// SUB-SCREEN: SAVED SCANS
// ====================================================================================
@Composable
fun SavedScansScreen(
    savedScans: List<SavedScan>,
    apiKeySaved: Boolean,
    onOpenSavedScan: (SavedScan) -> Unit,
    onDeleteSavedScan: (SavedScan) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Your saved scans",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            text = if (apiKeySaved) {
                "Reopen any scan to automatically refresh it with the latest VirusTotal results."
            } else {
                "Add an API key to refresh on reopen — saved results still open from cache."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (savedScans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CookieShape(scallops = 10, amplitude = 0.07f))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text("No saved scans yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Open a result and tap the bookmark icon to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(savedScans, key = { it.id }) { saved ->
                    SavedScanCard(
                        saved = saved,
                        onOpen = { onOpenSavedScan(saved) },
                        onDelete = { onDeleteSavedScan(saved) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedScanCard(
    saved: SavedScan,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val view = LocalView.current
    val e = MaterialTheme.expressive
    val stats = saved.report.data.attributes.lastAnalysisStats
    val flagged = stats.malicious + stats.suspicious

    val (accent, icon) = when {
        stats.malicious > 0 -> e.danger to Icons.Default.GppBad
        stats.suspicious > 0 -> e.warning to Icons.Default.GppMaybe
        stats.total > 0 -> e.safe to Icons.Default.GppGood
        else -> MaterialTheme.colorScheme.outline to Icons.Default.Shield
    }
    val verdictText = if (flagged > 0) "$flagged/${stats.total} detections" else "No detections"

    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Card(
        onClick = {
            triggerHaptic(view, HapticType.CLICK)
            onOpen()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scaleOnPress(scale),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CookieShape(scallops = 8, amplitude = 0.08f))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    saved.label,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$verdictText · ${formatEpochSeconds(saved.savedAt / 1000)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (flagged > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = {
                triggerHaptic(view, HapticType.CLICK)
                onDelete()
            }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete saved scan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ====================================================================================
// SUB-SCREEN 5: SETTINGS SCREEN (Schnittstelle API-Key)
// ====================================================================================
@Composable
fun SettingsScreen(
    apiKeySaved: Boolean,
    onSaveApiKey: (String) -> Unit,
    showHelp: Boolean,
    onSetShowHelp: (Boolean) -> Unit,
    askBeforeUpload: Boolean = true,
    onSetAskBeforeUpload: (Boolean) -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Configuration",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )

        // API Key Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (apiKeySaved) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (apiKeySaved) Icons.Default.Done else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (apiKeySaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Column {
                    Text(
                        if (apiKeySaved) "API key configured ✅" else "API key missing ❌",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (apiKeySaved) "Your scan requests are authorized." else "No scans can run without a key.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Enter a new VirusTotal API key") },
            placeholder = { Text("Paste your key here…") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            // Verhindert, dass die Tastatur den Key lernt oder als Vorschlag anbietet.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false
            ),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Default.Lock else Icons.Default.Settings,
                        contentDescription = if (keyVisible) "Hide" else "Show"
                    )
                }
            }
        )

        GradientButton(
            text = "Save API key",
            icon = Icons.Default.Done,
            enabled = keyInput.isNotBlank(),
            onClick = {
                if (keyInput.isNotBlank()) {
                    onSaveApiKey(keyInput.trim())
                    keyInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Report display preferences
        Text(
            "Report display 🧩",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show help icons", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Little “?” buttons on the results page that explain each section.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showHelp,
                    onCheckedChange = {
                        triggerHaptic(view, HapticType.CLICK)
                        onSetShowHelp(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Upload-Verhalten: die Rückfrage vor jedem Upload lässt sich abschalten.
        Text(
            "Uploads 🔐",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ask before uploading", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (askBeforeUpload) {
                            "A file VirusTotal has never seen can only be analyzed by uploading it. " +
                                "You get asked first, every time."
                        } else {
                            "Unknown files are uploaded straight away, without asking. Uploads are " +
                                "shared with VirusTotal's partners and cannot be withdrawn."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (askBeforeUpload) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = askBeforeUpload,
                    onCheckedChange = {
                        triggerHaptic(view, HapticType.CLICK)
                        onSetAskBeforeUpload(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FAQ / Instructions
        Text(
            "How to get your key 💡",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Create a free VirusTotal account, then open your profile menu (top-right) and choose “API key”. Copy the key shown there and paste it above.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Note: the free key allows up to 4 lookups per minute by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        triggerHaptic(view, HapticType.CLICK)
                        openUrl(context, VT_API_KEY_URL)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Get your API key", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
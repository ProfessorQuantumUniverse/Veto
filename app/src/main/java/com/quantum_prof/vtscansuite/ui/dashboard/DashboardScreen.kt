// ui/dashboard/DashboardScreen.kt
package com.quantum_prof.vtscansuite.ui.dashboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import com.quantum_prof.vtscansuite.ui.dashboard.components.ClipboardBanner
import com.quantum_prof.vtscansuite.ui.util.HapticType
import com.quantum_prof.vtscansuite.ui.util.triggerHaptic

enum class DashboardSubScreen {
    Home,
    UrlScan,
    CustomFileScan,
    InstalledApps,
    Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    installedApps: List<InstalledApp>,
    clipboardUrl: String?,
    apiKeySaved: Boolean,
    onAppSelected: (InstalledApp) -> Unit,
    onManualScan: (String) -> Unit,
    onCustomFileSelected: (Uri) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onNavigateToResults: (FileReportResponse) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(DashboardSubScreen.Home) }

    // Navigationstrigger bei erfolgreichem Scan-Durchlauf
    LaunchedEffect(state) {
        if (state is DashboardState.Success) {
            onNavigateToResults(state.report)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                DashboardSubScreen.Home -> "VT Express"
                                DashboardSubScreen.UrlScan -> "Link-Scanner"
                                DashboardSubScreen.CustomFileScan -> "Datei-Scanner"
                                DashboardSubScreen.InstalledApps -> "App-Scanner"
                                DashboardSubScreen.Settings -> "Einstellungen"
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
                                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == DashboardSubScreen.Home) {
                            IconButton(onClick = {
                                triggerHaptic(view, HapticType.CLICK)
                                currentScreen = DashboardSubScreen.Settings
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Navigations-Routing für Sub-Screens
                when (currentScreen) {
                    DashboardSubScreen.Home -> HomeScreen(
                        apiKeySaved = apiKeySaved,
                        clipboardUrl = clipboardUrl,
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
                        onAppSelected = { onAppSelected(it) }
                    )
                    DashboardSubScreen.Settings -> SettingsScreen(
                        apiKeySaved = apiKeySaved,
                        onSaveApiKey = {
                            onSaveApiKey(it)
                            triggerHaptic(view, HapticType.SUCCESS)
                        }
                    )
                }
            }
        }

        // 1. SATISFYING FULL SCREEN LOADING OVERLAY
        AnimatedVisibility(
            visible = state is DashboardState.Loading,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
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
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = (state as? DashboardState.Loading)?.message ?: "Analysiere...",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bitte schließe die App nicht, während wir das Untersuchungsergebnis von VirusTotal abrufen.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. ERROR STATE DIALOG (Sehr sauber & abfangbar)
        if (state is DashboardState.Error) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    Button(
                        onClick = {
                            triggerHaptic(view, HapticType.CLICK)
                            // Setzt den State zurück auf Idle, damit der Fehler verschwindet
                            onSaveApiKey("") // Triggert VM-Methode um state zurückzusetzen, oder rufe Hilfsmethode auf
                        }
                    ) {
                        Text("Verstanden")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text("Prüfung fehlgeschlagen", fontWeight = FontWeight.Bold)
                    }
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
// SUB-SCREEN 1: HOMEPAGE (Satisfying Grid & Overview)
// ====================================================================================
@Composable
fun HomeScreen(
    apiKeySaved: Boolean,
    clipboardUrl: String?,
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
        // Status Card mit elegantem Verlauf
        val statusGradient = if (apiKeySaved) {
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                )
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(statusGradient)
                    .padding(28.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (apiKeySaved) "System geschützt ✨" else "API-Key fehlt 🔑",
                        color = if (apiKeySaved) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                    Text(
                        text = if (apiKeySaved) {
                            "Wähle ein Modul aus, um Links, APK-Dateien oder Apps tiefgehend auf Schadsoftware zu testen."
                        } else {
                            "Bitte füge in den Einstellungen einen VirusTotal API-Key hinzu, um die Analyse-Engine zu aktivieren."
                        },
                        color = if (apiKeySaved) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onError.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
            "SCAN-METHODEN",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        // Expressive Card 1: URL / Links
        HomeMenuCard(
            title = "URL / Link scannen",
            subtitle = "Prüfe Internetadressen & Downloads",
            icon = Icons.Default.Send,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onNavigateTo(DashboardSubScreen.UrlScan) }
        )

        // Expressive Card 2: Custom File
        HomeMenuCard(
            title = "Eigene Datei prüfen",
            subtitle = "Analysiere Dokumente, Bilder oder APKs",
            icon = Icons.Default.Refresh,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onNavigateTo(DashboardSubScreen.CustomFileScan) }
        )

        // Expressive Card 3: Installed Apps
        HomeMenuCard(
            title = "Installierte Apps",
            subtitle = "Sicherheitsprüfung für System- & Drittanbieter-Apps",
            icon = Icons.Default.Build,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onNavigateTo(DashboardSubScreen.InstalledApps) }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                triggerHaptic(view, HapticType.CLICK)
                onClick()
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
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
                    .background(contentColor.copy(alpha = 0.15f), CircleShape),
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
            "Verdächtige Web-Adressen scannen",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Gib eine beliebige Webadresse ein. Diese wird in einen sicheren SHA-256 Hash konvertiert und über die VirusTotal-Schnittstelle analysiert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("URL eingeben (z.B. google.com)") },
            placeholder = { Text("https://example.com/malicious-file") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            trailingIcon = {
                if (urlText.isNotEmpty()) {
                    IconButton(onClick = { urlText = "" }) {
                        Icon(Icons.Default.Clear, "Leeren")
                    }
                }
            }
        )

        // Clipboard Helper Card
        if (!clipboardUrl.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        triggerHaptic(view, HapticType.CLICK)
                        urlText = clipboardUrl
                    },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
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
                            "Aus Zwischenablage einfügen",
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

        Button(
            onClick = {
                triggerHaptic(view, HapticType.CLICK)
                onScan(urlText)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = urlText.isNotBlank() && apiKeySaved,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Link analysieren 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
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
            selectedFileName = getFileName(context, uri) ?: "Ausgewählte Datei"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "Lokale Datei überprüfen",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Wähle ein Dokument, Bild, PDF oder ein APK-Archiv aus. Die Datei wird zunächst gehasht. Existiert bereits ein Testergebnis, wird dieses sofort geladen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Satisfying Drag & Drop Styled Select Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable {
                    triggerHaptic(view, HapticType.CLICK)
                    filePickerLauncher.launch("*/*")
                },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (selectedFileUri != null) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
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
                            "Ausgewähltes File:",
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
                            "Tippe hier, um eine andere Datei zu wählen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Tippe hier, um eine Datei auszuwählen 🔍",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Alle Dateiformate werden unterstützt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                selectedFileUri?.let { uri ->
                    triggerHaptic(view, HapticType.CLICK)
                    onFileSelected(uri)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = selectedFileUri != null && apiKeySaved,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Icon(Icons.Default.Done, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Sicherheits-Check starten", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
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
    onAppSelected: (InstalledApp) -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Installierte Apps auf Viren scannen",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("App suchen...") },
            modifier = Modifier.fillMaxWidth(),
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
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Keine Apps gefunden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredApps) { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = apiKeySaved) {
                                triggerHaptic(view, HapticType.CLICK)
                                onAppSelected(app)
                            },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    app.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (apiKeySaved) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Scannen",
                                    tint = if (apiKeySaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
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
    onSaveApiKey: (String) -> Unit
) {
    val view = LocalView.current
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
            "Konfiguration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
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
                        if (apiKeySaved) "API-Key eingerichtet ✅" else "API-Key fehlt ❌",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (apiKeySaved) "Deine Scan-Anfragen werden autorisiert." else "Ohne Key können keine Scans vorgenommen werden.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Neuen VirusTotal API-Key eintragen") },
            placeholder = { Text("Füge deinen Key hier ein...") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Default.Lock else Icons.Default.Settings,
                        contentDescription = if (keyVisible) "Ausblenden" else "Einblenden"
                    )
                }
            }
        )

        Button(
            onClick = {
                if (keyInput.isNotBlank()) {
                    onSaveApiKey(keyInput.trim())
                    keyInput = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = keyInput.isNotBlank(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Done, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("API-Key speichern", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FAQ / Anleitung
        Text(
            "Anleitung zum Abrufen des Keys 💡",
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
                    "1. Registriere dich kostenlos auf www.virustotal.com",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "2. Klicke oben rechts auf dein Profil und wähle 'API Key'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "3. Kopiere den dort angezeigten String und füge ihn oben ein.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Hinweis: Der kostenlose Key erlaubt standardmäßig bis zu 4 Suchen pro Minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
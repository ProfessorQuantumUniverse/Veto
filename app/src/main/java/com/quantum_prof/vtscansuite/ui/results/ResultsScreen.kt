// ui/results/ResultsScreen.kt
package com.quantum_prof.vtscansuite.ui.results

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantum_prof.vtscansuite.data.model.AndroguardInfo
import com.quantum_prof.vtscansuite.data.model.EngineResult
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.data.model.ReportAttributes
import com.quantum_prof.vtscansuite.ui.components.AnimatedCounter
import com.quantum_prof.vtscansuite.ui.components.CopyableField
import com.quantum_prof.vtscansuite.ui.components.ExpandableCard
import com.quantum_prof.vtscansuite.ui.components.GradientButton
import com.quantum_prof.vtscansuite.ui.components.LocalShowHelp
import com.quantum_prof.vtscansuite.ui.components.KeyValueRow
import com.quantum_prof.vtscansuite.ui.components.Pill
import com.quantum_prof.vtscansuite.ui.components.SectionCard
import com.quantum_prof.vtscansuite.ui.components.SectionHeader
import com.quantum_prof.vtscansuite.ui.components.StatBar
import com.quantum_prof.vtscansuite.ui.components.TagFlow
import com.quantum_prof.vtscansuite.ui.theme.CookieShape
import com.quantum_prof.vtscansuite.ui.theme.ExpressiveColors
import com.quantum_prof.vtscansuite.ui.theme.expressive
import com.quantum_prof.vtscansuite.ui.util.HapticType
import com.quantum_prof.vtscansuite.ui.util.asDisplayString
import com.quantum_prof.vtscansuite.ui.util.formatEpochSeconds
import com.quantum_prof.vtscansuite.ui.util.formatFileSize
import com.quantum_prof.vtscansuite.ui.util.triggerHaptic
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private enum class Verdict { MALICIOUS, SUSPICIOUS, SAFE, UNKNOWN }

/** Laienverständliche Erklärungen je Abschnitt – erklären auch die einzelnen Unterpunkte. */
private object Help {
    const val DETECTION = "How VirusTotal's security engines judged this item.\n" +
        "• Malicious — the engine believes it is harmful.\n" +
        "• Suspicious — the engine is unsure but sees warning signs.\n" +
        "• Harmless — the engine explicitly considers it safe.\n" +
        "• Undetected — the engine scanned it and found nothing.\n" +
        "• Unsupported / Timeout / Failure — the engine could not analyse it."
    const val THREAT = "VirusTotal's aggregated guess at what this threat is, based on the labels many engines assigned.\n" +
        "• Suggested label — the single most likely name.\n" +
        "• Categories — the type of threat (trojan, adware, …).\n" +
        "• Family names — specific malware families and how many engines named each."
    const val FILE_IDENTITY = "What this file actually is.\n" +
        "• Name / Other names — filenames it has been seen under.\n" +
        "• Type & Extension — the detected file format.\n" +
        "• Size — the file size.\n" +
        "• Magic — a signature describing the format from its first bytes.\n" +
        "• TrID — a probability guess of the file type from its content."
    const val URL = "Details about the scanned link.\n" +
        "• URL / Final URL — the address you scanned and where it ends up after redirects.\n" +
        "• Page title & HTTP status — what the server returned.\n" +
        "• Categories — topic labels vendors assign to the site.\n" +
        "• Threat names — threats associated with the URL, if any."
    const val HASHES = "Cryptographic fingerprints of the file — unique IDs you can use to look it up elsewhere or to prove two files are identical. Tap any value to copy it.\n" +
        "• SHA-256 / SHA-1 / MD5 — standard content hashes (SHA-256 is the strongest).\n" +
        "• SSDEEP / TLSH — \"fuzzy\" hashes that also match near-identical files.\n" +
        "• VHASH / Authentihash / Permhash — VirusTotal and signature/permission-based fingerprints."
    const val HISTORY = "How long VirusTotal has known this item and what the community thinks.\n" +
        "• First seen / Last submitted / Last analysis — key dates.\n" +
        "• Times submitted / Unique sources — how often and by how many different submitters.\n" +
        "• Reputation — a score (negative = bad, positive = trusted).\n" +
        "• Community votes — harmless vs. malicious votes from VirusTotal users."
    const val ANDROID = "Static analysis of the Android app via Androguard (without running it).\n" +
        "• Package / Main activity — the app's ID and entry screen.\n" +
        "• Version code — the app's internal build number.\n" +
        "• Min / Target SDK — the Android versions it supports and targets."
    const val PERMISSIONS = "Android permissions the app requests. Highlighted entries are sensitive — they can access SMS, calls, contacts, location, camera, microphone or storage. Many dangerous permissions on an unknown app is a red flag."
    const val SANDBOX = "Verdicts from automated sandboxes that actually ran the file in an isolated environment to watch its behaviour.\n" +
        "• Sandbox name — which analysis system produced the verdict.\n" +
        "• Category — its overall judgement (e.g. malicious).\n" +
        "• Malware names / classification — threats the sandbox observed at runtime."
    const val YARA = "Community-contributed YARA rules that matched this file. Each rule is a pattern describing known malware or noteworthy traits.\n" +
        "• Rule name & description — what the rule looks for.\n" +
        "• Ruleset & author — where the rule comes from."
    const val SIGNATURE = "Digital code-signing details: who signed the file and whether the signature is valid. A trusted, valid signature makes tampering less likely."
    const val PE = "Internal structure of a Windows executable (PE format): its sections, imported libraries and header fields. Unusual imports or packed sections can hint at malicious behaviour."
    const val EXIF = "Metadata embedded in the file by the tool that created it — for example author, software, camera model and timestamps."
    const val ENGINES = "The individual verdict from each security vendor.\n" +
        "• Detections — engines that flagged this item as malicious or suspicious (with the threat name they used).\n" +
        "• The rest reported no threat, or could not scan this file type."
    const val ADDITIONAL = "Every other field VirusTotal returned for this item that doesn't have a dedicated section above — shown exactly as the API delivered it, so nothing is hidden (e.g. packers, Sigma/IDS results, format-specific metadata, capabilities…)."
}

/**
 * Top-Level-`attributes`-Schlüssel, die bereits eine eigene Karte oben haben. Alles andere
 * aus der Roh-JSON landet im Detailed-Modus in „Additional data" – so fehlt garantiert nichts.
 */
private val HANDLED_ATTR_KEYS = setOf(
    "last_analysis_stats", "last_analysis_results",
    "meaningful_name", "names", "size", "magic",
    "type_description", "type_extension", "type_tags", "tags", "trid",
    "md5", "sha1", "sha256", "ssdeep", "tlsh", "vhash", "authentihash", "permhash",
    "reputation", "total_votes",
    "first_submission_date", "last_submission_date", "last_analysis_date", "creation_date",
    "times_submitted", "unique_sources",
    "popular_threat_classification",
    "sandbox_verdicts", "crowdsourced_yara_results",
    "androguard", "signature_info", "pe_info", "exiftool",
    "url", "final_url", "title", "categories",
    "last_http_response_code", "last_http_response_content_length",
    "redirection_chain", "outgoing_links", "threat_names"
)

private data class VerdictStyle(
    val color: Color,
    val container: Color,
    val onContainer: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

@Composable
private fun verdictStyleFor(verdict: Verdict, e: ExpressiveColors): VerdictStyle = when (verdict) {
    Verdict.MALICIOUS -> VerdictStyle(
        color = e.danger, container = e.dangerContainer, onContainer = e.onDangerContainer,
        icon = Icons.Default.GppBad, title = "Threat detected",
        subtitle = "Multiple engines flag this as malicious."
    )
    Verdict.SUSPICIOUS -> VerdictStyle(
        color = e.warning, container = e.warningContainer, onContainer = e.onWarningContainer,
        icon = Icons.Default.GppMaybe, title = "Suspicious",
        subtitle = "Some engines report suspicious behaviour."
    )
    Verdict.SAFE -> VerdictStyle(
        color = e.safe, container = e.safeContainer, onContainer = e.onSafeContainer,
        icon = Icons.Default.GppGood, title = "Clean",
        subtitle = "No engine reported a threat."
    )
    Verdict.UNKNOWN -> VerdictStyle(
        color = MaterialTheme.colorScheme.primary,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        onContainer = MaterialTheme.colorScheme.onSurface,
        icon = Icons.Default.Shield, title = "No verdict",
        subtitle = "No analysis results are available yet for this item."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    report: FileReportResponse,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onBackClick: () -> Unit,
    showHelp: Boolean = true
) {
    val context = LocalContext.current
    val view = LocalView.current
    val attr = report.data.attributes
    val stats = attr.lastAnalysisStats
    val e = MaterialTheme.expressive

    // Systemzurück → zurück zum Dashboard (statt die App zu schließen)
    BackHandler { onBackClick() }

    val verdict = when {
        stats.malicious > 0 -> Verdict.MALICIOUS
        stats.suspicious > 0 -> Verdict.SUSPICIOUS
        stats.total > 0 -> Verdict.SAFE
        else -> Verdict.UNKNOWN
    }
    val style = verdictStyleFor(verdict, e)
    val isUrl = attr.url != null
    val hasHashes = attr.sha256 != null || attr.sha1 != null || attr.md5 != null ||
            attr.ssdeep != null || attr.tlsh != null || attr.vhash != null

    // Anzeigemodus: Quick (Wesentliches) vs. Detailed (ALLE API-Infos). Quick ist der Default.
    var detailed by rememberSaveable { mutableStateOf(false) }

    // Engine-Filter
    var onlyDetections by remember { mutableStateOf(verdict == Verdict.MALICIOUS || verdict == Verdict.SUSPICIOUS) }
    val sortedEngines = remember(attr.lastAnalysisResults) {
        attr.lastAnalysisResults.values.sortedWith(
            compareByDescending<EngineResult> { it.category == "malicious" }
                .thenByDescending { it.category == "suspicious" }
                .thenBy { it.engineName.lowercase() }
        )
    }
    val visibleEngines = remember(sortedEngines, onlyDetections) {
        if (onlyDetections) sortedEngines.filter { it.category == "malicious" || it.category == "suspicious" }
        else sortedEngines
    }
    val detectionEngines = remember(sortedEngines) {
        sortedEngines.filter { it.category == "malicious" || it.category == "suspicious" }
    }
    val detectionCount = detectionEngines.size
    // In Quick nur Detektionen, in Detailed die (gefilterte) volle Liste.
    val enginesToShow = if (detailed) visibleEngines else detectionEngines

    // Alle Roh-Felder, die keine eigene Karte haben → „Additional data" (Detailed).
    val extraAttributes = remember(report.data.attributesRaw) {
        JsonObject(report.data.attributesRaw.filterKeys { it !in HANDLED_ATTR_KEYS })
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        triggerHaptic(view, HapticType.CLICK)
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        triggerHaptic(view, if (isSaved) HapticType.CLICK else HapticType.SUCCESS)
                        onToggleSave()
                    }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save scan",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        triggerHaptic(view, HapticType.CLICK)
                        shareReport(context, report, verdict)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalShowHelp provides showHelp) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                item { ReportModeToggle(detailed = detailed, onChange = { detailed = it }) }

                val heroTagline = attr.popularThreatClassification?.suggestedThreatLabel
                    ?: if (stats.total > 0) "${stats.total} engines analyzed" else null
                item { VerdictHero(verdict, style, stats.malicious, stats.suspicious, stats.total, heroTagline) }

                item { DetectionBreakdownCard(stats, e) }

                attr.popularThreatClassification?.let { threat ->
                    item { ThreatClassificationCard(threat) }
                }

                if (isUrl) {
                    item { UrlInfoCard(attr, detailed) }
                } else {
                    item { FileIdentityCard(attr) }
                }

                // History & Community gehören auch in die Quick-Zusammenfassung.
                item { HistoryReputationCard(attr, e) }

                // ----- Tiefe Details nur im Detailed-Modus -----
                if (detailed) {
                    if (hasHashes) {
                        item { HashesCard(attr, report.data.id) }
                    }

                    attr.androguard?.let { ag ->
                        item { AndroidCard(ag) }
                    }

                    if (attr.sandboxVerdicts.isNotEmpty()) {
                        item { SandboxCard(attr) }
                    }

                    if (attr.crowdsourcedYaraResults.isNotEmpty()) {
                        item { YaraCard(attr) }
                    }

                    attr.signatureInfo?.let { sig ->
                        item {
                            SectionCard(title = "Signature info", icon = Icons.Default.Verified, accent = MaterialTheme.colorScheme.tertiary, help = Help.SIGNATURE) {
                                JsonDetailList(sig)
                            }
                        }
                    }

                    attr.peInfo?.let { pe ->
                        item {
                            ExpandableCard(title = "PE file structure", icon = Icons.Default.Code, accent = MaterialTheme.colorScheme.secondary, help = Help.PE) {
                                JsonDetailList(pe)
                            }
                        }
                    }

                    attr.exiftool?.let { ex ->
                        item {
                            ExpandableCard(title = "ExifTool metadata", icon = Icons.Default.Description, accent = MaterialTheme.colorScheme.secondary, help = Help.EXIF) {
                                JsonDetailList(ex)
                            }
                        }
                    }

                    // Alles Übrige, was die API liefert (auch dem Modell unbekannte Felder).
                    if (extraAttributes.isNotEmpty()) {
                        item {
                            ExpandableCard(
                                title = "Additional data",
                                icon = Icons.Default.DataObject,
                                accent = MaterialTheme.colorScheme.secondary,
                                badge = "${extraAttributes.size}",
                                help = Help.ADDITIONAL
                            ) {
                                JsonDetailList(extraAttributes)
                            }
                        }
                    }
                }

                // ----- Engine-Ergebnisse (in Quick nur, wenn es Detektionen gibt) -----
                if (detailed || detectionEngines.isNotEmpty()) {
                    item {
                        EnginesHeader(
                            total = sortedEngines.size,
                            detections = detectionCount,
                            onlyDetections = onlyDetections,
                            onToggle = { onlyDetections = it },
                            showFilter = detailed
                        )
                    }

                    items(enginesToShow, key = { it.engineName }, contentType = { "engine" }) { engine ->
                        EngineRow(engine, e)
                    }
                }

                item {
                    GradientButton(
                        text = "View full report on VirusTotal",
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = { openVtReport(context, report) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ============================================================================
//  ANZEIGEMODUS – Quick vs. Detailed
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportModeToggle(detailed: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !detailed,
            onClick = {
                triggerHaptic(view, HapticType.CLICK)
                onChange(false)
            },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("Quick") }
        SegmentedButton(
            selected = detailed,
            onClick = {
                triggerHaptic(view, HapticType.CLICK)
                onChange(true)
            },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Detailed") }
    }
}

// ============================================================================
//  HERO – Animierter Verdikt-Block
// ============================================================================
@Composable
private fun VerdictHero(
    verdict: Verdict,
    style: VerdictStyle,
    malicious: Int,
    suspicious: Int,
    total: Int,
    tagline: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = style.container
    ) {
        Box {
            // Dekoratives Cookie-Siegel im Hintergrund
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(96.dp)
                    .clip(CookieShape(scallops = 12, amplitude = 0.06f))
                    .background(style.onContainer.copy(alpha = 0.06f))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VerdictGauge(verdict, style, malicious, suspicious, total)

                Text(
                    text = style.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = style.onContainer,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = style.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.onContainer.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
                if (tagline != null) {
                    Pill(
                        text = tagline,
                        container = style.onContainer.copy(alpha = 0.14f),
                        content = style.onContainer,
                        icon = if (verdict == Verdict.MALICIOUS || verdict == Verdict.SUSPICIOUS) Icons.Default.BugReport else Icons.Default.Verified
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictGauge(
    verdict: Verdict,
    style: VerdictStyle,
    malicious: Int,
    suspicious: Int,
    total: Int
) {
    val maliciousFraction = if (total > 0) malicious.toFloat() / total else 0f
    val suspiciousFraction = if (total > 0) suspicious.toFloat() / total else 0f

    val animMal = remember { Animatable(0f) }
    val animSus = remember { Animatable(0f) }
    LaunchedEffect(maliciousFraction, suspiciousFraction) {
        animSus.animateTo(suspiciousFraction, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(maliciousFraction) {
        animMal.animateTo(maliciousFraction, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow))
    }

    // Pulsierender Glow
    val infinite = rememberInfiniteTransition(label = "glow")
    val glow by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "glowScale"
    )

    val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val isSafe = verdict == Verdict.SAFE
    val warnColor = MaterialTheme.expressive.warning
    val glowBrush = remember(style.color) {
        Brush.radialGradient(colors = listOf(style.color.copy(alpha = 0.30f), Color.Transparent))
    }

    Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
        // Glow-Hintergrund (Cookie-Form, charakteristisch für Expressive)
        Box(
            modifier = Modifier
                .size(150.dp)
                // graphicsLayer statt scale(): animierter Glow nur in der Draw-Phase
                .graphicsLayer { scaleX = glow; scaleY = glow }
                .clip(CookieShape(scallops = 14, amplitude = 0.05f))
                .background(glowBrush)
        )

        androidx.compose.foundation.Canvas(modifier = Modifier.size(190.dp)) {
            val stroke = 20.dp.toPx()
            // Hintergrundring
            drawCircle(color = trackColor, radius = (size.minDimension - stroke) / 2, style = Stroke(width = stroke))
            if (isSafe) {
                drawArc(
                    color = style.color, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            } else if (verdict != Verdict.UNKNOWN) {
                // Verdächtig-Segment (hinter dem schädlichen)
                if (animSus.value > 0f) {
                    drawArc(
                        color = warnColor, startAngle = -90f + animMal.value * 360f,
                        sweepAngle = (animSus.value * 360f).coerceAtLeast(6f), useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                // Schädlich-Segment
                drawArc(
                    color = style.color, startAngle = -90f,
                    sweepAngle = (animMal.value * 360f).coerceAtLeast(if (malicious > 0) 8f else 0f),
                    useCenter = false, style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(4.dp))
            if (isSafe) {
                Text("100%", fontSize = 30.sp, fontWeight = FontWeight.Black, color = style.onContainer)
                Text("clean", style = MaterialTheme.typography.labelMedium, color = style.onContainer.copy(alpha = 0.7f))
            } else if (verdict == Verdict.UNKNOWN) {
                Text("–", fontSize = 34.sp, fontWeight = FontWeight.Black, color = style.onContainer)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(
                        target = if (malicious > 0) malicious else suspicious,
                        color = style.onContainer,
                        fontSize = 36.sp
                    )
                    Text(
                        " / $total",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = style.onContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text("detections", style = MaterialTheme.typography.labelMedium, color = style.onContainer.copy(alpha = 0.7f))
            }
        }
    }
}

// ============================================================================
//  ERKENNUNGS-AUFSCHLÜSSELUNG
// ============================================================================
@Composable
private fun DetectionBreakdownCard(
    stats: com.quantum_prof.vtscansuite.data.model.AnalysisStats,
    e: ExpressiveColors
) {
    SectionCard(
        title = "Detection overview",
        subtitle = "${stats.total} engines weighed in",
        icon = Icons.Default.Insights,
        accent = MaterialTheme.colorScheme.primary,
        help = Help.DETECTION
    ) {
        val total = stats.total
        StatBar("Malicious", stats.malicious, total, e.danger)
        StatBar("Suspicious", stats.suspicious, total, e.warning)
        StatBar("Harmless", stats.harmless, total, e.safe)
        StatBar("Undetected", stats.undetected, total, MaterialTheme.colorScheme.outline)
        if (stats.typeUnsupported > 0 || stats.timeout > 0 || stats.failure > 0 || stats.confirmedTimeout > 0) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stats.typeUnsupported > 0) MiniStat("Unsupported", stats.typeUnsupported)
                if (stats.timeout + stats.confirmedTimeout > 0) MiniStat("Timeout", stats.timeout + stats.confirmedTimeout)
                if (stats.failure > 0) MiniStat("Failure", stats.failure)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("$value", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================================
//  BEDROHUNGS-KLASSIFIZIERUNG
// ============================================================================
@Composable
private fun ThreatClassificationCard(
    threat: com.quantum_prof.vtscansuite.data.model.PopularThreatClassification
) {
    SectionCard(
        title = "Threat classification",
        icon = Icons.Default.BugReport,
        accent = MaterialTheme.expressive.danger,
        help = Help.THREAT
    ) {
        threat.suggestedThreatLabel?.let { label ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.expressive.dangerContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.expressive.onDangerContainer)
                    Column {
                        Text("Suggested label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.expressive.onDangerContainer.copy(alpha = 0.8f))
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.expressive.onDangerContainer
                        )
                    }
                }
            }
        }
        if (threat.popularThreatCategory.isNotEmpty()) {
            Text("Categories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagFlow(
                tags = threat.popularThreatCategory.map { "${it.value} (${it.count})" },
                container = MaterialTheme.expressive.dangerContainer,
                content = MaterialTheme.expressive.onDangerContainer
            )
        }
        if (threat.popularThreatName.isNotEmpty()) {
            Text("Family names", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagFlow(
                tags = threat.popularThreatName.map { "${it.value} (${it.count})" },
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ============================================================================
//  DATEI-IDENTITÄT
// ============================================================================
@Composable
private fun FileIdentityCard(attr: ReportAttributes) {
    SectionCard(
        title = "File identity",
        icon = Icons.Default.Description,
        accent = MaterialTheme.colorScheme.secondary,
        help = Help.FILE_IDENTITY
    ) {
        attr.meaningfulName?.let { KeyValueRow("Name", it) }
        attr.typeDescription?.let { KeyValueRow("Type", it) }
        attr.typeExtension?.let { KeyValueRow("Extension", ".$it") }
        KeyValueRow("Size", formatFileSize(attr.size))
        attr.magic?.let { KeyValueRow("Magic", it) }

        if (attr.typeTags.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            TagFlow(attr.typeTags)
        }
        if (attr.tags.isNotEmpty()) {
            Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagFlow(
                attr.tags,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        if (attr.trid.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("TrID analysis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            attr.trid.take(5).forEach { trid ->
                StatBar(trid.fileType, trid.probability.toInt(), 100, MaterialTheme.colorScheme.secondary)
            }
        }
        if (attr.names.size > 1) {
            Spacer(Modifier.height(2.dp))
            Text("Other known names (${attr.names.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            attr.names.take(8).forEach { name ->
                Text("• $name", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ============================================================================
//  URL-INFO (für /urls Berichte)
// ============================================================================
@Composable
private fun UrlInfoCard(attr: ReportAttributes, detailed: Boolean) {
    SectionCard(
        title = "URL details",
        icon = Icons.Default.Link,
        accent = MaterialTheme.colorScheme.primary,
        help = Help.URL
    ) {
        attr.url?.let { CopyableField("URL", it) }
        attr.finalUrl?.takeIf { it != attr.url }?.let { CopyableField("Final URL", it) }
        attr.title?.let { KeyValueRow("Page title", it) }
        attr.lastHttpResponseCode?.let { KeyValueRow("HTTP status", "$it") }
        attr.lastHttpResponseContentLength?.let { KeyValueRow("Content length", formatFileSize(it)) }

        val categories = remember(attr.categories) { attr.categories.values.distinct() }
        if (categories.isNotEmpty()) {
            Text("Categories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagFlow(
                tags = categories,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        if (attr.threatNames.isNotEmpty()) {
            Text("Threat names", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagFlow(
                tags = attr.threatNames,
                container = MaterialTheme.expressive.dangerContainer,
                content = MaterialTheme.expressive.onDangerContainer
            )
        }
    }

    if (detailed && attr.redirectionChain.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        ExpandableCard(
            title = "Redirection chain",
            icon = Icons.AutoMirrored.Filled.AltRoute,
            accent = MaterialTheme.colorScheme.secondary,
            badge = "${attr.redirectionChain.size}"
        ) {
            attr.redirectionChain.forEach { link ->
                Text("• $link", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (detailed && attr.outgoingLinks.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        ExpandableCard(
            title = "Outgoing links",
            icon = Icons.Default.Language,
            accent = MaterialTheme.colorScheme.secondary,
            badge = "${attr.outgoingLinks.size}"
        ) {
            attr.outgoingLinks.take(40).forEach { link ->
                Text("• $link", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ============================================================================
//  HASHES
// ============================================================================
@Composable
private fun HashesCard(attr: ReportAttributes, fallbackSha256: String) {
    SectionCard(
        title = "Hashes & signatures",
        subtitle = "Tap any value to copy",
        icon = Icons.Default.Fingerprint,
        accent = MaterialTheme.colorScheme.primary,
        help = Help.HASHES
    ) {
        CopyableField("SHA-256", attr.sha256 ?: fallbackSha256)
        attr.sha1?.let { CopyableField("SHA-1", it) }
        attr.md5?.let { CopyableField("MD5", it) }
        attr.ssdeep?.let { CopyableField("SSDEEP", it) }
        attr.tlsh?.let { CopyableField("TLSH", it) }
        attr.vhash?.let { CopyableField("VHASH", it) }
        attr.authentihash?.let { CopyableField("Authentihash", it) }
        attr.permhash?.let { CopyableField("Permhash", it) }
    }
}

// ============================================================================
//  HISTORIE & REPUTATION
// ============================================================================
@Composable
private fun HistoryReputationCard(attr: ReportAttributes, e: ExpressiveColors) {
    SectionCard(
        title = "History & community",
        icon = Icons.Default.CalendarMonth,
        accent = MaterialTheme.colorScheme.tertiary,
        help = Help.HISTORY
    ) {
        attr.firstSubmissionDate?.let { KeyValueRow("First seen", formatEpochSeconds(it)) }
        attr.lastSubmissionDate?.let { KeyValueRow("Last submitted", formatEpochSeconds(it)) }
        attr.lastAnalysisDate?.let { KeyValueRow("Last analysis", formatEpochSeconds(it)) }
        attr.creationDate?.let { KeyValueRow("Creation date", formatEpochSeconds(it)) }
        attr.timesSubmitted?.let { KeyValueRow("Times submitted", "$it×") }
        attr.uniqueSources?.let { KeyValueRow("Unique sources", "$it") }
        attr.reputation?.let {
            val repColor = if (it < 0) e.danger else if (it > 0) e.safe else MaterialTheme.colorScheme.onSurface
            KeyValueRow("Reputation", "$it", repColor)
        }

        attr.totalVotes?.let { votes ->
            Spacer(Modifier.height(4.dp))
            Text("Community votes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoteChip(Icons.Default.ThumbUp, votes.harmless, "Harmless", e.safe, e.safeContainer, e.onSafeContainer)
                VoteChip(Icons.Default.ThumbDown, votes.malicious, "Malicious", e.danger, e.dangerContainer, e.onDangerContainer)
            }
        }
    }
}

@Composable
private fun VoteChip(icon: ImageVector, count: Int, label: String, accent: Color, container: Color, onContainer: Color) {
    Surface(shape = MaterialTheme.shapes.medium, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text("$count", fontWeight = FontWeight.Black, color = onContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = onContainer.copy(alpha = 0.8f))
        }
    }
}

// ============================================================================
//  ANDROID / APK
// ============================================================================
@Composable
private fun AndroidCard(ag: AndroguardInfo) {
    val permissionKeys = remember(ag) { ag.permissions?.keys?.sorted() ?: emptyList() }
    SectionCard(
        title = "Android app analysis",
        subtitle = "Androguard",
        icon = Icons.Default.Android,
        accent = MaterialTheme.colorScheme.primary,
        help = Help.ANDROID
    ) {
        ag.packageName?.let { KeyValueRow("Package", it) }
        ag.mainActivity?.let { KeyValueRow("Main activity", it) }
        ag.internalVersionCode.asDisplayString()?.let { KeyValueRow("Version code", it) }
        ag.minSdkVersion.asDisplayString()?.let { KeyValueRow("Min SDK", it) }
        ag.targetSdkVersion.asDisplayString()?.let { KeyValueRow("Target SDK", it) }
    }

    Spacer(Modifier.height(16.dp))

    if (permissionKeys.isNotEmpty()) {
        ExpandableCard(
            title = "Permissions",
            icon = Icons.Default.Key,
            accent = MaterialTheme.expressive.warning,
            badge = "${permissionKeys.size}",
            help = Help.PERMISSIONS
        ) {
            permissionKeys.forEach { perm ->
                PermissionRow(perm)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    AppComponentSection("Activities", Icons.Default.Apps, ag.activities)
    AppComponentSection("Services", Icons.Default.Science, ag.services)
    AppComponentSection("Receivers", Icons.Default.Policy, ag.receivers)
    AppComponentSection("Providers", Icons.AutoMirrored.Filled.Article, ag.providers)
    if (ag.libraries.isNotEmpty()) AppComponentSection("Libraries", Icons.Default.Code, ag.libraries)

    ag.certificate?.let { cert ->
        ExpandableCard(title = "Certificate", icon = Icons.Default.Verified, accent = MaterialTheme.colorScheme.tertiary) {
            JsonDetailList(cert)
        }
    }
}

@Composable
private fun PermissionRow(perm: String) {
    val short = perm.substringAfterLast('.')
    val dangerous = perm.contains("SMS", true) || perm.contains("CALL", true) ||
            perm.contains("LOCATION", true) || perm.contains("CONTACTS", true) ||
            perm.contains("CAMERA", true) || perm.contains("RECORD_AUDIO", true) ||
            perm.contains("READ_PHONE", true) || perm.contains("STORAGE", true)
    val accent = if (dangerous) MaterialTheme.expressive.danger else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (dangerous) Icons.Default.Warning else Icons.Default.Lock,
            contentDescription = null, tint = accent, modifier = Modifier.size(16.dp)
        )
        Column {
            Text(short, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(perm, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppComponentSection(title: String, icon: ImageVector, items: List<String>) {
    if (items.isEmpty()) return
    ExpandableCard(title = title, icon = icon, accent = MaterialTheme.colorScheme.secondary, badge = "${items.size}") {
        items.forEach { item ->
            Text("• $item", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ============================================================================
//  SANDBOX
// ============================================================================
@Composable
private fun SandboxCard(attr: ReportAttributes) {
    SectionCard(
        title = "Sandbox verdicts",
        icon = Icons.Default.Science,
        accent = MaterialTheme.colorScheme.secondary,
        help = Help.SANDBOX
    ) {
        attr.sandboxVerdicts.forEach { (_, v) ->
            val malicious = v.category?.contains("malicious", true) == true
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (malicious) MaterialTheme.expressive.dangerContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (malicious) Icons.Default.GppBad else Icons.Default.GppGood,
                            contentDescription = null,
                            tint = if (malicious) MaterialTheme.expressive.danger else MaterialTheme.expressive.safe,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(v.sandboxName ?: "Sandbox", fontWeight = FontWeight.Bold)
                        v.category?.let {
                            Pill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (v.malwareNames.isNotEmpty()) {
                        Text(v.malwareNames.joinToString(", "), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    if (v.malwareClassification.isNotEmpty()) {
                        Text("Classification: ${v.malwareClassification.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ============================================================================
//  YARA
// ============================================================================
@Composable
private fun YaraCard(attr: ReportAttributes) {
    SectionCard(
        title = "Crowdsourced YARA",
        subtitle = "${attr.crowdsourcedYaraResults.size} rules matched",
        icon = Icons.Default.Security,
        accent = MaterialTheme.expressive.warning,
        help = Help.YARA
    ) {
        attr.crowdsourcedYaraResults.forEach { yara ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(yara.ruleName ?: "Rule", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    yara.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        yara.rulesetName?.let { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                        yara.author?.let {
                            Text("by $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
//  ENGINES
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnginesHeader(
    total: Int,
    detections: Int,
    onlyDetections: Boolean,
    onToggle: (Boolean) -> Unit,
    showFilter: Boolean
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        SectionHeader(
            title = "Engine results",
            subtitle = "$total security vendors · $detections detections",
            icon = Icons.Default.Shield,
            accent = MaterialTheme.colorScheme.primary,
            help = Help.ENGINES
        )
        if (showFilter) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onlyDetections,
                onClick = {
                    triggerHaptic(view, HapticType.CLICK)
                    onToggle(false)
                },
                label = { Text("All ($total)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            FilterChip(
                selected = onlyDetections,
                onClick = {
                    triggerHaptic(view, HapticType.CLICK)
                    onToggle(true)
                },
                label = { Text("Detections ($detections)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.expressive.dangerContainer,
                    selectedLabelColor = MaterialTheme.expressive.onDangerContainer
                )
            )
        }
    }
}

@Composable
private fun EngineRow(engine: EngineResult, e: ExpressiveColors) {
    val isMalicious = engine.category == "malicious"
    val isSuspicious = engine.category == "suspicious"
    val (accent, container, icon) = when {
        isMalicious -> Triple(e.danger, e.dangerContainer.copy(alpha = 0.55f), Icons.Default.GppBad)
        isSuspicious -> Triple(e.warning, e.warningContainer.copy(alpha = 0.55f), Icons.Default.GppMaybe)
        engine.category == "harmless" || engine.category == "undetected" -> Triple(e.safe, MaterialTheme.colorScheme.surfaceContainerHigh, Icons.Default.CheckCircle)
        else -> Triple(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceContainerHigh, Icons.Default.Shield)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(engine.engineName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    engine.result ?: when (engine.category) {
                        "type-unsupported" -> "File type not supported"
                        "timeout" -> "Timed out"
                        "failure" -> "Analysis failed"
                        else -> "No threat detected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMalicious || isSuspicious) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = if (engine.result != null) FontFamily.Monospace else FontFamily.Default
                )
            }
            engine.engineVersion?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================================
//  GENERISCHE JSON-DETAIL-LISTE (für variable Strukturen)
// ============================================================================
private const val JSON_MAX_DEPTH = 4
private const val JSON_MAX_KEYS = 100
private const val JSON_MAX_ARRAY = 30

@Composable
private fun JsonDetailList(obj: JsonObject, depth: Int = 0) {
    Column(
        modifier = Modifier.padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        obj.entries.take(JSON_MAX_KEYS).forEach { (key, value) ->
            JsonNode(prettifyKey(key), value, depth)
        }
        if (obj.size > JSON_MAX_KEYS) {
            KeyValueRow("", "+${obj.size - JSON_MAX_KEYS} more fields")
        }
    }
}

@Composable
private fun JsonNode(label: String, value: JsonElement, depth: Int) {
    when (value) {
        is JsonPrimitive -> KeyValueRow(label, value.content.ifBlank { "—" })
        is JsonArray -> {
            val primitives = value.mapNotNull { (it as? JsonPrimitive)?.content }
            when {
                value.isEmpty() -> KeyValueRow(label, "—")
                // Reines Werte-Array: kompakt in einer Zeile.
                primitives.size == value.size ->
                    KeyValueRow(label, primitives.take(12).joinToString(", ") + if (primitives.size > 12) " …" else "")
                depth < JSON_MAX_DEPTH -> {
                    JsonGroupLabel("$label (${value.size})")
                    Column(
                        modifier = Modifier.padding(start = ((depth + 1) * 12).dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        value.take(JSON_MAX_ARRAY).forEachIndexed { i, el ->
                            JsonNode("#${i + 1}", el, depth + 1)
                        }
                        if (value.size > JSON_MAX_ARRAY) KeyValueRow("", "+${value.size - JSON_MAX_ARRAY} more")
                    }
                }
                else -> KeyValueRow(label, "${value.size} entries")
            }
        }
        is JsonObject -> {
            if (value.isEmpty()) {
                KeyValueRow(label, "—")
            } else if (depth < JSON_MAX_DEPTH) {
                JsonGroupLabel(label)
                JsonDetailList(value, depth + 1)
            } else {
                KeyValueRow(label, "${value.size} fields")
            }
        }
    }
}

@Composable
private fun JsonGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

private fun prettifyKey(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

// ============================================================================
//  VIRUSTOTAL-WEBBERICHT ÖFFNEN
// ============================================================================
private fun openVtReport(context: android.content.Context, report: FileReportResponse) {
    val type = report.data.type.ifBlank { "file" } // "file" oder "url"
    val webUrl = "https://www.virustotal.com/gui/$type/${report.data.id}"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
    }
}

// ============================================================================
//  TEILEN
// ============================================================================
private fun shareReport(context: android.content.Context, report: FileReportResponse, verdict: Verdict) {
    val attr = report.data.attributes
    val stats = attr.lastAnalysisStats
    val verdictText = when (verdict) {
        Verdict.MALICIOUS -> "MALICIOUS"
        Verdict.SUSPICIOUS -> "SUSPICIOUS"
        Verdict.SAFE -> "CLEAN"
        Verdict.UNKNOWN -> "NO VERDICT"
    }
    val text = buildString {
        appendLine("Veto: Analysis report")
        appendLine("Verdict: $verdictText")
        appendLine("Detections: ${stats.malicious} malicious / ${stats.suspicious} suspicious of ${stats.total} engines")
        attr.url?.let { appendLine("URL: $it") }
        attr.meaningfulName?.let { appendLine("File: $it") }
        if (attr.url == null) appendLine("SHA-256: ${attr.sha256 ?: report.data.id}")
        attr.popularThreatClassification?.suggestedThreatLabel?.let { appendLine("Label: $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share report"))
}

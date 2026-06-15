// ui/results/ResultsScreen.kt
package com.quantum_prof.vtscansuite.ui.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quantum_prof.vtscansuite.data.model.FileReportResponse
import com.quantum_prof.vtscansuite.ui.dashboard.components.ScoreGaugeChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    report: FileReportResponse,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Übersicht", "AV-Scanner", "Details")
    val attributes = report.data.attributes
    val stats = attributes.lastAnalysisStats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scannergebnis", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewTab(stats, report.data.id)
                1 -> EnginesTab(attributes.lastAnalysisResults)
                2 -> DetailsTab(report.data.id, attributes.lastAnalysisResults.size)
            }
        }
    }
}

@Composable
fun OverviewTab(stats: com.quantum_prof.vtscansuite.data.model.AnalysisStats, hash: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ScoreGaugeChart(
            maliciousCount = stats.malicious,
            totalCount = stats.harmless + stats.malicious + stats.suspicious + stats.undetected
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zusammenfassung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                ResultRow("Unbedenklich (Harmless):", "${stats.harmless}", MaterialTheme.colorScheme.primary)
                ResultRow("Verdächtig (Suspicious):", "${stats.suspicious}", MaterialTheme.colorScheme.tertiary)
                ResultRow("Schadsoftware (Malicious):", "${stats.malicious}", MaterialTheme.colorScheme.error)
                ResultRow("Nicht erkannt (Undetected):", "${stats.undetected}", MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun EnginesTab(results: Map<String, com.quantum_prof.vtscansuite.data.model.EngineResult>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Schädliche/Verdächtige zuerst anzeigen
        val sortedResults = results.values.sortedByDescending { it.category == "malicious" || it.category == "suspicious" }

        items(sortedResults) { engineResult ->
            val isThreat = engineResult.category == "malicious" || engineResult.category == "suspicious"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isThreat) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                ListItem(
                    headlineContent = { Text(engineResult.engineName, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(engineResult.result ?: "Keine Bedrohung erkannt") },
                    leadingContent = {
                        if (isThreat) {
                            Icon(Icons.Default.Warning, contentDescription = "Bedrohung", tint = MaterialTheme.colorScheme.error)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Sicher", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun DetailsTab(sha256: String, engineCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datei-Signaturen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Text("SHA-256 Hash:", style = MaterialTheme.typography.labelMedium)
                Text(sha256, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Geprüfte Antiviren-Engines:", style = MaterialTheme.typography.labelMedium)
                Text("$engineCount Systeme", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
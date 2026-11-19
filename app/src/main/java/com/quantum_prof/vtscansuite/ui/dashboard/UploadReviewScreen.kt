// ui/dashboard/UploadReviewScreen.kt
package com.quantum_prof.vtscansuite.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quantum_prof.vtscansuite.scan.SkipReason
import com.quantum_prof.vtscansuite.scan.UploadCandidate
import com.quantum_prof.vtscansuite.scan.UploadConsent
import com.quantum_prof.vtscansuite.scan.UploadItem
import com.quantum_prof.vtscansuite.scan.UploadItemStatus
import com.quantum_prof.vtscansuite.scan.UploadQueueState
import com.quantum_prof.vtscansuite.scan.UploadText
import com.quantum_prof.vtscansuite.ui.util.formatFileSize

/**
 * "Unbekannte Apps hochladen": erst Durchsicht und ausdrückliche Auswahl, dann Fortschritt.
 *
 * Nichts ist vorausgewählt. "Alle auswählen" ist eine eigene Handlung des Nutzers und wählt
 * höchstens [UploadConsent.MAX_ITEMS] Apps. Hochgeladen wird erst nach der Bestätigung im
 * Dialog, und nur, was zu diesem Zeitpunkt ausgewählt ist.
 */
@Composable
fun UploadReviewScreen(
    candidates: List<UploadCandidate>,
    state: UploadQueueState,
    hashCheckRunning: Boolean,
    onStart: (Set<String>) -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onAcknowledge: () -> Unit
) {
    when (state) {
        is UploadQueueState.Running -> UploadProgressContent(
            items = state.items,
            summary = UploadText.progress(state),
            fraction = state.fraction,
            headline = "Uploading",
            canResume = false,
            retryHint = null,
            onCancel = onCancel,
            onResume = onResume,
            onDone = onAcknowledge
        )

        is UploadQueueState.Finished -> UploadProgressContent(
            items = state.items,
            summary = UploadText.summary(state),
            fraction = state.fraction,
            headline = UploadText.title(state.reason),
            canResume = state.canResume,
            retryHint = state.retryAfterSeconds?.let { "VirusTotal asked to wait about $it seconds." },
            onCancel = onCancel,
            onResume = onResume,
            onDone = onAcknowledge,
            finished = true
        )

        UploadQueueState.Idle -> ReviewContent(candidates, hashCheckRunning, onStart)
    }
}

@Composable
private fun ReviewContent(
    candidates: List<UploadCandidate>,
    hashCheckRunning: Boolean,
    onStart: (Set<String>) -> Unit
) {
    // Nichts vorausgewählt. Wer vom Bildschirm weg und zurück geht, behält die Auswahl.
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val offered = candidates.map { it.packageName }.toSet()
    // Was nicht mehr angeboten wird (deinstalliert, inzwischen bekannt), fällt aus der Auswahl.
    val effective = selected.filter { it in offered }.toSet()
    val atLimit = effective.size >= UploadConsent.MAX_ITEMS

    Column(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Uploading shares the app file", fontWeight = FontWeight.Bold)
                Text(
                    "The APK is sent to VirusTotal and shared with its security partners. " +
                        "It can contain private, proprietary or otherwise sensitive parts of the app. " +
                        "These apps are unknown to VirusTotal, so the file would be new to them.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Files are only submitted. A verdict comes later, when you run the app check again.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${effective.size} of ${candidates.size} selected (max ${UploadConsent.MAX_ITEMS})",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = candidates.isNotEmpty(),
                onClick = {
                    selected = candidates.take(UploadConsent.MAX_ITEMS).map { it.packageName }.toSet()
                }
            ) { Text("Select all") }
            TextButton(enabled = effective.isNotEmpty(), onClick = { selected = emptySet() }) {
                Text("Clear")
            }
        }

        if (candidates.isEmpty()) {
            Text(
                "No app is offered for upload. Only apps that VirusTotal confirmed as unknown " +
                    "for the installed version are listed.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(candidates, key = { it.packageName }) { candidate ->
                val isSelected = candidate.packageName in effective
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isSelected || !atLimit) {
                            selected = if (isSelected) effective - candidate.packageName
                            else effective + candidate.packageName
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        enabled = isSelected || !atLimit,
                        onCheckedChange = {
                            selected = if (it) effective + candidate.packageName
                            else effective - candidate.packageName
                        }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(candidate.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${candidate.versionName ?: "version ${candidate.versionCode}"} · " +
                                formatFileSize(candidate.apkSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            enabled = effective.isNotEmpty() && !hashCheckRunning,
            onClick = { confirming = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                if (hashCheckRunning) "Wait for the app check to finish"
                else "Review ${effective.size} selected…"
            )
        }
    }

    if (confirming) {
        val chosen = candidates.filter { it.packageName in effective }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Upload ${chosen.size} apps to VirusTotal?") },
            text = {
                Text(
                    "Total ${formatFileSize(chosen.sumOf { it.apkSize })}. These files are shared with " +
                        "VirusTotal and its security partners and may expose private or proprietary " +
                        "contents. Only the ${chosen.size} apps you selected are uploaded, each one " +
                        "after a fresh check that it is unchanged. You can stop at any time."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirming = false
                    onStart(chosen.map { it.packageName }.toSet())
                    selected = emptySet()
                }) { Text("Upload") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UploadProgressContent(
    items: List<UploadItem>,
    summary: String,
    fraction: Float,
    headline: String,
    canResume: Boolean,
    retryHint: String?,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onDone: () -> Unit,
    finished: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        if (retryHint != null) Text(retryHint, style = MaterialTheme.typography.bodySmall)

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.consented.packageName }) { item ->
                Column {
                    Text(item.consented.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        statusText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!finished) {
                OutlinedButton(onClick = onCancel) { Text("Stop") }
            } else {
                if (canResume) Button(onClick = onResume) { Text("Continue") }
                OutlinedButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

private fun statusText(item: UploadItem): String = when (item.status) {
    UploadItemStatus.PENDING -> "Waiting"
    UploadItemStatus.UPLOADING -> "Uploading…"
    UploadItemStatus.SUBMITTED -> "Submitted. No verdict yet."
    UploadItemStatus.FAILED -> item.detail ?: "Failed. Nothing was uploaded."
    UploadItemStatus.UNCERTAIN -> item.detail ?: "Unclear whether the file arrived."
    UploadItemStatus.CANCELLED -> "Stopped. Not submitted."
    UploadItemStatus.SKIPPED -> when (item.skipReason) {
        SkipReason.APP_GONE -> "Skipped: the app is no longer installed."
        SkipReason.NOT_ELIGIBLE -> "Skipped: no longer eligible for upload."
        SkipReason.CHANGED -> "Skipped: the app was updated."
        SkipReason.UNREADABLE -> "Skipped: the file cannot be read."
        SkipReason.HASH_CHANGED -> "Skipped: the file changed after you selected it."
        null -> "Skipped."
    }
}

package dev.dazaike.cadence.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.data.ExportFormat
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.CadenceDialog
import dev.dazaike.cadence.ui.components.ErrorNote
import dev.dazaike.cadence.ui.components.Notice
import dev.dazaike.cadence.ui.components.Segmented
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.theme.Cadence
import kotlinx.coroutines.launch

// 3650 days (~10 years) is the pragmatic "All time" ceiling because exportRows(metric, days)
// requires a finite window.
private val DAY_OPTIONS = listOf(7 to "7d", 30 to "30d", 90 to "90d", 365 to "1y", 3650 to "All")

/** Result line shown under the button (replaces the old snackbar). */
private class ExportOutcome(val text: String, val error: Boolean)

/** Export (CSV/JSON/Markdown) and AI share card. Used by Data Sources and Settings. */
@Composable
fun ExportCard() {
    val app = LocalApp.current
    val manager = app.services.manager
    val exporter = app.services.exporter
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Cadence.colors

    var showDialog by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var grantedMetrics by remember { mutableStateOf<List<Metric>>(emptyList()) }
    var pendingMetrics by remember { mutableStateOf<List<Metric>>(emptyList()) }
    var pendingDays by remember { mutableStateOf(365) }
    var outcome by remember { mutableStateOf<ExportOutcome?>(null) }

    suspend fun runExport(uri: Uri, format: ExportFormat, metrics: List<Metric>, days: Int) {
        if (metrics.isEmpty()) {
            outcome = ExportOutcome("No accessible data to export", true); return
        }
        exporting = true
        outcome = null
        val result = try { exporter.export(uri, metrics, format, days) } finally { exporting = false }
        outcome = result.fold(
            onSuccess = { count -> ExportOutcome("Exported $count records as ${format.label}", false) },
            onFailure = { ExportOutcome("Export failed: ${it.message}", true) },
        )
    }

    suspend fun runShareMarkdown(metrics: List<Metric>, days: Int) {
        if (metrics.isEmpty()) {
            outcome = ExportOutcome("No accessible data to export", true); return
        }
        exporting = true
        outcome = null
        val result = try { exporter.exportMarkdownToCache(metrics, days) } finally { exporting = false }
        result.fold(
            onSuccess = { (contentUri, _) ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = ExportFormat.MARKDOWN.mime
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Cadence Health Data Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share health data export").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    context.startActivity(chooser)
                }.onFailure {
                    outcome = ExportOutcome("Could not open share dialog: ${it.message}", true)
                }
            },
            onFailure = { outcome = ExportOutcome("Export failed: ${it.message}", true) },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime),
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.CSV, pendingMetrics, pendingDays) } } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime),
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.JSON, pendingMetrics, pendingDays) } } }

    CadenceCard(title = "Export & AI share", icon = Icons.Rounded.Download, color = c.accent.c600) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Choose metrics, range and format to save to a file, or share a Markdown summary into an AI chat. Files are written on this device, where you choose.",
                style = Cadence.type.bodyRegular, color = c.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CadenceButton(
                    "Export data",
                    onClick = {
                        scope.launch {
                            val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
                            grantedMetrics = Metric.entries.filter { manager.permissionFor(it) in granted }
                            showDialog = true
                        }
                    },
                    icon = Icons.Rounded.Download,
                    enabled = !exporting,
                )
                if (exporting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.accent.c600, strokeWidth = 2.dp)
                    Text("Preparing export…", style = Cadence.type.bodyRegular, color = c.textSecondary)
                }
            }
            outcome?.let { if (it.error) ErrorNote(it.text) else Notice(it.text) }
        }
    }

    if (showDialog) {
        ExportDialog(
            grantedMetrics = grantedMetrics,
            onDismiss = { showDialog = false },
            onExport = { metrics, days, format ->
                showDialog = false
                pendingMetrics = metrics
                pendingDays = days
                when (format) {
                    ExportFormat.CSV -> csvLauncher.launch("cadence-export.csv")
                    ExportFormat.JSON -> jsonLauncher.launch("cadence-export.json")
                    ExportFormat.MARKDOWN -> scope.launch { runShareMarkdown(pendingMetrics, pendingDays) }
                }
            },
        )
    }
}

@Composable
private fun ExportDialog(
    grantedMetrics: List<Metric>,
    onDismiss: () -> Unit,
    onExport: (metrics: List<Metric>, days: Int, format: ExportFormat) -> Unit,
) {
    val c = Cadence.colors
    val t = Cadence.type
    var selected by remember { mutableStateOf(grantedMetrics.toSet()) }
    var days by remember { mutableStateOf(365) }
    var format by remember { mutableStateOf(ExportFormat.CSV) }
    val markdown = format == ExportFormat.MARKDOWN

    CadenceDialog(
        title = "Export data",
        onDismiss = onDismiss,
        confirmLabel = if (markdown) "Share" else "Export",
        confirmEnabled = markdown || selected.isNotEmpty(),
        onConfirm = { onExport(if (markdown) grantedMetrics else selected.toList(), days, format) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Format", style = t.caption1Semibold, color = c.textSecondary)
            Segmented(
                value = format,
                options = listOf(ExportFormat.CSV to "CSV", ExportFormat.JSON to "JSON", ExportFormat.MARKDOWN to "Markdown"),
                onChange = { format = it },
                contentDescription = "Format",
            )
            if (markdown) {
                Text("${ExportFormat.MARKDOWN.label}: shares through the system share sheet.", style = t.caption1Regular, color = c.textSecondary)
            }

            Text("Range", style = t.caption1Semibold, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
            Segmented(
                value = days,
                options = DAY_OPTIONS,
                onChange = { days = it },
                contentDescription = "Range",
            )

            Text("Metrics", style = t.caption1Semibold, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
            when {
                markdown -> Text(
                    "Markdown export always includes every metric you've granted access to.",
                    style = t.bodyRegular, color = c.textSecondary,
                )
                grantedMetrics.isEmpty() -> Text(
                    "No accessible data to export. Grant access under Manage data access first.",
                    style = t.bodyRegular, color = c.textSecondary,
                )
                else -> grantedMetrics.forEach { metric ->
                    val checked = metric in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on -> selected = if (on) selected + metric else selected - metric },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = c.accent.c600,
                                uncheckedColor = c.textTertiary,
                                checkmarkColor = c.onFill,
                            ),
                        )
                        Text(metric.title, style = t.bodyRegular, color = c.textPrimary, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

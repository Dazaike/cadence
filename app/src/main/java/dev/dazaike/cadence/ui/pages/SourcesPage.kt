package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.dayKey
import dev.dazaike.cadence.analytics.fmtAgo
import dev.dazaike.cadence.analytics.fmtNum
import dev.dazaike.cadence.data.DataSource
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.Notice
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.PillTone
import dev.dazaike.cadence.ui.components.Segmented
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.tabular
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import dev.dazaike.cadence.CadenceApp
import dev.dazaike.cadence.samsung.ShizukuWake
import dev.dazaike.cadence.ui.components.ButtonKind
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri

private fun DataSource.displayName(): String = when (this) {
    DataSource.HEALTH_CONNECT -> "Health Connect"
    DataSource.SAMSUNG_HEALTH -> "Samsung Health"
}

/** Port of the web `Sources` page, adapted to an on-device health data source. */
@Composable
fun SourcesPage() {
    PageColumn {
        PageHeader("Data Sources", subtitle = "Imports, synchronisation and device connections")
        DatasetCard()
        ImportCard()
        PermissionsCard()
        if (LocalApp.current.services.source == DataSource.SAMSUNG_HEALTH) SilentSyncCard()
        AdaptiveRow(listOf(1f, 1f), { CloudSyncCard() }, { ExportCard() })
        FreshnessCard()
    }
}

@Composable
private fun DatasetCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val scope = rememberCoroutineScope()
    val source = app.services.source
    CadenceCard(title = "Dataset in use", icon = Icons.Rounded.Storage, color = c.accent.c600) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                buildAnnotatedString {
                    append("You are viewing your on-device data from ")
                    withStyle(SpanStyle(color = c.textPrimary, fontWeight = FontWeight.Medium)) { append(source.displayName()) }
                    append(". It is read live from your phone and stays here unless you sign in to Cloud sync.")
                },
                style = Cadence.type.bodyRegular, color = c.textSecondary,
            )
            Segmented(
                value = source,
                options = listOf(DataSource.HEALTH_CONNECT to "Health Connect", DataSource.SAMSUNG_HEALTH to "Samsung Health (direct)"),
                onChange = { next -> scope.launch { app.services.dataSourcePrefs.setSource(next) } },
                contentDescription = "Data source",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceNote("Health Connect", "Read via Android's Health Connect (default)")
                SourceNote("Samsung Health (direct)", "Reads straight from the Samsung Health app; bypasses Health Connect")
            }
            if (app.view.first == null) {
                Empty("No data read", hint = "Grant access to your health data below, or switch the data source.")
            }
        }
    }
}

@Composable
private fun SourceNote(name: String, description: String) {
    val c = Cadence.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.textPrimary, fontWeight = FontWeight.Medium)) { append(name) }
            append(": $description")
        },
        style = Cadence.type.caption1Regular, color = c.textSecondary,
    )
}

@Composable
private fun ImportCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val access = if (app.services.source == DataSource.SAMSUNG_HEALTH) "Samsung Health access" else "Health Connect access"
    CadenceCard(title = "Import health data", icon = Icons.Rounded.PhoneAndroid, color = c.accent.c600) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "There is nothing to import on your phone. Cadence reads directly from ${app.services.source.displayName()}, and new readings appear after the next refresh.",
                style = Cadence.type.bodyRegular, color = c.textSecondary,
            )
            Text(
                "To change which data Cadence can read, open $access with Manage data access below. File imports (Apple Health export, CSV, JSON) belong to the Cadence web dashboard.",
                style = Cadence.type.caption1Regular, color = c.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionsCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val t = Cadence.type
    val manager = app.services.manager
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(manager, app.loaded.loadedAt) {
        granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
    }
    val readable = granted?.let { g -> Metric.entries.filter { manager.permissionFor(it) in g } }
    val missing = readable?.let { r -> Metric.entries.filter { it !in r } }

    CadenceCard(title = "Permissions", icon = Icons.Rounded.Lock, color = c.accent.c600) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (readable == null || missing == null) {
                Text("Checking access…", style = t.bodyRegular, color = c.textSecondary)
            } else {
                Text("${readable.size} of ${Metric.entries.size} metrics can be read", style = t.bodyMedium, color = c.textPrimary)
                if (readable.isNotEmpty()) {
                    Text("Granted", style = t.caption1Semibold, color = c.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        readable.forEach { Pill(it.title, tone = PillTone.LIME) }
                    }
                }
                if (missing.isNotEmpty()) {
                    Text("Not granted", style = t.caption1Semibold, color = c.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        missing.forEach { Pill(it.title) }
                    }
                }
            }
            Notice("Cadence only reads your health data. It never writes anything back to ${app.services.source.displayName()}.")
            CadenceButton("Manage data access", onClick = app.services.onManagePermissions, icon = Icons.Rounded.Lock)
        }
    }
}

private data class ShizukuCopy(val tone: PillTone, val pill: String, val text: String, val action: String?)

private fun ShizukuWake.State.copy(): ShizukuCopy = when (this) {
    ShizukuWake.State.NOT_INSTALLED -> ShizukuCopy(
        PillTone.NEUTRAL, "Not set up",
        "Samsung Health only syncs your watch while it is open, so Cadence has to open it before reading. With Shizuku it does that on a hidden screen: nothing flashes, and it also works in the background. Install Shizuku, start it, then allow Cadence.",
        "Get Shizuku",
    )
    ShizukuWake.State.NOT_RUNNING -> ShizukuCopy(
        PillTone.NEUTRAL, "Shizuku stopped",
        "Shizuku is installed but not running. Open it and start the service (wireless debugging or root), then come back.",
        "Open Shizuku",
    )
    ShizukuWake.State.NEEDS_PERMISSION -> ShizukuCopy(
        PillTone.NEUTRAL, "Needs permission",
        "Shizuku is running. Allow Cadence so it can open Samsung Health on a hidden screen to sync your watch. It uses that access for nothing else.",
        "Allow in Shizuku",
    )
    ShizukuWake.State.DENIED -> ShizukuCopy(
        PillTone.NEUTRAL, "Denied",
        "Shizuku has Cadence set to deny. Open Shizuku, find Cadence under authorized apps and allow it.",
        "Open Shizuku",
    )
    ShizukuWake.State.READY -> ShizukuCopy(
        PillTone.LIME, "Active",
        "Cadence syncs your watch through Samsung Health on a hidden screen before it reads, on open, on refresh and before each cloud sync. Nothing appears on screen.",
        null,
    )
}

@Composable
private fun SilentSyncCard() {
    val context = LocalContext.current
    val c = Cadence.colors
    val shizuku = remember { (context.applicationContext as CadenceApp).shizukuWake }
    val wake = remember { (context.applicationContext as CadenceApp).samsungWake }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(shizuku.state()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            state = shizuku.state()
            delay(1_500)
        }
    }
    val copy = state.copy()
    CadenceCard(title = "Silent Samsung Health sync", icon = Icons.Rounded.Sync, color = c.accent.c600, actions = { Pill(copy.pill, tone = copy.tone) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(copy.text, style = Cadence.type.bodyRegular, color = c.textSecondary)
            if (copy.action != null) {
                CadenceButton(copy.action, onClick = {
                    when (state) {
                        ShizukuWake.State.NOT_INSTALLED ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuWake.SHIZUKU_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        ShizukuWake.State.NEEDS_PERMISSION -> shizuku.requestPermission()
                        else -> context.packageManager.getLaunchIntentForPackage(ShizukuWake.SHIZUKU_PACKAGE)
                            ?.let { context.startActivity(it) }
                    }
                })
            }
            if (state == ShizukuWake.State.READY) {
                CadenceButton(
                    "Test silent sync",
                    onClick = {
                        scope.launch {
                            testing = true
                            testResult = null
                            val ok = runCatching { wake.wakeSilently(0L) }.getOrDefault(false)
                            testResult = if (ok) "Samsung Health synced your watch." else "No watch sync finished. Check that the watch is connected."
                            testing = false
                        }
                    },
                    kind = ButtonKind.SECONDARY, icon = Icons.Rounded.Sync, loading = testing,
                )
                testResult?.let { Notice(it) }
            }
        }
    }
}

private val FRESH_WEIGHTS = listOf(3f, 2.5f, 1.5f, 2f, 2f, 4.5f)
private val FRESH_MIN = 720.dp

@Composable
private fun FreshnessCard() {
    val app = LocalApp.current
    val view = app.view
    val c = Cadence.colors
    val sourceName = app.services.source.displayName()
    val now = remember { System.currentTimeMillis() }
    val rows = view.series.entries.filter { it.value.isNotEmpty() }.sortedBy { it.key.ordinal }

    CadenceCard(
        title = "Data freshness & provenance",
        color = c.accent.c600,
        info = "Every measurement keeps the source that reported it. Freshness compares the newest sample to now.",
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Notice("Read the last ${fmtNum(app.loaded.days.toDouble())} days of history from $sourceName.")
            if (app.loaded.unreadable.isNotEmpty()) {
                Notice("Could not read ${app.loaded.unreadable.joinToString(", ")}. Everything else is still shown.")
            }
            if (rows.isEmpty()) {
                Empty("No measurements")
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tableW = maxOf(maxWidth, FRESH_MIN)
                    Column(Modifier.horizontalScroll(rememberScrollState()).width(tableW)) {
                        FreshRow(
                            listOf("Metric", "Source", "Samples", "First", "Latest"),
                            header = true,
                        )
                        rows.forEach { (k, samples) ->
                            val last = samples.last().t
                            val age = (now - last) / 86_400_000.0
                            val (label, tone) = when {
                                age <= 2 -> "fresh" to PillTone.LIME
                                age <= 14 -> "recent" to PillTone.YELLOW
                                else -> "stale" to PillTone.ROSE
                            }
                            FreshRow(
                                listOf(
                                    k.label,
                                    sourceName,
                                    fmtNum(samples.size.toDouble()),
                                    dayKey(samples.first().t, view.zone).toString(),
                                    dayKey(last, view.zone).toString(),
                                ),
                                pill = Triple(label, tone, fmtAgo(last, now)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One table row of the freshness card: five text cells plus a freshness pill (or the header labels). */
@Composable
private fun FreshRow(cells: List<String>, header: Boolean = false, pill: Triple<String, PillTone, String>? = null) {
    val c = Cadence.colors
    val t = Cadence.type
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (header) Modifier else Modifier.drawBehind { drawLine(c.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) })
            .padding(vertical = if (header) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, text ->
            val right = i >= 2
            Text(
                text,
                modifier = Modifier.weight(FRESH_WEIGHTS[i]).padding(horizontal = 6.dp),
                style = when {
                    header -> t.caption1Medium
                    i < 2 -> t.bodyRegular
                    else -> t.bodyRegular.tabular()
                },
                color = if (header || i > 0) c.textSecondary else c.textPrimary,
                textAlign = if (right) TextAlign.End else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (header) {
            Text(
                "Freshness",
                modifier = Modifier.weight(FRESH_WEIGHTS[5]).padding(horizontal = 6.dp),
                style = t.caption1Medium, color = c.textSecondary, textAlign = TextAlign.End,
            )
        } else if (pill != null) {
            Row(Modifier.weight(FRESH_WEIGHTS[5]).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.End) {
                Pill("${pill.first} · ${pill.third}", tone = pill.second)
            }
        }
    }
}

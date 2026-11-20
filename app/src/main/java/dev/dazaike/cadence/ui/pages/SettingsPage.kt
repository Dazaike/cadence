package dev.dazaike.cadence.ui.pages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dazaike.cadence.BuildConfig
import dev.dazaike.cadence.analytics.DEFAULT_GOALS
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.conv
import dev.dazaike.cadence.analytics.unconv
import dev.dazaike.cadence.analytics.unitOf
import dev.dazaike.cadence.cloud.CloudUiState
import dev.dazaike.cadence.data.UnitSystem
import dev.dazaike.cadence.data.WidgetDefaults
import dev.dazaike.cadence.data.WidgetPreferences
import dev.dazaike.cadence.mcp.McpServerService
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.CadenceSwitch
import dev.dazaike.cadence.ui.components.ConfirmDialog
import dev.dazaike.cadence.ui.components.ErrorNote
import dev.dazaike.cadence.ui.components.Logo
import dev.dazaike.cadence.ui.components.SelectMenu
import dev.dazaike.cadence.ui.components.Segmented
import dev.dazaike.cadence.ui.components.SettingRow
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.theme.ACCENT_SYSTEM
import dev.dazaike.cadence.ui.theme.Accent
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.ThemeMode
import dev.dazaike.cadence.widget.WidgetUpdateScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private const val REPO_URL = "https://github.com/GuyOnWifi/heartwood"

/** Titles of the Overview widgets (web `WIDGETS[].title`). */
private val WIDGET_TITLES = mapOf(
    "daily" to "Daily activity",
    "steps" to "Steps · last 14 days",
    "heart" to "Heart",
    "sleep" to "Sleep",
    "recovery" to "Recovery",
    "body" to "Body",
    "goals" to "Goals · this week",
    "insights" to "Insights",
    "consistency" to "Activity consistency",
)

/** Port of the web `Settings` page. Cards fill a two-column grid on wide screens, one column on phones. */
@Composable
fun SettingsPage() {
    val cards: List<@Composable () -> Unit> = listOf(
        { CloudAccountCard() },
        { AiAccessCard() },
        { AppearanceCard() },
        { GoalsCard() },
        { DashboardWidgetsCard() },
        { HomeWidgetsCard() },
        { ExportCard() },
        { PrivacyCard() },
        { AboutCard() },
    )
    PageColumn {
        PageHeader("Settings", subtitle = "Account, personalisation, goals and privacy")
        if (LocalWide.current) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.PageGap), verticalAlignment = Alignment.Top) {
                repeat(2) { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.PageGap)) {
                        cards.forEachIndexed { i, card -> if (i % 2 == col) card() }
                    }
                }
            }
        } else {
            cards.forEach { it() }
        }
    }
}

/* ------------------------------------------------------------------ */
/* AI access: the on-device loopback MCP server                        */
/* ------------------------------------------------------------------ */

@Composable
private fun AiAccessCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val t = Cadence.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val mcpPrefs = app.services.mcpPrefs
    val enabled by mcpPrefs.enabled.collectAsStateWithLifecycle(initialValue = false)
    val token by mcpPrefs.token.collectAsStateWithLifecycle(initialValue = null)
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    fun startServer() {
        error = null
        scope.launch {
            mcpPrefs.setEnabled(true)
            ContextCompat.startForegroundService(context, Intent(context, McpServerService::class.java))
        }
    }

    fun stopServer() {
        error = null
        scope.launch { mcpPrefs.setEnabled(false) }
        context.stopService(Intent(context, McpServerService::class.java))
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startServer() else error = "Notification permission is required to run the local AI server."
    }

    fun toggle(on: Boolean) {
        if (!on) {
            stopServer()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startServer()
    }

    CadenceCard(
        Modifier.fillMaxWidth(),
        title = "AI access",
        icon = Icons.Rounded.Key,
        color = c.accent.c600,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Run a read-only server on this device so an AI client you pair over ADB can read your health data. It listens on loopback only and never leaves this device.",
                style = t.bodyRegular, color = c.textSecondary,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Local MCP server", style = t.bodyRegular, color = c.textPrimary)
                    Text(
                        if (enabled) "On — 127.0.0.1:${McpServerService.PORT}" else "Off",
                        style = t.caption1Regular, color = c.textSecondary,
                    )
                }
                CadenceSwitch(checked = enabled, onCheckedChange = ::toggle)
            }
            error?.let { ErrorNote(it) }
            if (enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pairing token", style = t.caption1Medium, color = c.textSecondary)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            token ?: "generating…",
                            style = t.bodyRegular.copy(fontFamily = FontFamily.Monospace),
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        CadenceButton(
                            if (copied) "Copied" else "Copy",
                            onClick = {
                                token?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    copied = true
                                }
                            },
                            kind = ButtonKind.SECONDARY,
                            small = true,
                            enabled = token != null,
                        )
                    }
                }
                Text(
                    "adb forward tcp:${McpServerService.PORT} tcp:${McpServerService.PORT}",
                    style = t.caption1Regular.copy(fontFamily = FontFamily.Monospace), color = c.textSecondary,
                )
                ErrorNote(
                    "Anyone with local device or ADB access to this token can read your health data. " +
                        "Turn off when you're not pairing an AI client.",
                )
                CadenceButton(
                    "Regenerate token",
                    onClick = {
                        scope.launch {
                            mcpPrefs.regenerateToken()
                            context.stopService(Intent(context, McpServerService::class.java))
                            ContextCompat.startForegroundService(context, Intent(context, McpServerService::class.java))
                        }
                    },
                    kind = ButtonKind.SECONDARY,
                    small = true,
                    icon = Icons.Rounded.Refresh,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Appearance                                                          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val t = Cadence.type
    val settings = app.prefs.settings
    CadenceCard(Modifier.fillMaxWidth(), title = "Appearance") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingRow("Theme", Modifier.fillMaxWidth()) {
                Segmented(
                    value = settings.themeMode,
                    options = ThemeMode.entries.map { it to it.label },
                    onChange = { app.actions.setThemeMode(it) },
                    contentDescription = "Theme",
                )
            }
            SettingRow("Units", Modifier.fillMaxWidth()) {
                Segmented(
                    value = app.prefs.units,
                    options = listOf(UnitSystem.IMPERIAL to "mi · lb · in", UnitSystem.METRIC to "km · kg · cm"),
                    onChange = { app.actions.setUnits(it) },
                    contentDescription = "Units",
                )
            }
            Column {
                Text("Accent colour", style = t.bodyRegular, color = c.textPrimary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Accent.entries.forEach { accent ->
                        Swatch(selected = settings.accentId == accent.id, label = accent.label, fill = accent.s500, onClick = { app.actions.setAccent(accent.id) })
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Swatch(
                            selected = settings.accentId == ACCENT_SYSTEM,
                            label = "System colors",
                            fill = c.raised,
                            outline = c.border,
                            icon = Icons.Rounded.Palette,
                            onClick = { app.actions.setAccent(ACCENT_SYSTEM) },
                        )
                    }
                }
            }
            Text(
                "Theme and accent apply right away. Units only change how values are shown, never the stored data.",
                style = t.caption1Regular, color = c.textSecondary,
            )
        }
    }
}

/** A 28 dp accent swatch in a 36 dp hit area; the selected one gets the web's offset ring. */
@Composable
private fun Swatch(
    selected: Boolean,
    label: String,
    fill: Color,
    onClick: () -> Unit,
    outline: Color? = null,
    icon: ImageVector? = null,
) {
    val c = Cadence.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) c.textPrimary else Color.Transparent, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        SwatchFill(fill, outline, icon)
    }
}

@Composable
private fun BoxScope.SwatchFill(fill: Color, outline: Color?, icon: ImageVector?) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(fill)
            .then(if (outline != null) Modifier.border(1.dp, outline, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = Cadence.colors.textSecondary, modifier = Modifier.size(14.dp))
    }
}

/* ------------------------------------------------------------------ */
/* Personal goals                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun GoalsCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val units = app.prefs.units
    val distanceUnit = unitOf(MetricKey.DISTANCE, units)
    val hydrationUnit = unitOf(MetricKey.HYDRATION, units)
    CadenceCard(
        Modifier.fillMaxWidth(),
        title = "Personal goals",
        actions = {
            CadenceButton("Reset", onClick = { app.actions.resetGoals() }, kind = ButtonKind.SECONDARY, small = true, icon = Icons.Rounded.RestartAlt)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalKey.entries.forEach { key ->
                val metric = MetricKey.entries.first { it.goalKey == key }
                val canonical = app.prefs.goals[key] ?: DEFAULT_GOALS.getValue(key)
                SettingRow(key.label, Modifier.fillMaxWidth()) {
                    NumberInput(
                        value = conv(metric, canonical, units),
                        onValue = { app.actions.setGoal(key, unconv(metric, it, units)) },
                        accept = { it > 0.0 },
                        label = key.label,
                        suffix = when (key) {
                            GoalKey.DISTANCE -> distanceUnit
                            GoalKey.HYDRATION -> hydrationUnit
                            else -> null
                        },
                    )
                }
            }
            SettingRow("Assumed max heart rate (bpm)", Modifier.fillMaxWidth()) {
                NumberInput(
                    value = app.prefs.maxHr.toDouble(),
                    onValue = { app.actions.setMaxHr(it.roundToInt()) },
                    accept = { it in 100.0..230.0 },
                    label = "Assumed max heart rate",
                    integer = true,
                )
            }
            Text(
                "Goals are entered in your display units ($distanceUnit for distance, $hydrationUnit for hydration). Max HR only affects the zone chart.",
                style = Cadence.type.caption1Regular, color = c.textSecondary,
            )
        }
    }
}

private fun sameNumber(a: Double, b: Double) = abs(a - b) <= 1e-4

private fun formatInput(v: Double): String =
    if (v == floor(v) && abs(v) < 1e9) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

/**
 * A compact bordered number field (web `NumberField`): valid input is saved as you type, invalid
 * or rejected input is left alone until focus leaves. Outside changes (Reset, unit switch) replace
 * the text; the echo of values this field just sent never does, so fast typing is not overwritten.
 */
@Composable
private fun NumberInput(
    value: Double,
    onValue: (Double) -> Unit,
    accept: (Double) -> Boolean,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    integer: Boolean = false,
) {
    val c = Cadence.colors
    val shape = RoundedCornerShape(Dimens.ControlRadius)
    var text by remember { mutableStateOf(formatInput(value)) }
    var focused by remember { mutableStateOf(false) }
    val sent = remember { mutableListOf<Double>() }

    LaunchedEffect(value) {
        val parsed = text.toDoubleOrNull()
        when {
            parsed != null && sameNumber(parsed, value) -> sent.clear()
            sent.any { sameNumber(it, value) } -> Unit
            else -> {
                text = formatInput(value)
                sent.clear()
            }
        }
    }

    Row(
        modifier
            .width(132.dp)
            .height(40.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, if (focused) c.accent.c600 else c.border, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val next = raw.replace(',', '.').let { s -> if (integer) s.filter(Char::isDigit) else s.filter { it.isDigit() || it == '.' } }
                text = next
                next.toDoubleOrNull()?.takeIf(accept)?.let {
                    sent.add(it)
                    onValue(it)
                }
            },
            singleLine = true,
            textStyle = Cadence.type.bodyRegular.copy(color = c.textPrimary, textAlign = TextAlign.End),
            cursorBrush = SolidColor(c.accent.c600),
            keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) text = formatInput(value)
                }
                .semantics { contentDescription = label },
        )
        if (suffix != null) {
            Spacer(Modifier.width(6.dp))
            Text(suffix, style = Cadence.type.caption1Regular, color = c.textSecondary, maxLines = 1)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Dashboard widgets                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun DashboardWidgetsCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val layout = app.prefs.layout
    CadenceCard(
        Modifier.fillMaxWidth(),
        title = "Dashboard widgets",
        info = "Reorder, resize and hide widgets directly on the Overview. Layouts are saved on this device.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WidgetDefaults.ORDER.forEach { id ->
                val shown = id !in layout.hidden
                val set = { on: Boolean ->
                    app.actions.updateLayout(layout.id) {
                        it.copy(hidden = if (on) it.hidden.filterNot { h -> h == id } else (it.hidden + id).distinct())
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(Dimens.ControlRadius))
                        .clickable(role = Role.Switch) { set(!shown) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(WIDGET_TITLES[id] ?: id, style = Cadence.type.bodyRegular, color = c.textPrimary, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    CadenceSwitch(checked = shown, onCheckedChange = set)
                }
            }
            Text(
                "Editing layout “${layout.name}”. Manage layouts from the Overview page.",
                style = Cadence.type.caption1Regular, color = c.textSecondary,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Home-screen widgets                                                 */
/* ------------------------------------------------------------------ */

private fun intervalLabel(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
    else -> "$minutes minutes"
}

@Composable
private fun HomeWidgetsCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = app.services.widgetPrefs
    val interval by prefs.refreshIntervalMinutes.collectAsStateWithLifecycle(initialValue = 30)
    CadenceCard(Modifier.fillMaxWidth(), title = "Home-screen widgets") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow("Refresh interval", Modifier.fillMaxWidth()) {
                SelectMenu(
                    selected = interval,
                    options = WidgetPreferences.OPTIONS.map { it to "Every ${intervalLabel(it)}" },
                    onSelect = { minutes ->
                        scope.launch {
                            prefs.setRefreshIntervalMinutes(minutes)
                            WidgetUpdateScheduler.schedule(context, minutes.toLong())
                        }
                    },
                    contentDescription = "Widget refresh interval",
                )
            }
            Text(
                "How often the Cadence widgets on your home screen refresh in the background.",
                style = Cadence.type.caption1Regular, color = c.textSecondary,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Privacy and data controls                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun PrivacyCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val t = Cadence.type
    val ui by app.services.cloudSync.ui.collectAsStateWithLifecycle(initialValue = CloudUiState())
    val settings = ui.settings
    var confirmReset by remember { mutableStateOf(false) }
    val bullets = listOf(
        if (settings.signedIn) {
            "Signed in: your health readings and goals are uploaded to ${settings.serverUrl.substringAfter("://")} over HTTPS so the web dashboard and AI assistants can read them. Cadence still only reads from your health data source. There are no analytics or third-party requests."
        } else {
            "Signed out: all data stays on this device. Nothing is uploaded, and there are no analytics or third-party requests."
        },
        "Preferences, goals and layouts are stored on this device. The local AI server, when on, only listens on loopback.",
        "Insights describe trends in your data; they are not medical advice.",
    )
    CadenceCard(Modifier.fillMaxWidth(), title = "Privacy and data controls") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bullets.forEach {
                    Row {
                        Text("•", style = t.caption1Regular, color = c.textSecondary, modifier = Modifier.width(16.dp))
                        Text(it, style = t.caption1Regular, color = c.textSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }
            CadenceButton(
                "Reset preferences",
                onClick = { confirmReset = true },
                kind = ButtonKind.SECONDARY,
                small = true,
                icon = Icons.Rounded.RestartAlt,
            )
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset preferences?",
            description = "Layouts, goals, units and accent colour return to their defaults. Your health data is kept.",
            confirmLabel = "Reset",
            onConfirm = { app.actions.resetPreferences() },
            onDismiss = { confirmReset = false },
        )
    }
}

/* ------------------------------------------------------------------ */
/* About                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun AboutCard() {
    val c = Cadence.colors
    val t = Cadence.type
    val context = LocalContext.current
    CadenceCard(Modifier.fillMaxWidth(), title = "About") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cadence ${BuildConfig.VERSION_NAME}", style = t.bodySemibold, color = c.textPrimary)
                    Text("Private, read-only health dashboard", style = t.caption1Regular, color = c.textSecondary)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.ControlRadius))
                    .clickable(role = Role.Button) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Open source", style = t.bodyMedium, color = c.accent.c600)
                    Text("GPL-3.0-or-later, view the code", style = t.caption1Regular, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.iconSecondary, modifier = Modifier.size(18.dp))
            }
            Text("Typeset in Inter, SIL Open Font License 1.1", style = t.caption1Regular, color = c.textTertiary)
        }
    }
}

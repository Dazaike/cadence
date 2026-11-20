package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dazaike.cadence.cloud.CloudApi
import dev.dazaike.cadence.cloud.CloudUiState
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.CadenceSwitch
import dev.dazaike.cadence.ui.components.CadenceTextField
import dev.dazaike.cadence.ui.components.ConfirmDialog
import dev.dazaike.cadence.ui.components.ErrorNote
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.PillTone
import dev.dazaike.cadence.ui.shell.SyncLine
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.Route
import dev.dazaike.cadence.ui.theme.Cadence
import kotlinx.coroutines.launch

private const val AUTO_SYNC_HINT = "About every 15 minutes while online, and when you open the app"

/** Account card for Settings: sign in or create an account, status, Sync now, auto-sync, sign out. */
@Composable
fun CloudAccountCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val t = Cadence.type
    val cloudSync = app.services.cloudSync
    val scope = rememberCoroutineScope()
    val ui by cloudSync.ui.collectAsStateWithLifecycle(initialValue = CloudUiState())
    val settings = ui.settings
    val host = remember(settings.serverUrl) { settings.serverUrl.substringAfter("://") }

    CadenceCard(Modifier.fillMaxWidth(), title = "Account", icon = Icons.Rounded.Cloud, color = c.accent.c600) {
        if (settings.signedIn) {
            var confirmSignOut by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(settings.email, style = t.bodyRegular, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(host, style = t.caption1Regular, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SyncLine(ui)
                if (settings.lastSyncOk) {
                    settings.lastSyncMessage?.let { Text(it, style = t.caption1Regular, color = c.textSecondary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CadenceButton("Sync now", onClick = { cloudSync.syncNow() }, small = true, loading = ui.busy, enabled = !ui.busy)
                    CadenceButton(
                        "Sign out",
                        onClick = { confirmSignOut = true },
                        kind = ButtonKind.SECONDARY,
                        small = true,
                        icon = Icons.AutoMirrored.Rounded.Logout,
                    )
                }
                AutoSyncRow(checked = settings.autoSync, onChange = { scope.launch { cloudSync.setAutoSync(it) } })
                Text(
                    "While signed in, your health readings and goals are uploaded to $host. Data already " +
                        "uploaded stays on the server after you sign out until you delete it from the dashboard. " +
                        "The local AI server in this page is separate and still never leaves your device.",
                    style = t.caption1Regular, color = c.textSecondary,
                )
            }
            if (confirmSignOut) {
                ConfirmDialog(
                    title = "Sign out?",
                    description = "Cadence will stop syncing and forget this session. Data already uploaded stays in your cloud account.",
                    confirmLabel = "Sign out",
                    onConfirm = { scope.launch { cloudSync.signOut() } },
                    onDismiss = { confirmSignOut = false },
                    danger = false,
                )
            }
        } else {
            var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
            var email by remember(settings.email) { mutableStateOf(settings.email) }
            var password by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            // null = idle, false = signing in, true = creating an account.
            var working by remember { mutableStateOf<Boolean?>(null) }
            val idle = working == null
            val urlInvalid = serverUrl.isNotBlank() && CloudApi.normalizeServerUrl(serverUrl) == null

            fun submit(create: Boolean) {
                if (!idle) return
                working = create
                error = null
                scope.launch {
                    val message = try {
                        cloudSync.signIn(serverUrl, email, password, create)
                    } finally {
                        working = null
                    }
                    if (message == null) password = "" else error = message
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You are using Cadence without an account, so your data stays on this device. Sign in to copy your readings to your own Cadence cloud account, open them in the web dashboard and let AI assistants read them.",
                    style = t.bodyRegular, color = c.textSecondary,
                )
                settings.signedOutReason?.let { Text(it, style = t.caption1Regular, color = c.error) }
                CadenceTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = "Server",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idle,
                    isError = urlInvalid,
                    supportingText = if (urlInvalid) "Use an https:// address (http is only allowed for localhost)." else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                CadenceTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CadenceTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idle,
                    supportingText = "At least 10 characters when creating an account.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                error?.let { ErrorNote(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CadenceButton("Sign in", onClick = { submit(create = false) }, small = true, loading = working == false, enabled = idle && !urlInvalid)
                    CadenceButton(
                        "Create account",
                        onClick = { submit(create = true) },
                        kind = ButtonKind.SECONDARY,
                        small = true,
                        loading = working == true,
                        enabled = idle && !urlInvalid,
                    )
                }
                Text(
                    "Nothing leaves your device until you sign in. Once you do, Cadence uploads your health " +
                        "readings, and your goals, to the server above over HTTPS so the web dashboard and AI " +
                        "assistants can read them. Cadence still only reads from your health data source.",
                    style = t.caption1Regular, color = c.textSecondary,
                )
            }
        }
    }
}

/** Compact cloud sync status and controls card for Data Sources (reuses the status card's sync line). */
@Composable
fun CloudSyncCard() {
    val app = LocalApp.current
    val c = Cadence.colors
    val t = Cadence.type
    val cloudSync = app.services.cloudSync
    val scope = rememberCoroutineScope()
    val ui by cloudSync.ui.collectAsStateWithLifecycle(initialValue = CloudUiState())
    val settings = ui.settings

    CadenceCard(Modifier.fillMaxWidth(), title = "Cloud sync", icon = Icons.Rounded.Cloud, color = c.accent.c600, actions = { Pill("Your data", tone = PillTone.LIME) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (settings.signedIn) {
                Text(settings.email, style = t.bodyRegular, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SyncLine(ui)
                if (settings.lastSyncOk) {
                    settings.lastSyncMessage?.let { Text(it, style = t.caption1Regular, color = c.textSecondary) }
                }
                CadenceButton("Sync now", onClick = { cloudSync.syncNow() }, small = true, loading = ui.busy, enabled = !ui.busy)
                AutoSyncRow(checked = settings.autoSync, onChange = { scope.launch { cloudSync.setAutoSync(it) } })
            } else {
                Text("Not signed in. Optional.", style = t.bodyRegular, color = c.textSecondary)
                CadenceButton(
                    "Open Settings",
                    onClick = { app.actions.navigate(Route.SETTINGS) },
                    kind = ButtonKind.SECONDARY,
                    small = true,
                )
            }
        }
    }
}

@Composable
private fun AutoSyncRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cadence.colors
    val t = Cadence.type
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Auto-sync", style = t.bodyRegular, color = c.textPrimary)
            Text(AUTO_SYNC_HINT, style = t.caption1Regular, color = c.textSecondary)
        }
        CadenceSwitch(checked = checked, onCheckedChange = onChange)
    }
}

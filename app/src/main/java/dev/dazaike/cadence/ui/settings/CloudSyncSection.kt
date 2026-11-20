package dev.dazaike.cadence.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dazaike.cadence.cloud.CloudApi
import dev.dazaike.cadence.cloud.CloudSyncManager
import dev.dazaike.cadence.cloud.CloudUiState
import kotlinx.coroutines.launch

/** Contents of the "Cloud sync" settings card: sign-in form when signed out, status and controls when in. */
@Composable
internal fun CloudSyncCard(cloudSync: CloudSyncManager) {
    val ui by cloudSync.ui.collectAsStateWithLifecycle(initialValue = CloudUiState())
    val settings = ui.settings
    if (settings.signedIn) {
        SignedInContent(cloudSync, ui)
    } else {
        SignedOutContent(cloudSync, ui)
    }
}

@Composable
private fun SignedOutContent(cloudSync: CloudSyncManager, ui: CloudUiState) {
    val settings = ui.settings
    val scope = rememberCoroutineScope()
    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var email by remember(settings.email) { mutableStateOf(settings.email) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val urlInvalid = serverUrl.isNotBlank() && CloudApi.normalizeServerUrl(serverUrl) == null

    fun submit(create: Boolean) {
        if (working) return
        working = true
        error = null
        scope.launch {
            val message = try {
                cloudSync.signIn(serverUrl, email, password, create)
            } finally {
                working = false
            }
            if (message == null) password = "" else error = message
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(ok = false)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Not signed in", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Optional. Sign in to copy your readings to your own Cadence cloud account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        settings.signedOutReason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server") },
            singleLine = true,
            isError = urlInvalid,
            supportingText = if (urlInvalid) {
                { Text("Use an https:// address (http is only allowed for localhost).") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text("At least 10 characters when creating an account.") },
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { submit(create = false) }, enabled = !working && !urlInvalid) { Text("Sign in") }
            OutlinedButton(onClick = { submit(create = true) }, enabled = !working && !urlInvalid) { Text("Create account") }
            if (working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        }
        Text(
            "Nothing leaves your device until you sign in. Once you do, Cadence uploads your health " +
                "readings, and your goals, to the server above over HTTPS so the web dashboard and AI " +
                "assistants can read them. Cadence still only reads from Health Connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignedInContent(cloudSync: CloudSyncManager, ui: CloudUiState) {
    val settings = ui.settings
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    val host = remember(settings.serverUrl) { settings.serverUrl.substringAfter("://") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(ok = settings.lastSyncOk)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(settings.email, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        ui.busy -> "Syncing…"
                        settings.lastSyncAt == 0L -> "Not synced yet"
                        else -> "Last synced " + DateUtils.getRelativeTimeSpanString(
                            settings.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        }
        settings.lastSyncMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.lastSyncOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { cloudSync.syncNow() }, enabled = !ui.busy) { Text("Sync now") }
            OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-sync", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "About every 15 minutes while online, and when you open the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.autoSync, onCheckedChange = { scope.launch { cloudSync.setAutoSync(it) } })
        }
        Text(
            "While signed in, your health readings and goals are uploaded to $host. Data already " +
                "uploaded stays on the server after you sign out until you delete it from the dashboard. " +
                "The local AI server above is separate and still never leaves your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Cadence will stop syncing and forget this session. Data already uploaded stays in your cloud account.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { cloudSync.signOut() }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusIcon(ok: Boolean) {
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        if (ok) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
        null,
        tint = tint,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(9.dp),
    )
}

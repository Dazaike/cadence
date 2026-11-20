package dev.dazaike.cadence.ui.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dazaike.cadence.analytics.fmtExact
import dev.dazaike.cadence.analytics.fmtSince
import dev.dazaike.cadence.cloud.CloudSyncManager
import dev.dazaike.cadence.cloud.CloudUiState
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.PillTone
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens
import kotlinx.coroutines.delay

/** One minute-ticking clock so "synced 3 min ago" stays honest without a recomposition storm. */
@Composable
private fun rememberNow(everyMs: Long = 30_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everyMs) {
        while (true) {
            delay(everyMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Sync line shared by the status card and the Data Sources page: syncing, failed, or when the last
 * sync succeeded (relative plus exact time), the on-device twin of the web `SyncLine`.
 */
@Composable
fun SyncLine(ui: CloudUiState, modifier: Modifier = Modifier) {
    val c = Cadence.colors
    val t = Cadence.type
    val now = rememberNow()
    val s = ui.settings
    Column(modifier) {
        when {
            ui.busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(Modifier.size(12.dp), color = c.textSecondary, strokeWidth = 1.5.dp)
                Text("Syncing…", style = t.caption1Regular, color = c.textSecondary)
            }
            !s.lastSyncOk -> {
                Text("Sync failed", style = t.caption1Regular, color = c.error)
                s.lastSyncMessage?.let { Text(it, style = t.caption1Regular, color = c.textTertiary) }
                if (s.lastSyncAt > 0) Text("Last synced ${fmtExact(s.lastSyncAt)}", style = t.caption1Regular, color = c.textTertiary)
            }
            else -> {
                Text(if (s.lastSyncAt > 0) "Synced ${fmtSince(s.lastSyncAt, now)}" else "Waiting for first sync", style = t.caption1Regular, color = c.textSecondary)
                if (s.lastSyncAt > 0) Text(fmtExact(s.lastSyncAt), style = t.caption1Regular, color = c.textTertiary)
            }
        }
    }
}

/**
 * The web sidebar status card: "Your data" pill, cloud sync state with last sync (relative and
 * exact), the account, and a Sync now button. Signed out it offers to sign in under Settings.
 */
@Composable
fun StatusCard(cloudSync: CloudSyncManager, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cadence.colors
    val t = Cadence.type
    val ui by cloudSync.ui.collectAsStateWithLifecycle(initialValue = CloudUiState())
    val s = ui.settings
    Column(
        modifier.fillMaxWidth().border(1.dp, c.hairline, RoundedCornerShape(Dimens.InnerRadius)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Pill("Your data", tone = PillTone.LIME)
        if (s.signedIn) {
            SyncLine(ui)
            Text(s.email, style = t.caption1Regular, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CadenceButton("Sync now", onClick = { cloudSync.syncNow() }, kind = ButtonKind.SECONDARY, small = true, loading = ui.busy, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Stored on this device only.", style = t.caption1Regular, color = c.textSecondary)
            CadenceButton("Sign in to sync", onClick = onSignIn, kind = ButtonKind.SECONDARY, small = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

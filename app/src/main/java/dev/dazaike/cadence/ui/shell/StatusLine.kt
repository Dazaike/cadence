package dev.dazaike.cadence.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dazaike.cadence.CadenceApp
import dev.dazaike.cadence.cloud.CloudUiState
import dev.dazaike.cadence.data.DataSource
import dev.dazaike.cadence.ui.theme.Cadence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun clock(ts: Long): String = TIME.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

/**
 * A line at the top of the app that appears while Cadence is working and says what it is doing
 * ("Syncing Samsung Health with your watch…", "Reading your health data (4 of 11)…",
 * "Syncing to cloud: uploading 5000 of 12000 readings…"). It collapses when nothing is running.
 */
@Composable
fun StatusLine(modifier: Modifier = Modifier) {
    val c = Cadence.colors
    val app = LocalContext.current.applicationContext as CadenceApp
    val active by app.status.active.collectAsStateWithLifecycle()
    val busy = active.isNotEmpty()
    // Keep the last message while the line animates out.
    val shown = remember { mutableStateOf("") }
    if (busy) shown.value = active.joinToString("  ·  ") { it.text }
    val text = shown.value

    AnimatedVisibility(busy, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 28.dp)
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .semantics {
                    contentDescription = text
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), color = c.accent.c600, strokeWidth = 1.5.dp)
            Text(text, style = Cadence.type.caption1Regular, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * When the on-device data was last updated and how the last cloud sync went, for a page subtitle:
 * "Updated 3:42 PM from Samsung Health · cloud synced 3:40 PM".
 */
@Composable
fun rememberIdleStatus(loadedAt: Long, source: DataSource): String {
    val app = LocalContext.current.applicationContext as CadenceApp
    val cloud by app.cloudSync.ui.collectAsStateWithLifecycle(initialValue = CloudUiState())
    val name = if (source == DataSource.SAMSUNG_HEALTH) "Samsung Health" else "Health Connect"
    return buildList {
        add("Updated ${clock(loadedAt)} from $name")
        val s = cloud.settings
        if (s.signedIn && s.lastSyncAt > 0L) {
            add(if (s.lastSyncOk) "cloud synced ${clock(s.lastSyncAt)}" else "cloud sync failed" + (s.lastSyncMessage?.let { ": $it" } ?: ""))
        }
    }.joinToString("  ·  ")
}

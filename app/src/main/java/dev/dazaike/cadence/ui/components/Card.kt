package dev.dazaike.cadence.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens

/** Flat neutral card surface: 24 dp radius, hairline border, tiny shadow (web `rounded-3xl` card). */
@Composable
fun Modifier.cardSurface(padded: Boolean = true): Modifier {
    val c = Cadence.colors
    val shape = RoundedCornerShape(Dimens.CardRadius)
    return this
        .shadow(1.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (c.dark) 0.5f else 0.18f), spotColor = Color.Black.copy(alpha = if (c.dark) 0.5f else 0.18f))
        .clip(shape)
        .background(c.card)
        .border(BorderStroke(1.dp, c.border), shape)
        .then(if (padded) Modifier.padding(Dimens.CardPadding) else Modifier)
}

/**
 * The web `Card`: title row with a small tinted category icon (only the icon is tinted, the surface
 * stays neutral), optional info sheet and full-screen expand. [content] receives `full` = true when
 * shown full-screen so charts can grow.
 */
@Composable
fun CadenceCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    color: Color? = null,
    info: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    expandable: Boolean = false,
    padded: Boolean = true,
    content: @Composable ColumnScope.(full: Boolean) -> Unit,
) {
    val c = Cadence.colors
    var full by remember { mutableStateOf(false) }
    Column(modifier.cardSurface(padded)) {
        if (title != null || actions != null || expandable) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = color ?: c.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (title != null) {
                        Text(title, style = Cadence.type.bodySemibold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (info != null) {
                        Spacer(Modifier.width(8.dp))
                        InfoTip(info)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions?.invoke(this)
                    if (expandable) {
                        Icon(
                            Icons.Rounded.Fullscreen, null, tint = c.textTertiary,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .semantics { contentDescription = "Full screen" }
                                .clickable(role = Role.Button) { full = true }
                                .padding(3.dp),
                        )
                    }
                }
            }
        }
        content(false)
    }
    if (expandable && full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(c.card).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title ?: "Expanded view", style = Cadence.type.headlineSemibold, color = c.textPrimary, modifier = Modifier.weight(1f))
                    CadenceIconButton(Icons.Rounded.Close, "Close", onClick = { full = false })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    content(true)
                }
            }
        }
    }
}

/** The small (i) beside a card title; opens the explanation in a bottom sheet (touch has no hover). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTip(text: String, modifier: Modifier = Modifier) {
    val c = Cadence.colors
    var open by remember { mutableStateOf(false) }
    Icon(
        Icons.Outlined.Info, null, tint = c.textTertiary,
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "How is this calculated?" }
            .clickable(role = Role.Button) { open = true }
            .padding(4.dp),
    )
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
            contentColor = c.textPrimary,
            shape = RoundedCornerShape(topStart = Dimens.CardRadius, topEnd = Dimens.CardRadius),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text("How is this calculated?", style = Cadence.type.headlineSemibold, color = c.textPrimary)
                Spacer(Modifier.size(8.dp))
                Text(text, style = Cadence.type.bodyRegular, color = c.textSecondary)
            }
        }
    }
}

/** A bordered inline note (web `rounded-2xl border border-separator-border px-4 py-2` notices). */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, raised: Boolean = false) {
    val c = Cadence.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.InnerRadius))
            .then(if (raised) Modifier.background(c.card) else Modifier)
            .border(1.dp, c.hairline, RoundedCornerShape(Dimens.InnerRadius))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.iconSecondary, modifier = Modifier.padding(top = 2.dp).size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = Cadence.type.caption1Regular, color = c.textSecondary)
    }
}

package dev.dazaike.cadence.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.Motion
import dev.dazaike.cadence.ui.theme.Palette
import dev.dazaike.cadence.ui.theme.Tone
import dev.dazaike.cadence.ui.theme.reduced
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/* Pill (web `Pill` / Chip subtle)                                     */
/* ------------------------------------------------------------------ */

enum class PillTone { NEUTRAL, YELLOW, LIME, ROSE, PURPLE, ACCENT }

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.NEUTRAL) {
    val c = Cadence.colors
    val t: Tone = when (tone) {
        PillTone.NEUTRAL -> c.toneNeutral
        PillTone.YELLOW -> c.toneYellow
        PillTone.LIME -> c.toneLime
        PillTone.ROSE -> c.toneRose
        PillTone.PURPLE -> c.tonePurple
        PillTone.ACCENT -> c.toneAccent
    }
    Box(
        modifier.clip(CircleShape).background(t.bg).padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Cadence.type.caption1Medium, color = t.fg, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ */
/* Segmented control with a shared, spring-animated selection          */
/* ------------------------------------------------------------------ */

@Composable
fun <T> Segmented(
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val c = Cadence.colors
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val selectedIndex = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val target = bounds[selectedIndex]
    val x by animateFloatAsState(target?.first ?: 0f, reduced(Motion.highlight()), label = "segX")
    val w by animateFloatAsState(target?.second ?: 0f, reduced(Motion.highlight()), label = "segW")
    val shape = RoundedCornerShape(Dimens.ControlRadius)
    val selectedFill = if (c.dark) Palette.Neutral800 else c.card
    Box(
        modifier
            .clip(shape)
            .background(c.pageBg)
            .border(1.dp, c.hairline, shape)
            .horizontalScroll(rememberScrollState())
            .padding(3.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        if (target != null) {
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .width(with(density) { w.toDp() })
                    .height(32.dp)
                    .shadow(if (c.dark) 0.dp else 1.dp, RoundedCornerShape(9.dp), clip = false)
                    .clip(RoundedCornerShape(9.dp))
                    .background(selectedFill),
            )
        }
        Row {
            options.forEachIndexed { i, (key, label) ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .height(32.dp)
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(role = Role.Tab) { onChange(key) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Cadence.type.caption1Medium, color = if (selected) c.textPrimary else c.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Buttons                                                             */
/* ------------------------------------------------------------------ */

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, GHOST }

@Composable
fun CadenceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    small: Boolean = false,
) {
    val c = Cadence.colors
    val (bg, fg, stroke) = when (kind) {
        ButtonKind.PRIMARY -> Triple(c.accent.c600, c.onFill, null)
        ButtonKind.SECONDARY -> Triple(c.card, c.textPrimary, c.border)
        ButtonKind.DANGER -> Triple(if (c.dark) Palette.Red500 else Palette.Rose600, c.onFill, null)
        ButtonKind.GHOST -> Triple(c.toneAccent.bg, c.toneAccent.fg, null)
    }
    val shape = RoundedCornerShape(Dimens.ControlRadius)
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .defaultMinSize(minHeight = if (small) 32.dp else 44.dp)
            .clip(shape)
            .background(bg.copy(alpha = bg.alpha * alpha))
            .then(if (stroke != null) Modifier.border(BorderStroke(1.dp, stroke), shape) else Modifier)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(14.dp), color = fg, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(if (small) 14.dp else 16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = if (small) Cadence.type.caption1Semibold else Cadence.type.bodyMedium, color = fg.copy(alpha = alpha), maxLines = 1)
    }
}

@Composable
fun CadenceIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val c = Cadence.colors
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Dimens.ControlRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = (tint ?: c.iconSecondary).copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(20.dp))
    }
}

/* ------------------------------------------------------------------ */
/* Switch, text field, menu, dialogs                                   */
/* ------------------------------------------------------------------ */

@Composable
fun CadenceSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Cadence.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent.c600,
            checkedBorderColor = c.accent.c600,
            uncheckedThumbColor = if (c.dark) Palette.Neutral400 else Color.White,
            uncheckedTrackColor = c.raised,
            uncheckedBorderColor = c.border,
        ),
    )
}

@Composable
fun CadenceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    suffix: String? = null,
) {
    val c = Cadence.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        suffix = suffix?.let { { Text(it, color = c.textSecondary) } },
        shape = RoundedCornerShape(Dimens.ControlRadius),
        textStyle = Cadence.type.bodyRegular,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = c.accent.c600,
            unfocusedIndicatorColor = c.border,
            disabledIndicatorColor = c.hairline,
            errorIndicatorColor = c.error,
            focusedLabelColor = c.accent.c600,
            unfocusedLabelColor = c.textSecondary,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            disabledTextColor = c.textTertiary,
            cursorColor = c.accent.c600,
            focusedSupportingTextColor = c.textSecondary,
            unfocusedSupportingTextColor = c.textSecondary,
            errorSupportingTextColor = c.error,
        ),
    )
}

/** Compact select: a bordered 12 dp control showing the current value; opens a menu of options. */
@Composable
fun <T> SelectMenu(
    selected: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    val c = Cadence.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Dimens.ControlRadius)
    Box(modifier) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(shape)
                .background(c.card)
                .border(1.dp, c.border, shape)
                .clickable(role = Role.DropdownList) { open = true }
                .semantics { this.contentDescription = contentDescription }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "", style = Cadence.type.bodyMedium, color = c.textPrimary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.UnfoldMore, null, tint = c.iconSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.card, shape = shape, border = BorderStroke(1.dp, c.border)) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = Cadence.type.bodyMedium, color = if (key == selected) c.accent.c600 else c.textPrimary) },
                    onClick = {
                        open = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

/** Modal confirmation, the on-device twin of the web `ConfirmAction` dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val c = Cadence.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(Dimens.CardRadius),
        title = { Text(title, style = Cadence.type.headlineSemibold, color = c.textPrimary) },
        text = { Text(description, style = Cadence.type.bodyRegular, color = c.textSecondary) },
        confirmButton = {
            CadenceButton(confirmLabel, onClick = { onDismiss(); onConfirm() }, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, small = true)
        },
        dismissButton = { CadenceButton("Cancel", onClick = onDismiss, kind = ButtonKind.SECONDARY, small = true) },
    )
}

/** Generic modal with the app's card styling; [body] is arbitrary content. */
@Composable
fun CadenceDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    body: @Composable () -> Unit,
) {
    val c = Cadence.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = c.card,
        shape = RoundedCornerShape(Dimens.CardRadius),
        title = { Text(title, style = Cadence.type.headlineSemibold, color = c.textPrimary) },
        text = { body() },
        confirmButton = { CadenceButton(confirmLabel, onClick = onConfirm, enabled = confirmEnabled, small = true) },
        dismissButton = { CadenceButton("Cancel", onClick = onDismiss, kind = ButtonKind.SECONDARY, small = true) },
    )
}

/** A label on the left, a control on the right (web Settings `Row`). */
@Composable
fun SettingRow(label: String, modifier: Modifier = Modifier, control: @Composable RowScope.() -> Unit) {
    Row(
        modifier.defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Cadence.type.bodyRegular, color = Cadence.colors.textPrimary, modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp))
        control()
    }
}

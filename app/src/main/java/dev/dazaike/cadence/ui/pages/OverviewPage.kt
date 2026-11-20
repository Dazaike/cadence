package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.fmtAgo
import dev.dazaike.cadence.data.Layout
import dev.dazaike.cadence.data.WidgetDefaults
import dev.dazaike.cadence.data.WidgetSize
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.CadenceDialog
import dev.dazaike.cadence.ui.components.CadenceIconButton
import dev.dazaike.cadence.ui.components.CadenceTextField
import dev.dazaike.cadence.ui.components.SelectMenu
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens

private val GAP = Dimens.PageGap

/**
 * Port of `pages/Overview.tsx`: the customisable widget grid with named layouts, hide/show and S/M/L
 * sizes. On a phone S is half width and M/L are full width; wide screens use two or three columns.
 * Reordering uses Edit mode with move up/down buttons instead of the web's drag handle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewPage() {
    val app = LocalApp.current
    val view = app.view
    val layouts = app.prefs.layouts.layouts
    val layout = app.prefs.layout
    var editing by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    val c = Cadence.colors

    val order = layout.order
    val visible = order.filter { it !in layout.hidden }

    fun update(change: (Layout) -> Layout) = app.actions.updateLayout(layout.id, change)

    fun move(id: String, dir: Int) {
        val from = order.indexOf(id)
        val neighbour = if (dir < 0) order.subList(0, from).lastOrNull { it in visible } else order.subList(from + 1, order.size).firstOrNull { it in visible }
        if (neighbour == null) return
        val to = order.indexOf(neighbour)
        update { l -> l.copy(order = order.toMutableList().also { it[from] = neighbour; it[to] = id }) }
    }

    PageColumn {
        PageHeader(
            "Overview",
            subtitle = view.last?.let { d -> "Data through $d · updated ${fmtAgo(d.atTime(12, 0).atZone(view.zone).toInstant().toEpochMilli())}" } ?: "No data yet",
            controls = {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    SelectMenu(layout.id, layouts.map { it.id to it.name }, app.actions::setActiveLayout, contentDescription = "Dashboard layout")
                    CadenceButton(if (editing) "Done" else "Edit", onClick = { editing = !editing }, kind = if (editing) ButtonKind.PRIMARY else ButtonKind.SECONDARY, icon = if (editing) Icons.Rounded.Done else Icons.Rounded.Edit, small = true)
                    CadenceButton("New layout", onClick = { draftName = "Layout ${layouts.size + 1}"; naming = true }, kind = ButtonKind.SECONDARY, icon = Icons.Rounded.Add, small = true)
                    CadenceButton("Reset", onClick = { app.actions.resetLayout(layout.id) }, kind = ButtonKind.SECONDARY, icon = Icons.Rounded.RestartAlt, small = true)
                    if (layouts.size > 1) CadenceIconButton(Icons.Rounded.Delete, "Delete layout", onClick = { app.actions.removeLayout(layout.id) })
                }
            },
        )

        if (layout.hidden.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("Hidden:", style = Cadence.type.caption1Regular, color = c.textSecondary)
                layout.hidden.forEach { id ->
                    CadenceButton(
                        WIDGETS.firstOrNull { it.id == id }?.title ?: id,
                        onClick = { update { l -> l.copy(hidden = l.hidden - id) } },
                        kind = ButtonKind.SECONDARY, icon = Icons.Rounded.Visibility, small = true,
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (LocalWide.current && maxWidth >= 900.dp) 3 else 2
            val colWidth = (maxWidth - GAP * (cols - 1)) / cols
            class Cell(val id: String, val span: Int)
            val cells = visible.mapNotNull { id ->
                if (WIDGETS.none { it.id == id }) return@mapNotNull null
                val size = layout.sizes[id] ?: WidgetDefaults.SIZES[id] ?: WidgetSize.S
                Cell(id, minOf(cols, when (size) { WidgetSize.S -> 1; WidgetSize.M -> 2; WidgetSize.L -> cols }))
            }
            // CSS-grid style packing: fill a row left to right, start a new one when the next card does not fit.
            val rows = ArrayList<List<Cell>>()
            var current = ArrayList<Cell>()
            var used = 0
            for (cell in cells) {
                if (used + cell.span > cols) {
                    rows.add(current)
                    current = ArrayList()
                    used = 0
                }
                current.add(cell)
                used += cell.span
            }
            if (current.isNotEmpty()) rows.add(current)

            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        row.forEach { cell ->
                            val w = WIDGETS.first { it.id == cell.id }
                            val size = layout.sizes[cell.id] ?: WidgetDefaults.SIZES[cell.id] ?: WidgetSize.S
                            val cardWidth = colWidth * cell.span + GAP * (cell.span - 1)
                            Box(Modifier.width(cardWidth).fillMaxHeight()) {
                                CadenceCard(
                                    modifier = Modifier.fillMaxHeight(),
                                    title = w.title, icon = w.icon, color = w.color, info = w.info, expandable = true,
                                    actions = if (!editing) null else ({
                                        SizeChip(size) { update { l -> l.copy(sizes = l.sizes + (cell.id to size.next)) } }
                                        MiniAction(Icons.Rounded.VisibilityOff, "Hide widget") { update { l -> l.copy(hidden = l.hidden + cell.id) } }
                                        MiniAction(Icons.Rounded.ArrowUpward, "Move up", enabled = visible.first() != cell.id) { move(cell.id, -1) }
                                        MiniAction(Icons.Rounded.ArrowDownward, "Move down", enabled = visible.last() != cell.id) { move(cell.id, 1) }
                                    }),
                                ) { full ->
                                    CompositionLocalProvider(LocalWidgetWidth provides if (full) Dp.Unspecified else cardWidth) { w.content() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (naming) {
        CadenceDialog(
            title = "New layout",
            onDismiss = { naming = false },
            confirmLabel = "Create",
            onConfirm = {
                app.actions.addLayout(draftName.trim().ifEmpty { "Layout ${layouts.size + 1}" }, layout.id)
                naming = false
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Copies the current layout so you can change it freely.", style = Cadence.type.bodyRegular, color = c.textSecondary)
                CadenceTextField(draftName, { draftName = it }, "Layout name", Modifier.fillMaxWidth())
            }
        }
    }
}

/** The S/M/L button on a card in Edit mode; each tap cycles the size (web `Widget size` button). */
@Composable
private fun SizeChip(size: WidgetSize, onClick: () -> Unit) {
    val c = Cadence.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, c.hairline, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Widget size ${size.name}, tap to change" }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(size.name, style = Cadence.type.caption2Semibold, color = c.textSecondary)
    }
}

@Composable
private fun MiniAction(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Cadence.colors
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) c.textTertiary else c.textTertiary.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
    }
}

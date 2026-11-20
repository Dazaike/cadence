package dev.dazaike.cadence.ui.pages

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.data.Layout
import dev.dazaike.cadence.data.WidgetDefaults
import dev.dazaike.cadence.data.WidgetSize
import dev.dazaike.cadence.ui.components.ButtonKind
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.shell.rememberIdleStatus
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens

private val GAP = Dimens.PageGap

/**
 * Port of `pages/Overview.tsx`: the widget grid for the active layout. On a phone every widget is
 * full width; wide screens use two or three columns. The subtitle is the data's last update and
 * cloud sync status; what is happening right now shows in the status line at the top of the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewPage() {
    val app = LocalApp.current
    val layout = app.prefs.layout
    val c = Cadence.colors

    val visible = layout.order.filter { it !in layout.hidden }

    fun update(change: (Layout) -> Layout) = app.actions.updateLayout(layout.id, change)

    PageColumn {
        PageHeader("Overview", subtitle = rememberIdleStatus(app.loaded.loadedAt, app.services.source))

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
            // Phones get one full-width column (half-width cards squeezed titles and rows); wide screens use two or three.
            val cols = if (LocalWide.current) (if (maxWidth >= 900.dp) 3 else 2) else 1
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
                            val cardWidth = colWidth * cell.span + GAP * (cell.span - 1)
                            Box(Modifier.width(cardWidth).fillMaxHeight()) {
                                CadenceCard(
                                    modifier = Modifier.fillMaxHeight(),
                                    title = w.title, icon = w.icon, color = w.color, info = w.info,
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
}

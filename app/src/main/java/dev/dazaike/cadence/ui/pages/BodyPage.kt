package dev.dazaike.cadence.ui.pages

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.analytics.MetricKey
import dev.dazaike.cadence.analytics.Sample
import dev.dazaike.cadence.analytics.bmi
import dev.dazaike.cadence.analytics.dayKey
import dev.dazaike.cadence.analytics.fmtDay
import dev.dazaike.cadence.analytics.heightAt
import dev.dazaike.cadence.ui.charts.ChartPoint
import dev.dazaike.cadence.ui.charts.TrendChart
import dev.dazaike.cadence.ui.components.CadenceCard
import dev.dazaike.cadence.ui.components.Empty
import dev.dazaike.cadence.ui.components.Notice
import dev.dazaike.cadence.ui.components.Pill
import dev.dazaike.cadence.ui.components.Stat
import dev.dazaike.cadence.ui.components.cardSurface
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.theme.CatColor

private class WeightRow(val w: Sample, val bmi: Double?, val h: Sample?)

@Composable
fun BodyPage() {
    val app = LocalApp.current
    val view = app.view
    val fmt = app.fmt
    val color = CatColor.body
    PageColumn {
        if (view.weight.isEmpty() && view.height.isEmpty()) {
            PageHeader("Body")
            NoData("weight or height")
            return@PageColumn
        }
        fun dayOf(t: Long) = dayKey(t, view.zone)
        val weights = view.weight.map { w ->
            val h = heightAt(view, w.t)
            WeightRow(w, h?.let { bmi(w.v, it.v) }, h)
        }
        val latest = weights.lastOrNull()
        val wData = weights.map { ChartPoint(dayOf(it.w.t), fmtDay(dayOf(it.w.t), true), fmt.c(MetricKey.WEIGHT, it.w.v)) }
        val hData = view.height.map { ChartPoint(dayOf(it.t), fmtDay(dayOf(it.t), true), fmt.c(MetricKey.HEIGHT, it.v)) }
        val bData = weights.filter { it.bmi != null }.map { ChartPoint(dayOf(it.w.t), fmtDay(dayOf(it.w.t), true), it.bmi) }
        val delta = if (weights.size > 1) fmt.c(MetricKey.WEIGHT, weights.last().w.v - weights.first().w.v) else null
        val lastHeight = view.height.lastOrNull()
        val heightDecimals = fmt.decimals(MetricKey.HEIGHT)
        val heightFactor = Math.pow(10.0, heightDecimals.toDouble())

        PageHeader("Body", subtitle = "Weight and body measurements")

        StatRowsGrid(
            wideCols = 4,
            modifier = Modifier.cardSurface(),
            items = listOf(
                {
                    Stat(
                        "Weight", latest?.let { round1(fmt.c(MetricKey.WEIGHT, it.w.v)) },
                        sub = latest?.let { fmtDay(dayOf(it.w.t), true) } ?: "no data",
                        color = color, decimals = 1, unit = fmt.u(MetricKey.WEIGHT),
                    )
                },
                {
                    val b = latest?.bmi
                    val h = latest?.h
                    Stat(
                        "BMI", b?.let { round1(it) },
                        sub = if (b != null && h != null) "at ${fmt.f(MetricKey.HEIGHT, h.v)}" else "needs height + weight",
                        decimals = 1,
                    )
                },
                {
                    Stat(
                        "Height", lastHeight?.let { Math.round(fmt.c(MetricKey.HEIGHT, it.v) * heightFactor) / heightFactor },
                        sub = lastHeight?.let { fmtDay(dayOf(it.t), true) },
                        decimals = heightDecimals, unit = fmt.u(MetricKey.HEIGHT),
                    )
                },
                {
                    Stat(
                        "Change since first reading", delta?.let { round1(it) },
                        sub = "${weights.size} readings", decimals = 1, unit = fmt.u(MetricKey.WEIGHT),
                    )
                },
            ),
        )

        if (weights.size < 12) {
            Notice("Only ${weights.size} reading${if (weights.size == 1) "" else "s"} exist, so lines connect sparse points. Long gaps between readings are not measurements.")
        }

        AdaptiveRow(
            listOf(1f, 1f),
            {
                CadenceCard(title = "Weight history", color = color, expandable = true, info = "Every recorded reading, in chronological order.") { full ->
                    if (wData.size > 1) {
                        TrendChart(wData, color, fmt.u(MetricKey.WEIGHT), decimals = 1, height = if (full) 480.dp else 260.dp, name = "Weight")
                    } else Empty("Need at least two readings")
                }
            },
            {
                CadenceCard(
                    title = "BMI history", color = color,
                    info = "BMI = 703 × lb ÷ in² (the same as kg ÷ m²). It does not distinguish muscle from fat and is not a diagnosis. Each reading uses the latest height recorded on or before it.",
                ) {
                    if (bData.size > 1) TrendChart(bData, color, "", decimals = 1, height = 260.dp, name = "BMI") else Empty("Need height and 2+ weights")
                }
            },
        )

        val glucose = if (view.has(MetricKey.GLUCOSE)) view.latest(MetricKey.GLUCOSE) else null
        val glucoseAction: (@Composable RowScope.() -> Unit)? = if (glucose == null) {
            { Pill("none") }
        } else null

        AdaptiveRow(
            listOf(1f, 1f, 1f),
            {
                CadenceCard(title = "Height readings", color = color) {
                    if (hData.size > 1) {
                        TrendChart(hData, color, fmt.u(MetricKey.HEIGHT), decimals = 0, height = 180.dp, name = "Height")
                    } else Empty("One reading")
                }
            },
            {
                CadenceCard(title = "Body composition", color = color) {
                    Empty("Not recorded", hint = "Body-fat %, lean mass and similar values are not part of this source.")
                }
            },
            {
                CadenceCard(title = "Blood pressure & glucose", color = color, actions = glucoseAction) {
                    if (glucose != null) {
                        Stat("Latest glucose", Math.round(glucose.v).toDouble(), unit = "mg/dL")
                    } else {
                        Empty("No readings recorded", hint = "This source has no blood-pressure or blood-glucose data.")
                    }
                }
            },
        )
    }
}

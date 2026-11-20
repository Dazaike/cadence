package dev.dazaike.cadence.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.dazaike.cadence.CadenceApp
import dev.dazaike.cadence.data.AppSettings
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.CadenceTheme
import kotlinx.coroutines.launch

class MetricWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If the user backs out, the widget host must not place the widget.
        setResult(Activity.RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = (application as CadenceApp).appPrefs
        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CadenceTheme(mode = settings.themeMode, accentId = settings.accentId) {
                Box(Modifier.fillMaxSize().background(Cadence.colors.pageBg)) {
                    MetricPicker(onPick = { metric -> save(appWidgetId, metric) })
                }
            }
        }
    }

    private fun save(appWidgetId: Int, metric: Metric) {
        val app = applicationContext
        // Write the choice + computed data into Glance state BEFORE returning RESULT_OK, so the
        // widget's first post-bind render already shows the right metric, no delayed "settle".
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[METRIC_KEY] = metric.key }
                }
                MetricWidget.refreshData(app, glanceId)
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
private fun MetricPicker(onPick: (Metric) -> Unit) {
    val c = Cadence.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Choose a metric", style = Cadence.type.title2Medium, color = c.textPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            items(Metric.entries.toList()) { metric ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(metric) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(metric.icon, null, tint = metric.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(metric.title, style = Cadence.type.bodyMedium, color = c.textPrimary)
                }
            }
        }
    }
}

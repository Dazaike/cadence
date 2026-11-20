package dev.dazaike.cadence.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dazaike.cadence.analytics.Gran
import dev.dazaike.cadence.analytics.GoalKey
import dev.dazaike.cadence.analytics.RangePref
import dev.dazaike.cadence.data.Layout
import dev.dazaike.cadence.data.Metric
import dev.dazaike.cadence.data.UnitSystem
import dev.dazaike.cadence.ui.components.BrandHeader
import dev.dazaike.cadence.ui.components.PageSkeleton
import dev.dazaike.cadence.ui.components.cardSurface
import dev.dazaike.cadence.ui.pages.ActivityPage
import dev.dazaike.cadence.ui.pages.BodyPage
import dev.dazaike.cadence.ui.pages.CalendarPage
import dev.dazaike.cadence.ui.pages.HeartPage
import dev.dazaike.cadence.ui.pages.InsightsPage
import dev.dazaike.cadence.ui.pages.OverviewPage
import dev.dazaike.cadence.ui.pages.SettingsPage
import dev.dazaike.cadence.ui.pages.SleepPage
import dev.dazaike.cadence.ui.pages.SourcesPage
import dev.dazaike.cadence.ui.state.AppActions
import dev.dazaike.cadence.ui.state.AppCtx
import dev.dazaike.cadence.ui.state.AppServices
import dev.dazaike.cadence.ui.state.AppViewModel
import dev.dazaike.cadence.ui.state.LocalApp
import dev.dazaike.cadence.ui.state.LocalBottomInset
import dev.dazaike.cadence.ui.state.LocalWide
import dev.dazaike.cadence.ui.state.Route
import dev.dazaike.cadence.ui.theme.Cadence
import dev.dazaike.cadence.ui.theme.Dimens
import dev.dazaike.cadence.ui.theme.LocalReduceMotion
import dev.dazaike.cadence.ui.theme.Motion
import dev.dazaike.cadence.ui.theme.Palette
import dev.dazaike.cadence.ui.theme.ThemeMode
import dev.dazaike.cadence.ui.theme.reduced
import java.time.LocalDate
import kotlin.math.roundToInt

private val BAR_HEIGHT = 64.dp
private val BAR_MARGIN = 12.dp
private val RAIL_WIDTH = 224.dp
private val RAIL_ITEM = 44.dp

/**
 * The app frame (web `App.tsx`): a floating rounded bottom bar with five tabs and a More menu on
 * phones, a left rail on wide screens, a page area with fade + slide transitions, and pull to
 * refresh. Pages read everything from [LocalApp].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceShell(
    vm: AppViewModel,
    services: AppServices,
    deepLinkMetric: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val c = Cadence.colors
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val routeState = rememberSaveable { mutableStateOf(Route.OVERVIEW) }
    val route = routeState.value

    LifecycleResumeEffect(Unit) {
        vm.refreshIfStale()
        onPauseOrDispose { }
    }
    LaunchedEffect(deepLinkMetric) {
        Metric.fromKey(deepLinkMetric.orEmpty())?.let {
            routeState.value = Route.forMetric(it)
            onDeepLinkConsumed()
        }
    }
    BackHandler(enabled = route != Route.OVERVIEW) { routeState.value = Route.OVERVIEW }

    val actions = remember(vm) {
        object : AppActions {
            override fun navigate(route: Route) { routeState.value = route }
            override fun openDay(day: LocalDate) {
                vm.selectDay(day)
                routeState.value = Route.CALENDAR
            }
            override fun selectDay(day: LocalDate?) = vm.selectDay(day)
            override fun setRange(range: RangePref) = vm.setRange(range)
            override fun setGran(gran: Gran) = vm.setGran(gran)
            override fun setUnits(units: UnitSystem) = vm.setUnits(units)
            override fun setGoal(key: GoalKey, value: Double) = vm.setGoal(key, value)
            override fun resetGoals() = vm.resetGoals()
            override fun setMaxHr(bpm: Int) = vm.setMaxHr(bpm)
            override fun setThemeMode(mode: ThemeMode) = vm.setThemeMode(mode)
            override fun setAccent(id: String) = vm.setAccent(id)
            override fun resetPreferences() = vm.resetPreferences()
            override fun updateLayout(id: String, change: (Layout) -> Layout) = vm.updateLayout(id, change)
            override fun refresh() = vm.refresh(userInitiated = true)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.pageBg)) {
        val wide = maxWidth >= Dimens.WideBreakpointDp.dp
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomInset = if (wide) 0.dp else BAR_HEIGHT + BAR_MARGIN * 2 + navBottom
        val p = prefs
        val l = loaded
        val ctx = if (p != null && l != null) AppCtx(l.view, l, p, selectedDay, refreshing, services, actions) else null

        CompositionLocalProvider(LocalWide provides wide, LocalBottomInset provides bottomInset) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Rail(route, { routeState.value = it }, services, p?.settings?.themeMode ?: ThemeMode.SYSTEM, actions::setThemeMode) { routeState.value = Route.SETTINGS }
                    Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
                        StatusLine()
                        Page(ctx, route, refreshing, vm, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        StatusLine()
                        Page(ctx, route, refreshing, vm, Modifier.weight(1f).fillMaxWidth())
                    }
                    BottomBar(route, { routeState.value = it }, Modifier.align(Alignment.BottomCenter).padding(horizontal = BAR_MARGIN).padding(bottom = BAR_MARGIN + navBottom))
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(ctx: AppCtx?, route: Route, refreshing: Boolean, vm: AppViewModel, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    PullToRefreshBox(isRefreshing = refreshing && ctx != null, onRefresh = { vm.refresh(userInitiated = true) }, modifier = modifier) {
        if (ctx == null) {
            PageSkeleton()
        } else {
            CompositionLocalProvider(LocalApp provides ctx) {
                val offset = with(LocalDensity.current) { 12.dp.roundToPx() }
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        if (reduce) EnterTransition.None togetherWith ExitTransition.None
                        else (fadeIn(tween(Motion.PAGE_MS)) + slideInVertically(tween(Motion.PAGE_MS)) { offset }) togetherWith fadeOut(tween(100))
                    },
                    label = "page",
                ) { target ->
                    when (target) {
                        Route.OVERVIEW -> OverviewPage()
                        Route.ACTIVITY -> ActivityPage()
                        Route.HEART -> HeartPage()
                        Route.SLEEP -> SleepPage()
                        Route.BODY -> BodyPage()
                        Route.INSIGHTS -> InsightsPage()
                        Route.CALENDAR -> CalendarPage()
                        Route.SOURCES -> SourcesPage()
                        Route.SETTINGS -> SettingsPage()
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Phone: floating bottom bar                                          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(route: Route, onRoute: (Route) -> Unit, modifier: Modifier) {
    val c = Cadence.colors
    val density = LocalDensity.current
    var more by remember { mutableStateOf(false) }
    val slots = Route.TABS.size + 1
    val index = Route.TABS.indexOf(route).let { if (it < 0) slots - 1 else it }
    val shape = RoundedCornerShape(Dimens.CardRadius)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = if (c.dark) 0.7f else 0.2f), spotColor = Color.Black.copy(alpha = if (c.dark) 0.7f else 0.2f))
            .clip(shape)
            .background(c.toneAccent.bg.compositeOver(c.card))
            .border(1.dp, c.accent.c600.copy(alpha = 0.35f), shape)
            .semantics { contentDescription = "Sections" },
    ) {
        val itemWidth = (maxWidth - 12.dp) / slots
        val x by animateFloatAsState(with(density) { (itemWidth * index).toPx() }, reduced(Motion.highlight()), label = "barHighlight")
        Box(Modifier.padding(6.dp)) {
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .width(itemWidth)
                    .height(BAR_HEIGHT - 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.accent.c600),
            )
            Row(Modifier.fillMaxWidth()) {
                Route.TABS.forEach { r -> BarItem(r.label, r.icon, route == r, Modifier.weight(1f)) { onRoute(r) } }
                BarItem("More", Icons.Rounded.MoreHoriz, route in Route.MORE, Modifier.weight(1f)) { more = true }
            }
        }
    }

    if (more) {
        ModalBottomSheet(
            onDismissRequest = { more = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
            contentColor = c.textPrimary,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Route.MORE.forEach { r ->
                    val active = route == r
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) c.pageBg else Color.Transparent)
                            .clickable(role = Role.Button) {
                                more = false
                                onRoute(r)
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(r.icon, null, tint = if (active) c.accentStrong else c.iconSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(r.label, style = Cadence.type.bodyMedium, color = if (active) c.accentStrong else c.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Cadence.colors
    Box(
        modifier
            .height(BAR_HEIGHT - 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                selected = active
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) c.onFill else c.toneAccent.fg, modifier = Modifier.size(24.dp))
    }
}

/* ------------------------------------------------------------------ */
/* Wide: left rail                                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun Rail(route: Route, onRoute: (Route) -> Unit, services: AppServices, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit, onSignIn: () -> Unit) {
    val c = Cadence.colors
    val density = LocalDensity.current
    val index = Route.entries.indexOf(route)
    val y by animateFloatAsState(with(density) { ((RAIL_ITEM + 2.dp) * index).toPx() }, reduced(Motion.highlight()), label = "railHighlight")
    Column(
        Modifier
            .statusBarsPadding()
            .padding(12.dp)
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .shadow(6.dp, RoundedCornerShape(Dimens.CardRadius), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(Dimens.CardRadius))
            .background(c.card)
            .padding(12.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        BrandHeader(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp))
        Box(Modifier.semantics { contentDescription = "Sections" }) {
            Box(
                Modifier
                    .offset { IntOffset(0, y.roundToInt()) }
                    .fillMaxWidth()
                    .height(RAIL_ITEM)
                    .clip(RoundedCornerShape(Dimens.ControlRadius))
                    .background(c.pageBg),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Route.entries.forEach { r ->
                    val active = r == route
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(RAIL_ITEM)
                            .clip(RoundedCornerShape(Dimens.ControlRadius))
                            .clickable(role = Role.Tab) { onRoute(r) }
                            .semantics { selected = active }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(r.icon, null, tint = if (active) c.accentStrong else c.iconSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(r.label, style = Cadence.type.bodyMedium, color = if (active) c.textPrimary else c.textSecondary)
                    }
                }
            }
        }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            StatusCard(services.cloudSync, onSignIn = onSignIn)
            ThemeToggle(themeMode, onThemeMode)
        }
    }
}

/** Three-way System / Light / Dark switch for the rail (web `ThemeToggle` sidebar-segmented). */
@Composable
private fun ThemeToggle(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    val c = Cadence.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(Dimens.ControlRadius)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(c.pageBg).padding(3.dp)) {
        val w = maxWidth / 3
        val x by animateFloatAsState(with(density) { (w * ThemeMode.entries.indexOf(mode)).toPx() }, reduced(Motion.highlight()), label = "themeHighlight")
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .width(w)
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (c.dark) Palette.Neutral800 else c.card),
        )
        Row {
            listOf(ThemeMode.SYSTEM to Icons.Rounded.BrightnessAuto, ThemeMode.LIGHT to Icons.Rounded.LightMode, ThemeMode.DARK to Icons.Rounded.DarkMode).forEach { (m, icon) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(role = Role.RadioButton) { onMode(m) }
                        .semantics { contentDescription = m.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (m == mode) c.textPrimary else c.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

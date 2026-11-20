package dev.dazaike.cadence.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.error.HealthDataException
import dev.dazaike.cadence.CadenceApp
import dev.dazaike.cadence.cloud.CloudSyncManager
import dev.dazaike.cadence.data.DashboardLayoutRepository
import dev.dazaike.cadence.data.DataSource
import dev.dazaike.cadence.data.DataSourcePreferences
import dev.dazaike.cadence.data.ExportManager
import dev.dazaike.cadence.data.GoalsRepository
import dev.dazaike.cadence.data.HealthConnectManager
import dev.dazaike.cadence.data.HealthDataSource
import dev.dazaike.cadence.data.UnitPreferences
import dev.dazaike.cadence.data.WidgetPreferences
import dev.dazaike.cadence.mcp.McpServerPreferences
import dev.dazaike.cadence.samsung.SamsungHealthManager
import dev.dazaike.cadence.ui.onboarding.LoadingScreen
import dev.dazaike.cadence.ui.onboarding.OnboardingScreen
import dev.dazaike.cadence.ui.onboarding.SamsungHealthErrorScreen
import dev.dazaike.cadence.ui.onboarding.UnavailableScreen
import dev.dazaike.cadence.ui.shell.CadenceShell
import dev.dazaike.cadence.ui.state.AppServices
import dev.dazaike.cadence.ui.state.AppViewModel
import kotlinx.coroutines.launch

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val ACTION_HC_SETTINGS = "androidx.health.connect.action.HEALTH_CONNECT_SETTINGS"
private const val ACTION_MANAGE_HEALTH_PERMISSIONS = "androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

@Composable
fun CadenceRoot(
    healthConnect: HealthConnectManager,
    samsungHealth: SamsungHealthManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    dashboardLayout: DashboardLayoutRepository,
    widgetPrefs: WidgetPreferences,
    mcpPrefs: McpServerPreferences,
    dataSourcePrefs: DataSourcePreferences,
    unitPrefs: UnitPreferences,
    cloudSync: CloudSyncManager,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val source by dataSourcePrefs.source.collectAsStateWithLifecycle(initialValue = DataSource.HEALTH_CONNECT)
    val deps = MainDeps(goalsRepo, exporter, dashboardLayout, widgetPrefs, mcpPrefs, dataSourcePrefs, unitPrefs, cloudSync)
    when (source) {
        DataSource.HEALTH_CONNECT -> HealthConnectRoot(healthConnect, deps, deepLinkMetric, onDeepLinkConsumed)
        DataSource.SAMSUNG_HEALTH -> SamsungHealthRoot(samsungHealth, deps, deepLinkMetric, onDeepLinkConsumed)
    }
}

/** Everything the main shell needs besides the active data source. */
private class MainDeps(
    val goalsRepo: GoalsRepository,
    val exporter: ExportManager,
    val dashboardLayout: DashboardLayoutRepository,
    val widgetPrefs: WidgetPreferences,
    val mcpPrefs: McpServerPreferences,
    val dataSourcePrefs: DataSourcePreferences,
    val unitPrefs: UnitPreferences,
    val cloudSync: CloudSyncManager,
)

@Composable
private fun HealthConnectRoot(
    manager: HealthConnectManager,
    deps: MainDeps,
    deepLinkMetric: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val context = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen(onInstall = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                        .setPackage("com.android.vending")
                )
            }
        })
        return
    }

    var granted by remember { mutableStateOf<Set<String>?>(null) }
    // True while the initial setup is chaining its permission requests (data → background).
    var inSetup by remember { mutableStateOf(false) }
    var requestedExtras by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result }

    // Re-read grants every time we return to the app (e.g. after toggling in HC settings).
    LifecycleResumeEffect(Unit) {
        scope.launch { granted = manager.grantedPermissions() }
        onPauseOrDispose { }
    }

    val hasData = (granted ?: emptySet()).any { it in manager.metricPermissions }
    val hasBackground = granted?.contains(HealthConnectManager.PERMISSION_READ_IN_BACKGROUND) == true

    // Setup is one continuous flow: after data is granted, immediately chain the background +
    // history prompt (HC won't allow it in the same request), so widgets work straight away.
    LaunchedEffect(inSetup, granted) {
        if (!inSetup || granted == null) return@LaunchedEffect
        when {
            !hasData -> inSetup = false
            !hasBackground && !requestedExtras -> {
                requestedExtras = true
                permissionLauncher.launch(manager.extraPermissions)
            }
            else -> inSetup = false
        }
    }

    fun startSetup() {
        requestedExtras = false
        inSetup = true
        permissionLauncher.launch(manager.metricPermissions)
    }

    fun manageAccess() {
        val g = granted ?: emptySet()
        when {
            !manager.metricPermissions.any { it in g } -> startSetup()
            HealthConnectManager.PERMISSION_READ_IN_BACKGROUND !in g ->
                permissionLauncher.launch(manager.extraPermissions)
            else -> {
                val candidates = listOf(
                    Intent(ACTION_MANAGE_HEALTH_PERMISSIONS).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
                    Intent(ACTION_HC_SETTINGS),
                )
                for (intent in candidates) {
                    if (runCatching { context.startActivity(intent) }.isSuccess) return
                }
                permissionLauncher.launch(manager.permissions)
            }
        }
    }

    when {
        granted == null || inSetup -> LoadingScreen()
        !hasData -> OnboardingScreen(onConnect = ::startSetup)
        else -> MainShell(manager, DataSource.HEALTH_CONNECT, deps, ::manageAccess, deepLinkMetric, onDeepLinkConsumed)
    }
}

@Composable
private fun SamsungHealthRoot(
    manager: SamsungHealthManager,
    deps: MainDeps,
    deepLinkMetric: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    var platformError by remember { mutableStateOf<String?>(null) }

    fun loadPermissions() {
        scope.launch {
            runCatching { manager.grantedPermissions() }
                .onSuccess {
                    granted = it
                    platformError = null
                }
                .onFailure { platformError = platformErrorMessage(it) }
        }
    }

    LifecycleResumeEffect(Unit) {
        loadPermissions()
        onPauseOrDispose { }
    }

    fun connect() {
        scope.launch {
            runCatching { manager.requestPermissions(context as Activity) }
                .onSuccess {
                    granted = it
                    platformError = null
                }
                .onFailure { platformError = platformErrorMessage(it) }
        }
    }

    when {
        platformError != null -> SamsungHealthErrorScreen(
            message = platformError!!,
            onRetry = {
                platformError = null
                granted = null
                loadPermissions()
            },
        )
        granted == null -> LoadingScreen()
        granted!!.isEmpty() -> OnboardingScreen(
            onConnect = ::connect,
            sourceName = "Samsung Health",
        )
        else -> MainShell(manager, DataSource.SAMSUNG_HEALTH, deps, ::connect, deepLinkMetric, onDeepLinkConsumed)
    }
}

private fun platformErrorMessage(error: Throwable): String = when (error) {
    is AuthorizationException ->
        "Samsung Health has not authorized Cadence. Confirm it is installed, then try again later."
    is HealthDataException -> when (error.errorCode) {
        ErrorCode.ERR_PLATFORM_NOT_INSTALLED ->
            "Samsung Health is not installed. Install it, then try again."
        ErrorCode.ERR_OLD_VERSION_PLATFORM ->
            "Samsung Health needs an update before Cadence can connect."
        ErrorCode.ERR_PLATFORM_DISABLED ->
            "Samsung Health is disabled. Enable it, then try again."
        ErrorCode.ERR_PLATFORM_NOT_INITIALIZED ->
            "Samsung Health is still starting. Open it once, then try again."
        else -> error.errorMessage.ifBlank { "Samsung Health is unavailable. Please try again." }
    }
    else -> "Samsung Health is unavailable. Please try again."
}

/** Builds the view model for the active data source (a source switch gets a fresh one) and hosts the shell. */
@Composable
private fun MainShell(
    manager: HealthDataSource,
    source: DataSource,
    deps: MainDeps,
    onManagePermissions: () -> Unit,
    deepLinkMetric: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as CadenceApp
    val vm: AppViewModel = viewModel(
        key = source.name,
        factory = AppViewModel.factory(manager, if (source == DataSource.SAMSUNG_HEALTH) app.samsungWake else null, app.status, app.appPrefs, deps.unitPrefs, deps.goalsRepo, deps.dashboardLayout),
    )
    val services = remember(manager, source, onManagePermissions) {
        AppServices(
            manager = manager,
            exporter = deps.exporter,
            cloudSync = deps.cloudSync,
            mcpPrefs = deps.mcpPrefs,
            widgetPrefs = deps.widgetPrefs,
            dataSourcePrefs = deps.dataSourcePrefs,
            source = source,
            onManagePermissions = onManagePermissions,
        )
    }
    CadenceShell(vm, services, deepLinkMetric, onDeepLinkConsumed)
}

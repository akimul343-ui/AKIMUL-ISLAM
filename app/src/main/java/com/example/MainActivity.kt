package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.security.NotificationHelper
import com.example.security.SecurityController
import com.example.ui.MainViewModel
import com.example.ui.Routes
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsAndGuideScreen
import com.example.ui.screens.RemoteControlScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SentinelGuardTheme

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)

        setContent {
            SentinelGuardTheme {
                SentinelGuardApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SentinelGuardApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh Device Admin and Accessibility states immediately upon returning from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAllState()
                SecurityController.syncGuardServiceState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) {
            viewModel.emitToast("All SMS, GPS, and Notification permissions granted!")
        } else {
            viewModel.emitToast("Some permissions denied (${denied.size}). Grant them for full offline protection.")
        }
        viewModel.refreshAllState()
        SecurityController.syncGuardServiceState(context)
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAllState()
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(
                route = Routes.DASHBOARD,
                label = "Shield",
                selectedIcon = Icons.Filled.Shield,
                unselectedIcon = Icons.Outlined.Shield,
                testTag = "nav_dashboard"
            ),
            NavDestination(
                route = Routes.REMOTE,
                label = "Remote",
                selectedIcon = Icons.Filled.CellTower,
                unselectedIcon = Icons.Outlined.CellTower,
                testTag = "nav_remote"
            ),
            NavDestination(
                route = Routes.LOGS,
                label = "Logs",
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_logs"
            ),
            NavDestination(
                route = Routes.SETTINGS,
                label = "Settings",
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.DASHBOARD

    val requestAllRuntimePermissions: () -> Unit = {
        val perms = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.DASHBOARD) {
                        DashboardScreen(
                            uiState = uiState,
                            onToggleArmed = { armed -> viewModel.toggleGuardArmed(armed) },
                            onRequestDeviceAdmin = {
                                if (SecurityController.isDeviceAdminActive(context)) {
                                    viewModel.emitToast("Device Admin is already active!")
                                } else {
                                    deviceAdminLauncher.launch(
                                        SecurityController.buildDeviceAdminIntent(context)
                                    )
                                }
                            },
                            onOpenAccessibilitySettings = {
                                context.startActivity(
                                    SecurityController.buildAccessibilitySettingsIntent()
                                )
                            },
                            onRequestRuntimePermissions = requestAllRuntimePermissions,
                            onTestLock = { viewModel.triggerLocalTestLock() },
                            onTestLocation = { viewModel.triggerLocalLocationTest() },
                            onToggleSiren = { viewModel.toggleLocalSiren() },
                            onStopSiren = { viewModel.stopActiveSiren() },
                            onSyncServer = { viewModel.registerThisDeviceWithServer(silent = false) },
                            onNotify = { viewModel.emitToast(it) }
                        )
                    }

                    composable(Routes.REMOTE) {
                        RemoteControlScreen(
                            uiState = uiState,
                            onLoadFleet = { url, fleet -> viewModel.loadFleetDevices(url, fleet) },
                            onSaveTarget = { target -> viewModel.saveTargetDevice(target) },
                            onRemoveTarget = { id -> viewModel.removeTargetDevice(id) },
                            onSendOnlineCommand = { url, id, pin, cmd, contact, msg ->
                                viewModel.sendRemoteOnlineCommand(url, id, pin, cmd, contact, msg)
                            },
                            onRefreshStatus = { url, id, pin ->
                                viewModel.fetchTargetDeviceStatus(url, id, pin)
                            },
                            onCheckHealth = { url -> viewModel.checkServerHealth(url) },
                            onSendOfflineSms = { phone, payload ->
                                viewModel.sendRemoteOfflineSms(phone, payload)
                            },
                            onClearFeedback = { viewModel.clearRemoteFeedback() }
                        )
                    }

                    composable(Routes.LOGS) {
                        LogsAndGuideScreen(
                            logs = uiState.logs,
                            oemGuides = viewModel.oemGuides,
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            settings = uiState.settings,
                            protection = uiState.protection,
                            adbDeviceOwnerCommand = uiState.adbDeviceOwnerCommand,
                            onToggleStealthIcon = { hide -> viewModel.toggleLauncherIconHidden(hide) },
                            onEnforceUninstallBlock = { viewModel.enableDeviceOwnerUninstallProtection() },
                            onNotify = { viewModel.emitToast(it) },
                            onSave = { role, fleet, alias, pin, prefix, keyword, url, id, phone, contact, msg, blockPower, blockSettingsShade, lockTamper, interval ->
                                viewModel.saveSettings(
                                    appRole = role,
                                    fleetId = fleet,
                                    deviceAlias = alias,
                                    secretPin = pin,
                                    smsPrefix = prefix,
                                    secretLockKeyword = keyword,
                                    serverUrl = url,
                                    deviceId = id,
                                    trustedPhone = phone,
                                    ownerContactInfo = contact,
                                    lockMessage = msg,
                                    blockPowerMenu = blockPower,
                                    blockSettingsAndShade = blockSettingsShade,
                                    lockOnPowerTamper = lockTamper,
                                    syncIntervalSeconds = interval
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

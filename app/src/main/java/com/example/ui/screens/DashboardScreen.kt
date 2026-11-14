package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.MainUiState

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onToggleArmed: (Boolean) -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onTestLock: () -> Unit,
    onTestLocation: () -> Unit,
    onToggleSiren: () -> Unit,
    onStopSiren: () -> Unit,
    onSyncServer: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLockConfirmDialog by remember { mutableStateOf(false) }

    val protection = uiState.protection
    val settings = uiState.settings

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(visible = settings.isSirenPlaying) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = "Active alarm",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "EMERGENCY SIREN RINGING",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Tap silence to stop max-volume alarm.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Button(
                                onClick = onStopSiren,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("stop_siren_banner_button")
                            ) {
                                Icon(Icons.Filled.VolumeOff, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Silence")
                            }
                        }
                    }
                }
            }

            // Hero Shield Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        1.dp,
                        if (protection.isGuardArmed) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(215.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_shield_hero),
                            contentDescription = "SentinelGuard Protection Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xF0070C18),
                                            Color(0xCC0B132B),
                                            Color(0x550B132B)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (protection.isGuardArmed) {
                                        Color(0xFF064E3B)
                                    } else {
                                        Color(0xFF1E293B)
                                    },
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (protection.isGuardArmed) Color(0xFF10B981)
                                                    else Color(0xFFF59E0B)
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (protection.isGuardArmed) "MDM GUARD ARMED" else "STANDBY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Switch(
                                    checked = protection.isGuardArmed,
                                    onCheckedChange = onToggleArmed,
                                    modifier = Modifier.testTag("master_guard_switch")
                                )
                            }

                            Column {
                                Text(
                                    text = "SentinelGuard MDM",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${settings.appRole.label} • Fleet: ${settings.fleetId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF00E5FF)
                                )
                                Text(
                                    text = "Readiness: ${protection.readinessCount}/${protection.totalPillars} Pillars (${protection.scorePercent}%) • ${if (protection.isDeviceOwnerActive) "Device Owner" else "Standard Admin"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFCBD5E1)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { protection.scorePercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (protection.scorePercent == 100) Color(0xFF10B981) else Color(0xFF00E5FF),
                                    trackColor = Color(0x44FFFFFF)
                                )
                            }
                        }
                    }
                }
            }

            // Device Identity & SMS Cheat Sheet Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DEVICE ID (${settings.deviceAlias})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = settings.deviceId,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, "Device ID", settings.deviceId)
                                        onNotify("Device ID copied")
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("copy_device_id_button")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Device ID")
                                }
                                FilledTonalButton(
                                    onClick = onSyncServer,
                                    enabled = !uiState.isRemoteLoading,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("sync_server_button")
                                ) {
                                    Text("Poll Flask")
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Unique Offline SMS Commands for this device:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Device Lock: #LOCK_${settings.deviceId}#  •  ${settings.secretLockKeyword}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Targeted: ${settings.deviceId}#LOCATE#${settings.secretPin}  •  ${settings.deviceId}#SIREN#${settings.secretPin}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        if (!uiState.serverHealthSummary.isNullOrBlank()) {
                            Text(
                                text = uiState.serverHealthSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Knox / MDM Device Owner Status Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (protection.isDeviceOwnerActive) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = if (protection.isDeviceOwnerActive) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (protection.isDeviceOwnerActive) {
                                        "Knox/MDM Device Owner Active"
                                    } else {
                                        "Device Owner Not Provisioned"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (protection.isDeviceOwnerActive) {
                                        "Uninstall protection & MDM policies enforced."
                                    } else {
                                        "Optional: Run ADB command in Settings to block uninstall permanently."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                copyToClipboard(context, "ADB Device Owner Command", uiState.adbDeviceOwnerCommand)
                                onNotify("ADB Device Owner command copied")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("copy_adb_command_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy ADB Command")
                        }
                    }
                }
            }

            // 4-Pillar Security Setup Checklist
            item {
                Text(
                    text = "Protection Pillars",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillarCard(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = "1. Device Admin (Remote Lock)",
                        subtitle = if (protection.isDeviceAdminActive) {
                            "Active — Remote screen lock is ready."
                        } else {
                            "Required to lock the screen via SMS or Flask server."
                        },
                        isReady = protection.isDeviceAdminActive,
                        actionLabel = if (protection.isDeviceAdminActive) "Active" else "Enable",
                        testTag = "pillar_device_admin_button",
                        onClick = onRequestDeviceAdmin
                    )

                    PillarCard(
                        icon = Icons.Filled.PowerSettingsNew,
                        title = "2. MDM Accessibility Lockdown",
                        subtitle = if (protection.isAccessibilityEnabled) {
                            "Enabled — Blocks Power Menu, Settings & Notification Shade when armed."
                        } else {
                            "Blocks thieves from turning off phone, Mobile Data, or GPS."
                        },
                        isReady = protection.isAccessibilityEnabled,
                        actionLabel = if (protection.isAccessibilityEnabled) "Settings" else "Enable",
                        testTag = "pillar_accessibility_button",
                        onClick = onOpenAccessibilitySettings
                    )

                    PillarCard(
                        icon = Icons.Filled.Sms,
                        title = "3. Offline SMS Commands",
                        subtitle = if (protection.hasSmsPermissions) {
                            "Granted — Listens for ${settings.secretLockKeyword} & PIN commands."
                        } else {
                            "Grant RECEIVE_SMS & SEND_SMS to reply with GPS links."
                        },
                        isReady = protection.hasSmsPermissions,
                        actionLabel = if (protection.hasSmsPermissions) "Granted" else "Grant",
                        testTag = "pillar_sms_button",
                        onClick = onRequestRuntimePermissions
                    )

                    PillarCard(
                        icon = Icons.Filled.GpsFixed,
                        title = "4. High-Accuracy GPS Tracking",
                        subtitle = if (protection.hasLocationPermission) {
                            "Granted — Captures coordinates & battery level."
                        } else {
                            "Grant Fine Location to upload & SMS Google Maps links."
                        },
                        isReady = protection.hasLocationPermission,
                        actionLabel = if (protection.hasLocationPermission) "Granted" else "Grant",
                        testTag = "pillar_location_button",
                        onClick = onRequestRuntimePermissions
                    )
                }
            }

            // Quick Local Diagnostic Actions
            item {
                Text(
                    text = "Quick Tests & Telemetry",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showLockConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("test_lock_button")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Lock")
                    }

                    Button(
                        onClick = onTestLocation,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("test_gps_button")
                    ) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test GPS")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleSiren,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("test_siren_button")
                    ) {
                        Icon(
                            imageVector = if (settings.isSirenPlaying) Icons.Filled.VolumeOff else Icons.Filled.NotificationsActive,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (settings.isSirenPlaying) "Stop Siren" else "Test Siren")
                    }

                    OutlinedButton(
                        onClick = {
                            val summary = "Device ID: ${settings.deviceId}\nInstant Lock SMS: ${settings.secretLockKeyword}\nPIN Lock SMS: ${settings.smsPrefix}#LOCK#${settings.secretPin}\nLocate SMS: ${settings.smsPrefix}#LOCATE#${settings.secretPin}"
                            copyToClipboard(context, "SentinelGuard Commands", summary)
                            onNotify("Recovery commands copied to clipboard")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("copy_commands_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Codes")
                    }
                }
            }

            // Last Location Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LAST CAPTURED GPS FIX",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = settings.lastLocationText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (settings.lastMapsUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(settings.lastMapsUrl))
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("open_last_map_button")
                            ) {
                                Text("Open Map")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showLockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLockConfirmDialog = false },
            icon = { Icon(Icons.Filled.Security, contentDescription = null) },
            title = { Text("Lock Screen Now?") },
            text = {
                Text("This will immediately lock your screen using Device Admin so you can verify that remote locking works.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLockConfirmDialog = false
                        onTestLock()
                    },
                    modifier = Modifier.testTag("confirm_test_lock_button")
                ) {
                    Text("Lock Screen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PillarCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isReady: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isReady) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isReady) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isReady) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = if (isReady) "Ready" else "Action needed",
                        tint = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AppRole
import com.example.data.Constants
import com.example.data.GuardSettings
import com.example.data.ProtectionStatus
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: GuardSettings,
    protection: ProtectionStatus,
    adbDeviceOwnerCommand: String,
    onToggleStealthIcon: (Boolean) -> Unit,
    onEnforceUninstallBlock: () -> Unit,
    onNotify: (String) -> Unit,
    onSave: (
        appRole: AppRole,
        fleetId: String,
        deviceAlias: String,
        secretPin: String,
        smsPrefix: String,
        secretLockKeyword: String,
        serverUrl: String,
        deviceId: String,
        trustedPhone: String,
        ownerContactInfo: String,
        lockMessage: String,
        blockPowerMenu: Boolean,
        blockSettingsAndShade: Boolean,
        lockOnPowerTamper: Boolean,
        syncIntervalSeconds: Int
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appRole by remember(settings.appRole) { mutableStateOf(settings.appRole) }
    var fleetId by remember(settings.fleetId) { mutableStateOf(settings.fleetId) }
    var deviceAlias by remember(settings.deviceAlias) { mutableStateOf(settings.deviceAlias) }
    var secretPin by remember(settings.secretPin) { mutableStateOf(settings.secretPin) }
    var smsPrefix by remember(settings.smsPrefix) { mutableStateOf(settings.smsPrefix) }
    var secretLockKeyword by remember(settings.secretLockKeyword) {
        mutableStateOf(settings.secretLockKeyword)
    }
    val serverUrl = Constants.RENDER_BASE_URL
    var deviceId by remember(settings.deviceId) { mutableStateOf(settings.deviceId) }
    var trustedPhone by remember(settings.trustedPhone) { mutableStateOf(settings.trustedPhone) }
    var ownerContactInfo by remember(settings.ownerContactInfo) {
        mutableStateOf(settings.ownerContactInfo)
    }
    var lockMessage by remember(settings.lockMessage) { mutableStateOf(settings.lockMessage) }
    var blockPowerMenu by remember(settings.blockPowerMenu) { mutableStateOf(settings.blockPowerMenu) }
    var blockSettingsAndShade by remember(settings.blockSettingsAndShade) {
        mutableStateOf(settings.blockSettingsAndShade)
    }
    var lockOnPowerTamper by remember(settings.lockOnPowerTamper) {
        mutableStateOf(settings.lockOnPowerTamper)
    }
    var syncInterval by remember(settings.syncIntervalSeconds) {
        mutableFloatStateOf(settings.syncIntervalSeconds.toFloat())
    }
    var showStealthConfirmDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Fleet & MDM Settings",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure device role (Master Admin vs Target Client), Fleet ID, unique SMS commands, and Stealth Mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Device Role & Fleet Identity", style = MaterialTheme.typography.titleMedium)

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppRole.entries.forEachIndexed { index, role ->
                                SegmentedButton(
                                    selected = appRole == role,
                                    onClick = { appRole = role },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AppRole.entries.size
                                    )
                                ) {
                                    Text(role.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = fleetId,
                            onValueChange = { fleetId = it.uppercase() },
                            label = { Text("Fleet / Family Group ID") },
                            supportingText = { Text("Master & all Target phones in the same Fleet ID appear together") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_fleet_id_input")
                        )

                        OutlinedTextField(
                            value = deviceAlias,
                            onValueChange = { deviceAlias = it },
                            label = { Text("Device Nickname (e.g. Mom's Redmi)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_device_alias_input")
                        )

                        OutlinedTextField(
                            value = deviceId,
                            onValueChange = { deviceId = it.uppercase() },
                            label = { Text("Unique Device ID / IMEI") },
                            supportingText = {
                                Text("Unique SMS Lock for this device: #LOCK_${deviceId.trim().uppercase()}#")
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_device_id_input")
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("SMS Triggers & Secret PIN", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = secretLockKeyword,
                            onValueChange = { secretLockKeyword = it },
                            label = { Text("Custom Instant Lock SMS Keyword") },
                            supportingText = { Text("Also responds to #LOCK_${deviceId.trim().uppercase()}#") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_lock_keyword_input")
                        )

                        OutlinedTextField(
                            value = secretPin,
                            onValueChange = { secretPin = it },
                            label = { Text("Secret Command PIN") },
                            supportingText = { Text("Used for Flask API & ${deviceId.trim().uppercase()}#LOCATE#<PIN>") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_pin_input")
                        )

                        OutlinedTextField(
                            value = smsPrefix,
                            onValueChange = { smsPrefix = it.uppercase() },
                            label = { Text("Global SMS Prefix") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_prefix_input")
                        )

                        OutlinedTextField(
                            value = trustedPhone,
                            onValueChange = { trustedPhone = it },
                            label = { Text("Default Target / Emergency Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_phone_input")
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Render Flask Server & Lockdown Rules", style = MaterialTheme.typography.titleMedium)

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_server_url_badge")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "HARDCODED RENDER FLASK SERVER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        OutlinedTextField(
                            value = ownerContactInfo,
                            onValueChange = { ownerContactInfo = it },
                            label = { Text("Owner Contact (Telegram / Phone)") },
                            supportingText = { Text("Displayed on full-screen Lost-Mode Lock Overlay (e.g. @Akimul0)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_owner_contact_input")
                        )

                        OutlinedTextField(
                            value = lockMessage,
                            onValueChange = { lockMessage = it },
                            label = { Text("Default Lock Screen Message") },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_lock_message_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block Power Menu", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Dismiss shutdown/reboot menu while Guard is armed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = blockPowerMenu,
                                onCheckedChange = { blockPowerMenu = it },
                                modifier = Modifier.testTag("settings_block_power_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block Settings & Notification Panel", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Prevent disabling Mobile Data, GPS, or Accessibility when armed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = blockSettingsAndShade,
                                onCheckedChange = { blockSettingsAndShade = it },
                                modifier = Modifier.testTag("settings_block_settings_shade_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Lock on Tamper", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Immediately lock screen if power menu or Settings is opened",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = lockOnPowerTamper,
                                onCheckedChange = { lockOnPowerTamper = it },
                                modifier = Modifier.testTag("settings_lock_tamper_switch")
                            )
                        }

                        Column {
                            Text(
                                text = "Flask Polling Interval: ${syncInterval.roundToInt()}s",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Slider(
                                value = syncInterval,
                                onValueChange = { syncInterval = it },
                                valueRange = 10f..120f,
                                steps = 10,
                                modifier = Modifier.testTag("settings_sync_slider")
                            )
                        }
                    }
                }
            }

            // Stealth Launcher Icon & Device Owner Hardening
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Stealth & Device Owner (Target Client)", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hide App Launcher Icon", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (protection.isLauncherIconHidden) {
                                        "Hidden via COMPONENT_ENABLED_STATE_DISABLED. Tap persistent notification to open."
                                    } else {
                                        "Disables launcher shortcut so target app runs silently in background."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = protection.isLauncherIconHidden,
                                onCheckedChange = { wantHide ->
                                    if (wantHide) {
                                        showStealthConfirmDialog = true
                                    } else {
                                        onToggleStealthIcon(false)
                                    }
                                },
                                modifier = Modifier.testTag("settings_stealth_icon_switch")
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ADB Device Owner Command (Uninstall Lock):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = adbDeviceOwnerCommand,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("ADB Command", adbDeviceOwnerCommand))
                                    onNotify("ADB command copied")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_copy_adb_button")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy ADB")
                            }

                            FilledTonalButton(
                                onClick = onEnforceUninstallBlock,
                                enabled = protection.isDeviceOwnerActive,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_block_uninstall_button")
                            ) {
                                Text(
                                    if (protection.isDeviceOwnerActive) "Block Uninstall" else "Need Device Owner"
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSave(
                            appRole,
                            fleetId,
                            deviceAlias,
                            secretPin,
                            smsPrefix,
                            secretLockKeyword,
                            serverUrl,
                            deviceId,
                            trustedPhone,
                            ownerContactInfo,
                            lockMessage,
                            blockPowerMenu,
                            blockSettingsAndShade,
                            lockOnPowerTamper,
                            syncInterval.roundToInt()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Configuration")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showStealthConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStealthConfirmDialog = false },
            icon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
            title = { Text("Hide App Icon?") },
            text = {
                Text(
                    "This disables the app drawer icon using PackageManager.COMPONENT_ENABLED_STATE_DISABLED. " +
                        "Make sure Guard Armed is ON so you can reopen SentinelGuard anytime by tapping its persistent notification."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStealthConfirmDialog = false
                        onToggleStealthIcon(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_hide_icon_button")
                ) {
                    Text("Hide Icon")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStealthConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

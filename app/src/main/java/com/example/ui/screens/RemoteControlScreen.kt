package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.DeviceStatusResponse
import com.example.data.RemoteCommandType
import com.example.data.SavedTargetDevice
import com.example.ui.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteControlScreen(
    uiState: MainUiState,
    onLoadFleet: (serverUrl: String, fleetId: String) -> Unit,
    onSaveTarget: (SavedTargetDevice) -> Unit,
    onRemoveTarget: (String) -> Unit,
    onSendOnlineCommand: (
        serverUrl: String,
        deviceId: String,
        pin: String,
        command: RemoteCommandType,
        contactInfo: String,
        lockMessage: String
    ) -> Unit,
    onRefreshStatus: (serverUrl: String, deviceId: String, pin: String) -> Unit,
    onCheckHealth: (serverUrl: String) -> Unit,
    onSendOfflineSms: (phone: String, rawSmsPayload: String) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var modeIndex by remember { mutableIntStateOf(0) } // 0 = Online Flask, 1 = Offline SMS
    var serverUrl by remember(uiState.settings.serverUrl) { mutableStateOf(uiState.settings.serverUrl) }
    var fleetId by remember(uiState.settings.fleetId) { mutableStateOf(uiState.settings.fleetId) }
    var targetDeviceId by remember(uiState.settings.deviceId) { mutableStateOf(uiState.settings.deviceId) }
    var targetPhone by remember(uiState.settings.trustedPhone) { mutableStateOf(uiState.settings.trustedPhone) }
    var targetPin by remember { mutableStateOf(uiState.settings.secretPin) }
    var contactInfo by remember(uiState.settings.ownerContactInfo) {
        mutableStateOf(uiState.settings.ownerContactInfo)
    }
    var lockMessage by remember(uiState.settings.lockMessage) {
        mutableStateOf(uiState.settings.lockMessage)
    }
    var showAddTargetDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Master Admin Console",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Lock target devices remotely and display your custom contact info / Telegram handle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAddTargetDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_target_device_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            // Saved Target Devices Quick Switcher with direct LOCK button
            if (uiState.savedTargets.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "ENROLLED TARGET DEVICES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            uiState.savedTargets.forEach { target ->
                                val selected = targetDeviceId.equals(target.deviceId, ignoreCase = true)
                                Surface(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${target.alias} (${target.deviceId})",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            if (target.phoneNumber.isNotBlank()) {
                                                Text(
                                                    text = target.phoneNumber,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    targetDeviceId = target.deviceId
                                                    if (target.pin.isNotBlank()) targetPin = target.pin
                                                    onSendOnlineCommand(
                                                        serverUrl,
                                                        target.deviceId,
                                                        target.pin.ifBlank { targetPin },
                                                        RemoteCommandType.LOCK,
                                                        contactInfo,
                                                        lockMessage
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("lock_saved_${target.deviceId}")
                                            ) {
                                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("LOCK")
                                            }
                                            IconButton(
                                                onClick = { onRemoveTarget(target.deviceId) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Fleet Discovery from Flask Server
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LIVE FLEET DISCOVERY (FLASK SERVER)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = fleetId,
                                onValueChange = { fleetId = it.uppercase() },
                                label = { Text("Fleet ID") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fleet_id_input")
                            )
                            Button(
                                onClick = { onLoadFleet(serverUrl, fleetId) },
                                enabled = !uiState.isRemoteLoading,
                                modifier = Modifier
                                    .heightIn(min = 52.dp)
                                    .testTag("load_fleet_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Fleet")
                            }
                        }

                        if (uiState.fleetDevices.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.fleetDevices.forEach { dev ->
                                    FleetDeviceCard(
                                        device = dev,
                                        isSelected = targetDeviceId.equals(dev.deviceId, ignoreCase = true),
                                        onSelect = { targetDeviceId = dev.deviceId },
                                        onQuickLock = {
                                            targetDeviceId = dev.deviceId
                                            onSendOnlineCommand(
                                                serverUrl,
                                                dev.deviceId,
                                                targetPin,
                                                RemoteCommandType.LOCK,
                                                contactInfo,
                                                lockMessage
                                            )
                                        },
                                        onOpenMaps = { url ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = modeIndex == 0,
                        onClick = {
                            modeIndex = 0
                            onClearFeedback()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.CloudQueue, contentDescription = null) },
                        modifier = Modifier.testTag("mode_online_button")
                    ) {
                        Text("Online (Flask)")
                    }
                    SegmentedButton(
                        selected = modeIndex == 1,
                        onClick = {
                            modeIndex = 1
                            onClearFeedback()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                        modifier = Modifier.testTag("mode_sms_button")
                    ) {
                        Text("Offline (SMS)")
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
                        if (modeIndex == 0) {
                            OutlinedTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = { Text("Render Flask Server URL") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_server_url_input")
                            )
                            OutlinedTextField(
                                value = contactInfo,
                                onValueChange = { contactInfo = it },
                                label = { Text("Owner Contact Info (Telegram / Phone)") },
                                placeholder = { Text("@Akimul0 or +8801XXXXXXXXX") },
                                supportingText = { Text("Displayed prominently on target phone's Full-Screen Lock Overlay") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_contact_info_input")
                            )
                        } else {
                            OutlinedTextField(
                                value = targetPhone,
                                onValueChange = { targetPhone = it },
                                label = { Text("Target Phone Number") },
                                placeholder = { Text("+8801XXXXXXXXX") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remote_phone_input")
                            )
                        }

                        OutlinedTextField(
                            value = targetDeviceId,
                            onValueChange = { targetDeviceId = it.uppercase() },
                            label = { Text("Target Device ID") },
                            supportingText = {
                                if (modeIndex == 1 && targetDeviceId.isNotBlank()) {
                                    Text("Unique SMS Lock: #LOCK_${targetDeviceId.trim().uppercase()}#")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_device_id_input")
                        )

                        OutlinedTextField(
                            value = targetPin,
                            onValueChange = { targetPin = it },
                            label = { Text("Target Secret PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_pin_input")
                        )

                        if (modeIndex == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCheckHealth(serverUrl) },
                                    enabled = !uiState.isRemoteLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("check_server_health_button")
                                ) {
                                    Text("Ping Server")
                                }
                                FilledTonalButton(
                                    onClick = { onRefreshStatus(serverUrl, targetDeviceId, targetPin) },
                                    enabled = !uiState.isRemoteLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("fetch_target_status_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Get Status")
                                }
                            }
                        }
                    }
                }
            }

            // Command Action Grid
            item {
                val cleanDev = targetDeviceId.trim().uppercase()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (modeIndex == 0) {
                                    onSendOnlineCommand(
                                        serverUrl,
                                        targetDeviceId,
                                        targetPin,
                                        RemoteCommandType.LOCK,
                                        contactInfo,
                                        lockMessage
                                    )
                                } else {
                                    val payload = if (cleanDev.isNotBlank()) {
                                        "#LOCK_${cleanDev}#"
                                    } else {
                                        "LOCK#${targetPin.trim()}"
                                    }
                                    onSendOfflineSms(targetPhone, payload)
                                }
                            },
                            enabled = !uiState.isRemoteLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .testTag("remote_lock_button")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LOCK")
                        }

                        if (modeIndex == 0) {
                            FilledTonalButton(
                                onClick = {
                                    onSendOnlineCommand(
                                        serverUrl,
                                        targetDeviceId,
                                        targetPin,
                                        RemoteCommandType.UNLOCK,
                                        contactInfo,
                                        lockMessage
                                    )
                                },
                                enabled = !uiState.isRemoteLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("remote_unlock_button")
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("UNLOCK")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val payload = if (cleanDev.isNotBlank()) {
                                        "${cleanDev}#LOCATE#${targetPin.trim()}"
                                    } else {
                                        "LOCATE#${targetPin.trim()}"
                                    }
                                    onSendOfflineSms(targetPhone, payload)
                                },
                                enabled = !uiState.isRemoteLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("remote_locate_button")
                            ) {
                                Icon(Icons.Filled.GpsFixed, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Track GPS")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (modeIndex == 0) {
                            Button(
                                onClick = {
                                    onSendOnlineCommand(
                                        serverUrl,
                                        targetDeviceId,
                                        targetPin,
                                        RemoteCommandType.LOCATE,
                                        contactInfo,
                                        lockMessage
                                    )
                                },
                                enabled = !uiState.isRemoteLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("remote_locate_button")
                            ) {
                                Icon(Icons.Filled.GpsFixed, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Locate GPS")
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                if (modeIndex == 0) {
                                    onSendOnlineCommand(
                                        serverUrl,
                                        targetDeviceId,
                                        targetPin,
                                        RemoteCommandType.SIREN,
                                        contactInfo,
                                        lockMessage
                                    )
                                } else {
                                    val payload = if (cleanDev.isNotBlank()) {
                                        "${cleanDev}#SIREN#${targetPin.trim()}"
                                    } else {
                                        "SIREN#${targetPin.trim()}"
                                    }
                                    onSendOfflineSms(targetPhone, payload)
                                }
                            },
                            enabled = !uiState.isRemoteLoading,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .testTag("remote_siren_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Siren")
                        }

                        OutlinedButton(
                            onClick = {
                                if (modeIndex == 0) {
                                    onSendOnlineCommand(
                                        serverUrl,
                                        targetDeviceId,
                                        targetPin,
                                        RemoteCommandType.GUARD_ON,
                                        contactInfo,
                                        lockMessage
                                    )
                                } else {
                                    val payload = if (cleanDev.isNotBlank()) {
                                        "${cleanDev}#GUARD#${targetPin.trim()}"
                                    } else {
                                        "GUARD#${targetPin.trim()}"
                                    }
                                    onSendOfflineSms(targetPhone, payload)
                                }
                            },
                            enabled = !uiState.isRemoteLoading,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .testTag("remote_guard_on_button")
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guard")
                        }
                    }

                    if (modeIndex == 0) {
                        OutlinedButton(
                            onClick = {
                                onSendOnlineCommand(
                                    serverUrl,
                                    targetDeviceId,
                                    targetPin,
                                    RemoteCommandType.STOP_SIREN,
                                    contactInfo,
                                    lockMessage
                                )
                            },
                            enabled = !uiState.isRemoteLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("remote_stop_siren_button")
                        ) {
                            Icon(Icons.Filled.VolumeOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Remote Siren")
                        }
                    }
                }
            }

            if (uiState.isRemoteLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }

            if (!uiState.remoteError.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = uiState.remoteError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            if (!uiState.remoteResultMessage.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = uiState.remoteResultMessage,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            uiState.remoteTargetStatus?.let { target ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TARGET TELEMETRY: ${target.deviceAlias.ifBlank { target.deviceName }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "State: ${target.lockState} • ID: ${target.deviceId} • Fleet: ${target.fleetId}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (target.contactInfo.isNotBlank()) {
                                Text(
                                    text = "Lock Contact Info: ${target.contactInfo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = "Guard Armed: ${target.guardArmed} • Device Owner: ${target.isDeviceOwner} • Battery: ${target.batteryPercent}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Last Seen: ${target.lastSeen}", style = MaterialTheme.typography.bodySmall)

                            val loc = target.lastLocation
                            if (loc != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "GPS: ${loc.latitude}, ${loc.longitude} (±${loc.accuracyMeters.toInt()}m)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (loc.mapsUrl.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(loc.mapsUrl))
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_target_maps_button")
                                    ) {
                                        Text("Open Location in Google Maps")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTargetDialog) {
        AddTargetDialog(
            onDismiss = { showAddTargetDialog = false },
            onSave = { device ->
                onSaveTarget(device)
                targetDeviceId = device.deviceId
                if (device.phoneNumber.isNotBlank()) targetPhone = device.phoneNumber
                if (device.pin.isNotBlank()) targetPin = device.pin
                showAddTargetDialog = false
            }
        )
    }
}

@Composable
private fun FleetDeviceCard(
    device: DeviceStatusResponse,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onQuickLock: () -> Unit,
    onOpenMaps: (String) -> Unit
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${device.deviceAlias.ifBlank { device.deviceName }} (${device.deviceId})",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${device.lockState} • Bat: ${if (device.batteryPercent >= 0) "${device.batteryPercent}%" else "--"} • Owner: ${device.isDeviceOwner}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                device.lastLocation?.let { loc ->
                    if (loc.mapsUrl.isNotBlank()) {
                        TextButton(
                            onClick = { onOpenMaps(loc.mapsUrl) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Text("View GPS on Maps", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onQuickLock,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("LOCK")
                }
                FilledTonalButton(
                    onClick = onSelect,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (isSelected) "Active" else "Select")
                }
            }
        }
    }
}

@Composable
private fun AddTargetDialog(
    onDismiss: () -> Unit,
    onSave: (SavedTargetDevice) -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("2468") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll Target Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it.uppercase() },
                    label = { Text("Device ID") },
                    placeholder = { Text("SG-8492A1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Nickname (e.g. Mom's Phone)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (for Offline SMS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Device Secret PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (deviceId.isNotBlank()) {
                        val cleanId = deviceId.trim().uppercase()
                        onSave(
                            SavedTargetDevice(
                                deviceId = cleanId,
                                alias = alias.ifBlank { cleanId },
                                phoneNumber = phone.trim(),
                                pin = pin.trim().ifBlank { "2468" },
                                smsKeyword = "#LOCK_${cleanId}#"
                            )
                        )
                    }
                }
            ) {
                Text("Save Target")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

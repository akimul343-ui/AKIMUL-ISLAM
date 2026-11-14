package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AntiTheftPrefs
import com.example.data.AppRole
import com.example.data.DeviceStatusResponse
import com.example.data.GuardSettings
import com.example.data.LogSeverity
import com.example.data.OemGuideItem
import com.example.data.ProtectionStatus
import com.example.data.RemoteCommandType
import com.example.data.RemoteRepository
import com.example.data.SavedTargetDevice
import com.example.data.SecurityLogEntry
import com.example.data.SendCommandRequest
import com.example.security.SecurityController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: GuardSettings = GuardSettings(),
    val protection: ProtectionStatus = ProtectionStatus(),
    val logs: List<SecurityLogEntry> = emptyList(),
    val savedTargets: List<SavedTargetDevice> = emptyList(),
    val fleetDevices: List<DeviceStatusResponse> = emptyList(),
    val adbDeviceOwnerCommand: String = "",
    val isRemoteLoading: Boolean = false,
    val remoteResultMessage: String? = null,
    val remoteError: String? = null,
    val remoteTargetStatus: DeviceStatusResponse? = null,
    val serverHealthSummary: String? = null,
    val toastMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = AntiTheftPrefs(context)
    private val repository = RemoteRepository()

    private val _uiState = MutableStateFlow(
        MainUiState(
            settings = prefs.getSettings(),
            protection = SecurityController.readProtectionStatus(context),
            logs = prefs.getLogs(),
            savedTargets = prefs.getSavedTargets(),
            adbDeviceOwnerCommand = SecurityController.adbDeviceOwnerCommand(context)
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val oemGuides: List<OemGuideItem> = listOf(
        OemGuideItem(
            brand = "Device Owner (All Android OEMs)",
            osName = "ADB MDM Provisioning",
            steps = listOf(
                "Remove all Google/Mi/Samsung accounts temporarily from Settings -> Accounts (can be re-added right after).",
                "Enable USB Debugging in Developer Options and connect phone to PC.",
                "Run: ${SecurityController.adbDeviceOwnerCommand(context)}",
                "Once provisioned, SentinelGuard becomes Device Owner and permanently blocks uninstallation."
            )
        ),
        OemGuideItem(
            brand = "Xiaomi / Redmi / POCO",
            osName = "MIUI & HyperOS",
            steps = listOf(
                "Open Security App -> Permissions -> Autostart -> Enable SentinelGuard.",
                "App Info -> Battery saver -> Select 'No restrictions'.",
                "Open Recent Apps -> Long-press SentinelGuard -> Tap the Lock icon.",
                "Accessibility -> Downloaded apps -> Enable 'SentinelGuard Power Menu Guard'."
            )
        ),
        OemGuideItem(
            brand = "Samsung Galaxy",
            osName = "One UI 5 / 6 / 7",
            steps = listOf(
                "Settings -> Battery -> Background usage limits -> Add SentinelGuard to 'Never auto sleeping apps'.",
                "Settings -> Apps -> SentinelGuard -> Battery -> Set to 'Unrestricted'.",
                "Settings -> Accessibility -> Installed apps -> Turn ON 'SentinelGuard Power Menu Guard'.",
                "Settings -> Security & privacy -> More security settings -> Device admin apps -> Activate SentinelGuard."
            )
        ),
        OemGuideItem(
            brand = "Realme / Oppo / OnePlus",
            osName = "Realme UI & ColorOS",
            steps = listOf(
                "App Info -> Battery usage -> Enable 'Allow foreground activity' and 'Allow background activity'.",
                "App Info -> Enable 'Allow auto launch'.",
                "In Recent Apps screen, tap the 3-dot menu on SentinelGuard -> Lock.",
                "If Accessibility shows 'Restricted setting', open App Info -> 3-dot menu -> 'Allow restricted settings'."
            )
        )
    )

    init {
        viewModelScope.launch {
            AntiTheftPrefs.settingsVersion.collect {
                refreshAllState()
            }
        }
        viewModelScope.launch {
            AntiTheftPrefs.logsVersion.collect {
                _uiState.update { it.copy(logs = prefs.getLogs()) }
            }
        }
    }

    fun refreshAllState() {
        _uiState.update {
            it.copy(
                settings = prefs.getSettings(),
                protection = SecurityController.readProtectionStatus(context),
                logs = prefs.getLogs(),
                savedTargets = prefs.getSavedTargets(),
                adbDeviceOwnerCommand = SecurityController.adbDeviceOwnerCommand(context)
            )
        }
    }

    fun toggleGuardArmed(armed: Boolean) {
        prefs.updateSettings { it.copy(guardArmed = armed) }
        prefs.appendLog(
            title = if (armed) "Anti-Theft MDM Guard Armed" else "Anti-Theft MDM Guard Disarmed",
            detail = if (armed) {
                "Power menu, Settings, Quick Settings blocking and Flask polling enabled."
            } else {
                "Background guard paused by user."
            },
            source = "Dashboard",
            severity = if (armed) LogSeverity.SUCCESS else LogSeverity.INFO
        )
        SecurityController.syncGuardServiceState(context)
        if (armed) {
            registerThisDeviceWithServer(silent = true)
        }
        refreshAllState()
    }

    fun toggleLauncherIconHidden(hidden: Boolean) {
        SecurityController.setLauncherIconHidden(context, hidden)
        emitToast(
            if (hidden) {
                "Launcher icon hidden (COMPONENT_ENABLED_STATE_DISABLED)"
            } else {
                "Launcher icon restored"
            }
        )
        refreshAllState()
    }

    fun enableDeviceOwnerUninstallProtection() {
        val ok = SecurityController.setUninstallBlocked(context, true)
        if (ok) {
            prefs.appendLog(
                title = "Uninstall Blocked via Device Owner",
                detail = "dpm.setUninstallBlocked(..., true) enforced.",
                source = "DeviceOwner",
                severity = LogSeverity.SUCCESS
            )
            emitToast("Uninstall protection enforced via Device Owner!")
        } else {
            emitToast("App is not Device Owner yet. Provision via ADB first.")
        }
        refreshAllState()
    }

    fun triggerLocalTestLock() {
        val settings = prefs.getSettings()
        SecurityController.enforceLostModeLock(
            context = context,
            source = "Dashboard Test",
            contactInfo = settings.ownerContactInfo,
            lockMessage = settings.lockMessage
        )
        refreshAllState()
    }

    fun triggerLocalLocationTest() {
        SecurityController.dispatchLocationCapture(
            context = context,
            source = "Dashboard Test",
            replySmsTo = null,
            uploadToServer = true,
            repository = repository
        )
        emitToast("Fetching high-accuracy GPS fix...")
    }

    fun toggleLocalSiren() {
        if (_uiState.value.settings.isSirenPlaying) {
            SecurityController.stopSiren(context, "Dashboard")
        } else {
            SecurityController.startSiren(context, "Dashboard Test")
        }
        refreshAllState()
    }

    fun stopActiveSiren() {
        SecurityController.stopSiren(context, "Emergency Banner")
        refreshAllState()
    }

    fun registerThisDeviceWithServer(silent: Boolean = false) {
        val settings = prefs.getSettings()
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isRemoteLoading = true, remoteError = null) }
            }
            repository.pollCommands(
                baseUrl = settings.serverUrl,
                deviceId = settings.deviceId,
                pin = settings.secretPin,
                deviceName = prefs.deviceDisplayName(),
                deviceAlias = settings.deviceAlias,
                fleetId = settings.fleetId,
                guardArmed = settings.guardArmed,
                isDeviceOwner = SecurityController.isDeviceOwnerApp(context),
                batteryPercent = SecurityController.batteryPercentage(context)
            ).onSuccess { resp ->
                prefs.appendLog(
                    title = "Synced with Flask Server",
                    detail = "Fleet ${settings.fleetId} • Device ${resp.deviceId} (${resp.commands.size} pending).",
                    source = "CloudSync",
                    severity = LogSeverity.SUCCESS
                )
                if (!silent) {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            serverHealthSummary = "Connected • ${resp.deviceId} (${settings.fleetId})"
                        )
                    }
                    emitToast("Synced with Render Flask server!")
                }
            }.onFailure { err ->
                if (!silent) {
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            serverHealthSummary = "Server unreachable: ${err.message}"
                        )
                    }
                    emitToast("Server connection failed (Render free tier may be waking up).")
                }
            }
        }
    }

    fun loadFleetDevices(serverUrl: String, fleetId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoteLoading = true, remoteError = null) }
            repository.getFleetDevices(serverUrl, fleetId)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            fleetDevices = res.devices,
                            remoteResultMessage = "Loaded ${res.devices.size} device(s) in fleet ${res.fleetId}."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteError = err.message ?: "Unable to fetch fleet devices."
                        )
                    }
                }
        }
    }

    fun saveTargetDevice(target: SavedTargetDevice) {
        prefs.upsertSavedTarget(target)
        emitToast("Saved target ${target.alias} (${target.deviceId})")
        refreshAllState()
    }

    fun removeTargetDevice(deviceId: String) {
        prefs.removeSavedTarget(deviceId)
        emitToast("Removed $deviceId from local console")
        refreshAllState()
    }

    fun checkServerHealth(serverUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoteLoading = true, remoteError = null) }
            repository.checkHealth(serverUrl)
                .onSuccess { health ->
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            serverHealthSummary = "Online • ${health.service} (${health.registeredDevices} devices)"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            serverHealthSummary = "Offline or waking up: ${err.message}"
                        )
                    }
                }
        }
    }

    fun sendRemoteOnlineCommand(
        serverUrl: String,
        targetDeviceId: String,
        targetPin: String,
        command: RemoteCommandType,
        contactInfo: String = "",
        lockMessage: String = ""
    ) {
        if (targetDeviceId.isBlank() || targetPin.isBlank()) {
            _uiState.update { it.copy(remoteError = "Target Device ID and Secret PIN are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isRemoteLoading = true, remoteError = null, remoteResultMessage = null)
            }
            repository.sendCommand(
                baseUrl = serverUrl,
                request = SendCommandRequest(
                    deviceId = targetDeviceId.trim().uppercase(),
                    pin = targetPin.trim(),
                    command = command.wireValue,
                    contactInfo = contactInfo.trim(),
                    lockMessage = lockMessage.trim()
                )
            ).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isRemoteLoading = false,
                        remoteResultMessage = "Command ${res.command} (${res.lockState.ifBlank { res.status }}) sent to ${res.deviceId}."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRemoteLoading = false,
                        remoteError = err.message ?: "Failed to queue command."
                    )
                }
            }
        }
    }

    fun fetchTargetDeviceStatus(serverUrl: String, targetDeviceId: String, targetPin: String) {
        if (targetDeviceId.isBlank() || targetPin.isBlank()) {
            _uiState.update { it.copy(remoteError = "Target Device ID and Secret PIN are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoteLoading = true, remoteError = null) }
            repository.getDeviceStatus(serverUrl, targetDeviceId, targetPin)
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteTargetStatus = status,
                            remoteResultMessage = "Status updated for ${status.deviceAlias.ifBlank { status.deviceName }}"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isRemoteLoading = false,
                            remoteError = err.message ?: "Failed to fetch device status."
                        )
                    }
                }
        }
    }

    fun sendRemoteOfflineSms(targetPhone: String, rawSmsPayload: String) {
        if (targetPhone.isBlank() || rawSmsPayload.isBlank()) {
            _uiState.update { it.copy(remoteError = "Target phone number and command payload are required.") }
            return
        }
        if (!SecurityController.hasSmsPermissions(context)) {
            _uiState.update { it.copy(remoteError = "Grant SMS permission on Dashboard first.") }
            return
        }
        val ok = SecurityController.sendSmsText(context, targetPhone.trim(), rawSmsPayload.trim())
        if (ok) {
            prefs.appendLog(
                title = "Offline SMS Command Sent",
                detail = "Sent '$rawSmsPayload' to $targetPhone",
                source = "MasterAdmin",
                severity = LogSeverity.INFO
            )
            _uiState.update {
                it.copy(
                    remoteError = null,
                    remoteResultMessage = "SMS '$rawSmsPayload' sent to $targetPhone. Wait 5-15s for GPS reply."
                )
            }
        } else {
            _uiState.update {
                it.copy(remoteError = "Failed to send SMS. Check SIM / SMS permissions.")
            }
        }
    }

    fun saveSettings(
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
    ) {
        prefs.updateSettings {
            it.copy(
                appRole = appRole,
                fleetId = fleetId.ifBlank { "FAMILY-FLEET" },
                deviceAlias = deviceAlias.ifBlank { prefs.deviceDisplayName() },
                secretPin = secretPin.ifBlank { "2468" },
                smsPrefix = smsPrefix.ifBlank { "SG" },
                secretLockKeyword = secretLockKeyword.ifBlank { "#LOCK_PHONE#" },
                serverUrl = serverUrl.ifBlank { "https://sentinelguard-flask-server.onrender.com" },
                deviceId = deviceId.ifBlank { it.deviceId },
                trustedPhone = trustedPhone,
                ownerContactInfo = ownerContactInfo.ifBlank { "@Akimul0" },
                lockMessage = lockMessage.ifBlank { it.lockMessage },
                blockPowerMenu = blockPowerMenu,
                blockSettingsAndShade = blockSettingsAndShade,
                lockOnPowerTamper = lockOnPowerTamper,
                syncIntervalSeconds = syncIntervalSeconds
            )
        }
        prefs.appendLog(
            title = "Multi-Device Settings Saved",
            detail = "Role: ${appRole.label} • Fleet: $fleetId",
            source = "Settings",
            severity = LogSeverity.INFO
        )
        SecurityController.syncGuardServiceState(context)
        emitToast("Settings saved")
        refreshAllState()
    }

    fun clearLogs() {
        prefs.clearLogs()
        emitToast("Security logs cleared")
    }

    fun clearRemoteFeedback() {
        _uiState.update { it.copy(remoteError = null, remoteResultMessage = null) }
    }

    fun emitToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}

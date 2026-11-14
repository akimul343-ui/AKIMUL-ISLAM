package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class AppRole(val label: String) {
    TARGET_CLIENT("Target Client"),
    MASTER_ADMIN("Master Admin"),
    DUAL_MODE("Master + Target")
}

enum class LogSeverity {
    INFO,
    SUCCESS,
    WARNING,
    CRITICAL
}

@JsonClass(generateAdapter = true)
data class SecurityLogEntry(
    val id: String,
    val timestamp: Long,
    val title: String,
    val detail: String,
    val source: String,
    val severity: LogSeverity = LogSeverity.INFO
)

@JsonClass(generateAdapter = true)
data class SavedTargetDevice(
    val deviceId: String,
    val alias: String,
    val phoneNumber: String = "",
    val pin: String = "2468",
    val smsKeyword: String = ""
)

data class ProtectionStatus(
    val isDeviceAdminActive: Boolean = false,
    val isDeviceOwnerActive: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val hasSmsPermissions: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isGuardArmed: Boolean = false,
    val isDeviceLocked: Boolean = false,
    val isPowerMenuBlocked: Boolean = false,
    val isSettingsAndShadeBlocked: Boolean = false,
    val isLauncherIconHidden: Boolean = false,
    val isRemoteServiceRunning: Boolean = false
) {
    val readinessCount: Int
        get() = listOf(
            isDeviceAdminActive,
            isAccessibilityEnabled,
            hasSmsPermissions,
            hasLocationPermission
        ).count { it }

    val totalPillars: Int = 4

    val scorePercent: Int
        get() = ((readinessCount.toFloat() / totalPillars) * 100).toInt()
}

enum class RemoteCommandType(val wireValue: String, val label: String) {
    LOCK("LOCK", "Lock Screen"),
    UNLOCK("UNLOCK", "Unlock Device"),
    LOCATE("LOCATE", "Locate GPS"),
    SIREN("SIREN", "Sound Siren"),
    STOP_SIREN("STOP_SIREN", "Stop Siren"),
    GUARD_ON("GUARD_ON", "Arm Power Guard"),
    GUARD_OFF("GUARD_OFF", "Disarm Power Guard")
}

// Flask Master-Slave Backend DTOs
@JsonClass(generateAdapter = true)
data class SendCommandRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "pin") val pin: String,
    @Json(name = "command") val command: String,
    @Json(name = "contact_info") val contactInfo: String = "",
    @Json(name = "lock_message") val lockMessage: String = "",
    @Json(name = "issued_by") val issuedBy: String = "master_admin"
)

@JsonClass(generateAdapter = true)
data class SendCommandResponse(
    @Json(name = "status") val status: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "command") val command: String,
    @Json(name = "lock_state") val lockState: String = "",
    @Json(name = "contact_info") val contactInfo: String = "",
    @Json(name = "command_id") val commandId: String = ""
)

@JsonClass(generateAdapter = true)
data class QueuedCommandDto(
    @Json(name = "id") val id: String,
    @Json(name = "command") val command: String,
    @Json(name = "contact_info") val contactInfo: String = "",
    @Json(name = "lock_message") val lockMessage: String = "",
    @Json(name = "issued_by") val issuedBy: String = "",
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class PollCommandsResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "is_locked") val isLocked: Boolean = false,
    @Json(name = "lock_state") val lockState: String = "UNLOCKED",
    @Json(name = "contact_info") val contactInfo: String = "",
    @Json(name = "lock_message") val lockMessage: String = "",
    @Json(name = "commands") val commands: List<QueuedCommandDto> = emptyList(),
    @Json(name = "server_time") val serverTime: String = ""
)

@JsonClass(generateAdapter = true)
data class LocationReportRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "device_alias") val deviceAlias: String,
    @Json(name = "fleet_id") val fleetId: String,
    @Json(name = "pin") val pin: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "accuracy_meters") val accuracyMeters: Float,
    @Json(name = "battery_percent") val batteryPercent: Int,
    @Json(name = "provider") val provider: String
)

@JsonClass(generateAdapter = true)
data class LocationInfoDto(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "accuracy_meters") val accuracyMeters: Float = 0f,
    @Json(name = "battery_percent") val batteryPercent: Int = -1,
    @Json(name = "provider") val provider: String = "",
    @Json(name = "maps_url") val mapsUrl: String = "",
    @Json(name = "reported_at") val reportedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class DeviceStatusResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "device_alias") val deviceAlias: String = "",
    @Json(name = "fleet_id") val fleetId: String = "",
    @Json(name = "guard_armed") val guardArmed: Boolean = false,
    @Json(name = "is_locked") val isLocked: Boolean = false,
    @Json(name = "lock_state") val lockState: String = "UNLOCKED",
    @Json(name = "contact_info") val contactInfo: String = "",
    @Json(name = "lock_message") val lockMessage: String = "",
    @Json(name = "is_device_owner") val isDeviceOwner: Boolean = false,
    @Json(name = "battery_percent") val batteryPercent: Int = -1,
    @Json(name = "last_seen") val lastSeen: String = "",
    @Json(name = "pending_commands") val pendingCommands: Int = 0,
    @Json(name = "last_location") val lastLocation: LocationInfoDto? = null
)

@JsonClass(generateAdapter = true)
data class FleetDevicesResponse(
    @Json(name = "fleet_id") val fleetId: String = "",
    @Json(name = "devices") val devices: List<DeviceStatusResponse> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String,
    @Json(name = "service") val service: String = "",
    @Json(name = "registered_devices") val registeredDevices: Int = 0,
    @Json(name = "timestamp") val timestamp: String = ""
)

data class OemGuideItem(
    val brand: String,
    val osName: String,
    val steps: List<String>
)

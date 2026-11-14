package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class GuardSettings(
    val appRole: AppRole = AppRole.DUAL_MODE,
    val fleetId: String = Constants.DEFAULT_FLEET_ID,
    val deviceId: String = "",
    val deviceAlias: String = "",
    val secretPin: String = "2468",
    val smsPrefix: String = "SG",
    val secretLockKeyword: String = "#LOCK_PHONE#",
    val serverUrl: String = Constants.RENDER_BASE_URL,
    val trustedPhone: String = "",
    val ownerContactInfo: String = Constants.DEFAULT_OWNER_CONTACT,
    val guardArmed: Boolean = false,
    val isDeviceLocked: Boolean = false,
    val lockContactInfo: String = "@Akimul0",
    val lockMessage: String = "This device is lost or locked by SentinelGuard. Please contact the owner immediately.",
    val blockPowerMenu: Boolean = true,
    val blockSettingsAndShade: Boolean = true,
    val lockOnPowerTamper: Boolean = true,
    val syncIntervalSeconds: Int = 20,
    val lastLocationText: String = "No GPS fix recorded yet",
    val lastMapsUrl: String = "",
    val isSirenPlaying: Boolean = false
)

class AntiTheftPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val logListType = Types.newParameterizedType(List::class.java, SecurityLogEntry::class.java)
    private val logAdapter = moshi.adapter<List<SecurityLogEntry>>(logListType)

    private val targetListType = Types.newParameterizedType(List::class.java, SavedTargetDevice::class.java)
    private val targetAdapter = moshi.adapter<List<SavedTargetDevice>>(targetListType)

    init {
        if (prefs.getString(KEY_DEVICE_ID, null).isNullOrBlank()) {
            val generated = "SG-" + UUID.randomUUID().toString().take(6).uppercase()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }
        if (prefs.getString(KEY_DEVICE_ALIAS, null).isNullOrBlank()) {
            prefs.edit().putString(KEY_DEVICE_ALIAS, deviceDisplayName()).apply()
        }
    }

    fun getSettings(): GuardSettings {
        val roleName = prefs.getString(KEY_APP_ROLE, AppRole.DUAL_MODE.name)
        val role = runCatching { AppRole.valueOf(roleName ?: AppRole.DUAL_MODE.name) }
            .getOrDefault(AppRole.DUAL_MODE)

        return GuardSettings(
            appRole = role,
            fleetId = prefs.getString(KEY_FLEET_ID, Constants.DEFAULT_FLEET_ID) ?: Constants.DEFAULT_FLEET_ID,
            deviceId = prefs.getString(KEY_DEVICE_ID, "SG-DEVICE") ?: "SG-DEVICE",
            deviceAlias = prefs.getString(KEY_DEVICE_ALIAS, deviceDisplayName()) ?: deviceDisplayName(),
            secretPin = prefs.getString(KEY_SECRET_PIN, "2468") ?: "2468",
            smsPrefix = prefs.getString(KEY_SMS_PREFIX, "SG") ?: "SG",
            secretLockKeyword = prefs.getString(KEY_LOCK_KEYWORD, "#LOCK_PHONE#") ?: "#LOCK_PHONE#",
            serverUrl = Constants.RENDER_BASE_URL,
            trustedPhone = prefs.getString(KEY_TRUSTED_PHONE, "") ?: "",
            ownerContactInfo = prefs.getString(KEY_OWNER_CONTACT_INFO, Constants.DEFAULT_OWNER_CONTACT)
                ?: Constants.DEFAULT_OWNER_CONTACT,
            guardArmed = prefs.getBoolean(KEY_GUARD_ARMED, false),
            isDeviceLocked = prefs.getBoolean(KEY_IS_DEVICE_LOCKED, false),
            lockContactInfo = prefs.getString(KEY_LOCK_CONTACT_INFO, "@Akimul0") ?: "@Akimul0",
            lockMessage = prefs.getString(
                KEY_LOCK_MESSAGE,
                "This device is lost or locked by SentinelGuard. Please contact the owner immediately."
            ) ?: "This device is lost or locked by SentinelGuard. Please contact the owner immediately.",
            blockPowerMenu = prefs.getBoolean(KEY_BLOCK_POWER_MENU, true),
            blockSettingsAndShade = prefs.getBoolean(KEY_BLOCK_SETTINGS_SHADE, true),
            lockOnPowerTamper = prefs.getBoolean(KEY_LOCK_ON_TAMPER, true),
            syncIntervalSeconds = prefs.getInt(KEY_SYNC_INTERVAL, 20),
            lastLocationText = prefs.getString(KEY_LAST_LOC_TEXT, "No GPS fix recorded yet")
                ?: "No GPS fix recorded yet",
            lastMapsUrl = prefs.getString(KEY_LAST_MAPS_URL, "") ?: "",
            isSirenPlaying = prefs.getBoolean(KEY_SIREN_PLAYING, false)
        )
    }

    fun updateSettings(transform: (GuardSettings) -> GuardSettings) {
        val updated = transform(getSettings())
        prefs.edit()
            .putString(KEY_APP_ROLE, updated.appRole.name)
            .putString(KEY_FLEET_ID, updated.fleetId.trim().uppercase().ifEmpty { "FAMILY-FLEET" })
            .putString(KEY_DEVICE_ID, updated.deviceId.trim().uppercase())
            .putString(KEY_DEVICE_ALIAS, updated.deviceAlias.trim().ifEmpty { deviceDisplayName() })
            .putString(KEY_SECRET_PIN, updated.secretPin.trim())
            .putString(KEY_SMS_PREFIX, updated.smsPrefix.trim().uppercase().ifEmpty { "SG" })
            .putString(KEY_LOCK_KEYWORD, updated.secretLockKeyword.trim().ifEmpty { "#LOCK_PHONE#" })
            .putString(KEY_SERVER_URL, Constants.RENDER_BASE_URL)
            .putString(KEY_TRUSTED_PHONE, updated.trustedPhone.trim())
            .putString(KEY_OWNER_CONTACT_INFO, updated.ownerContactInfo.trim())
            .putBoolean(KEY_GUARD_ARMED, updated.guardArmed)
            .putBoolean(KEY_IS_DEVICE_LOCKED, updated.isDeviceLocked)
            .putString(KEY_LOCK_CONTACT_INFO, updated.lockContactInfo.trim())
            .putString(KEY_LOCK_MESSAGE, updated.lockMessage.trim())
            .putBoolean(KEY_BLOCK_POWER_MENU, updated.blockPowerMenu)
            .putBoolean(KEY_BLOCK_SETTINGS_SHADE, updated.blockSettingsAndShade)
            .putBoolean(KEY_LOCK_ON_TAMPER, updated.lockOnPowerTamper)
            .putInt(KEY_SYNC_INTERVAL, updated.syncIntervalSeconds.coerceIn(10, 120))
            .putString(KEY_LAST_LOC_TEXT, updated.lastLocationText)
            .putString(KEY_LAST_MAPS_URL, updated.lastMapsUrl)
            .putBoolean(KEY_SIREN_PLAYING, updated.isSirenPlaying)
            .apply()
        notifySettingsChanged()
    }

    fun getSavedTargets(): List<SavedTargetDevice> {
        val raw = prefs.getString(KEY_SAVED_TARGETS, null) ?: return emptyList()
        return runCatching { targetAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    fun upsertSavedTarget(target: SavedTargetDevice) {
        val normalizedId = target.deviceId.trim().uppercase()
        if (normalizedId.isBlank()) return
        val current = getSavedTargets().filterNot { it.deviceId.equals(normalizedId, ignoreCase = true) }
            .toMutableList()
        current.add(
            0,
            target.copy(
                deviceId = normalizedId,
                alias = target.alias.trim().ifEmpty { normalizedId },
                smsKeyword = target.smsKeyword.trim().ifEmpty { "#LOCK_${normalizedId}#" }
            )
        )
        prefs.edit().putString(KEY_SAVED_TARGETS, targetAdapter.toJson(current)).apply()
        notifySettingsChanged()
    }

    fun removeSavedTarget(deviceId: String) {
        val updated = getSavedTargets().filterNot { it.deviceId.equals(deviceId, ignoreCase = true) }
        prefs.edit().putString(KEY_SAVED_TARGETS, targetAdapter.toJson(updated)).apply()
        notifySettingsChanged()
    }

    fun isGuardArmed(): Boolean = prefs.getBoolean(KEY_GUARD_ARMED, false)

    fun isDeviceLocked(): Boolean = prefs.getBoolean(KEY_IS_DEVICE_LOCKED, false)

    fun isBlockPowerMenuEnabled(): Boolean = prefs.getBoolean(KEY_BLOCK_POWER_MENU, true)

    fun isBlockSettingsAndShadeEnabled(): Boolean = prefs.getBoolean(KEY_BLOCK_SETTINGS_SHADE, true)

    fun isLockOnPowerTamperEnabled(): Boolean = prefs.getBoolean(KEY_LOCK_ON_TAMPER, true)

    fun setLostModeLock(locked: Boolean, contactInfo: String = "", lockMessage: String = "") {
        val editor = prefs.edit().putBoolean(KEY_IS_DEVICE_LOCKED, locked)
        if (locked) {
            editor.putBoolean(KEY_GUARD_ARMED, true)
            if (contactInfo.isNotBlank()) editor.putString(KEY_LOCK_CONTACT_INFO, contactInfo.trim())
            if (lockMessage.isNotBlank()) editor.putString(KEY_LOCK_MESSAGE, lockMessage.trim())
        }
        editor.apply()
        notifySettingsChanged()
    }

    fun setSirenPlaying(playing: Boolean) {
        prefs.edit().putBoolean(KEY_SIREN_PLAYING, playing).apply()
        notifySettingsChanged()
    }

    fun saveLastLocation(summary: String, mapsUrl: String) {
        prefs.edit()
            .putString(KEY_LAST_LOC_TEXT, summary)
            .putString(KEY_LAST_MAPS_URL, mapsUrl)
            .apply()
        notifySettingsChanged()
    }

    fun getLogs(): List<SecurityLogEntry> {
        val raw = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyList()
        return runCatching { logAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    fun appendLog(
        title: String,
        detail: String,
        source: String,
        severity: LogSeverity = LogSeverity.INFO
    ) {
        val current = getLogs().toMutableList()
        current.add(
            0,
            SecurityLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                title = title,
                detail = detail,
                source = source,
                severity = severity
            )
        )
        val trimmed = current.take(100)
        prefs.edit().putString(KEY_LOGS_JSON, logAdapter.toJson(trimmed)).apply()
        notifyLogsChanged()
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS_JSON).apply()
        notifyLogsChanged()
    }

    fun deviceDisplayName(): String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    companion object {
        private const val PREFS_NAME = "sentinel_guard_prefs"

        private const val KEY_APP_ROLE = "app_role"
        private const val KEY_FLEET_ID = "fleet_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_SECRET_PIN = "secret_pin"
        private const val KEY_SMS_PREFIX = "sms_prefix"
        private const val KEY_LOCK_KEYWORD = "lock_keyword"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TRUSTED_PHONE = "trusted_phone"
        private const val KEY_OWNER_CONTACT_INFO = "owner_contact_info"
        private const val KEY_GUARD_ARMED = "guard_armed"
        private const val KEY_IS_DEVICE_LOCKED = "is_device_locked"
        private const val KEY_LOCK_CONTACT_INFO = "lock_contact_info"
        private const val KEY_LOCK_MESSAGE = "lock_message"
        private const val KEY_BLOCK_POWER_MENU = "block_power_menu"
        private const val KEY_BLOCK_SETTINGS_SHADE = "block_settings_shade"
        private const val KEY_LOCK_ON_TAMPER = "lock_on_power_tamper"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_LAST_LOC_TEXT = "last_loc_text"
        private const val KEY_LAST_MAPS_URL = "last_maps_url"
        private const val KEY_SIREN_PLAYING = "siren_playing"
        private const val KEY_LOGS_JSON = "logs_json"
        private const val KEY_SAVED_TARGETS = "saved_targets_json"

        private val _settingsVersion = MutableStateFlow(0L)
        val settingsVersion: StateFlow<Long> = _settingsVersion.asStateFlow()

        private val _logsVersion = MutableStateFlow(0L)
        val logsVersion: StateFlow<Long> = _logsVersion.asStateFlow()

        private fun notifySettingsChanged() {
            _settingsVersion.value = System.currentTimeMillis()
        }

        private fun notifyLogsChanged() {
            _logsVersion.value = System.currentTimeMillis()
        }
    }
}

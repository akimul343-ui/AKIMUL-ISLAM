package com.example.security

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.SmsManager
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.data.AntiTheftPrefs
import com.example.data.LocationReportRequest
import com.example.data.LogSeverity
import com.example.data.ProtectionStatus
import com.example.data.RemoteRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object SecurityController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null

    fun adminComponentName(context: Context): ComponentName =
        ComponentName(context.applicationContext, AntiTheftDeviceAdminReceiver::class.java)

    fun launcherAliasComponentName(context: Context): ComponentName =
        ComponentName(context.applicationContext, "com.example.LauncherAlias")

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return dpm.isAdminActive(adminComponentName(context))
    }

    fun isDeviceOwnerApp(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun setUninstallBlocked(context: Context, blocked: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false
        return runCatching {
            dpm.setUninstallBlocked(adminComponentName(context), context.packageName, blocked)
            true
        }.getOrDefault(false)
    }

    fun adbDeviceOwnerCommand(context: Context): String {
        val component = adminComponentName(context).flattenToShortString()
        return "adb shell dpm set-device-owner $component"
    }

    fun isLauncherIconHidden(context: Context): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(launcherAliasComponentName(context))
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setLauncherIconHidden(context: Context, hidden: Boolean) {
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(
            launcherAliasComponentName(context),
            newState,
            PackageManager.DONT_KILL_APP
        )
        AntiTheftPrefs(context).appendLog(
            title = if (hidden) "App Icon Hidden (Stealth Mode)" else "App Icon Restored",
            detail = if (hidden) {
                "LauncherAlias disabled via PackageManager.COMPONENT_ENABLED_STATE_DISABLED."
            } else {
                "Launcher icon re-enabled in app drawer."
            },
            source = "StealthMode",
            severity = LogSeverity.INFO
        )
    }

    fun buildDeviceAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required to lock your phone screen immediately on SMS or remote anti-theft commands."
            )
        }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, PowerMenuGuardService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentString = splitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentString)
            if (enabledComponent != null && enabledComponent == expectedComponent) {
                return true
            }
        }
        return false
    }

    fun buildAccessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    fun hasSmsPermissions(context: Context): Boolean {
        val receive = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val send = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return receive && send
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun readProtectionStatus(context: Context): ProtectionStatus {
        val prefs = AntiTheftPrefs(context)
        val settings = prefs.getSettings()
        val accessibilityOn = isAccessibilityServiceEnabled(context)
        return ProtectionStatus(
            isDeviceAdminActive = isDeviceAdminActive(context),
            isDeviceOwnerActive = isDeviceOwnerApp(context),
            isAccessibilityEnabled = accessibilityOn,
            hasSmsPermissions = hasSmsPermissions(context),
            hasLocationPermission = hasLocationPermission(context),
            hasNotificationPermission = hasNotificationPermission(context),
            isGuardArmed = settings.guardArmed,
            isDeviceLocked = settings.isDeviceLocked,
            isPowerMenuBlocked = settings.guardArmed && settings.blockPowerMenu && accessibilityOn,
            isSettingsAndShadeBlocked = settings.guardArmed && settings.blockSettingsAndShade && accessibilityOn,
            isLauncherIconHidden = isLauncherIconHidden(context),
            isRemoteServiceRunning = RemoteGuardService.isRunning
        )
    }

    fun launchLockOverlay(context: Context) {
        val intent = Intent(context.applicationContext, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching {
            context.applicationContext.startActivity(intent)
        }
    }

    fun enforceLostModeLock(
        context: Context,
        source: String,
        contactInfo: String = "",
        lockMessage: String = ""
    ): Boolean {
        val appContext = context.applicationContext
        val prefs = AntiTheftPrefs(appContext)
        prefs.setLostModeLock(
            locked = true,
            contactInfo = contactInfo,
            lockMessage = lockMessage
        )
        val effectiveContact = prefs.getSettings().let {
            it.lockContactInfo.ifBlank { it.ownerContactInfo }
        }
        NotificationHelper.showLostModeFullScreenNotification(appContext, effectiveContact)
        launchLockOverlay(appContext)
        return lockDeviceNow(appContext, source)
    }

    fun releaseLostModeLock(context: Context, source: String) {
        val appContext = context.applicationContext
        val prefs = AntiTheftPrefs(appContext)
        prefs.setLostModeLock(locked = false)
        NotificationHelper.clearLostModeNotification(appContext)
        prefs.appendLog(
            title = "Lost-Mode Lock Cleared",
            detail = "Device unlocked remotely.",
            source = source,
            severity = LogSeverity.SUCCESS
        )
    }

    fun lockDeviceNow(context: Context, source: String): Boolean {
        val prefs = AntiTheftPrefs(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm == null || !isDeviceAdminActive(context)) {
            prefs.appendLog(
                title = "Lock Screen Warning (Device Admin Inactive)",
                detail = "Overlay lock triggered by $source, but Device Admin hardware lock is not granted.",
                source = source,
                severity = LogSeverity.WARNING
            )
            return false
        }
        return try {
            dpm.lockNow()
            prefs.appendLog(
                title = "Device Screen Locked",
                detail = "Hardware lock & Lost-Mode overlay executed.",
                source = source,
                severity = LogSeverity.CRITICAL
            )
            NotificationHelper.showSecurityAlert(
                context,
                "Device Locked by SentinelGuard",
                "Screen locked via $source."
            )
            true
        } catch (e: SecurityException) {
            prefs.appendLog(
                title = "Lock Security Error",
                detail = e.message ?: "Unable to lock screen.",
                source = source,
                severity = LogSeverity.WARNING
            )
            false
        }
    }

    fun startSiren(context: Context, source: String) {
        val prefs = AntiTheftPrefs(context)
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }

            mediaPlayer?.release()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                prepare()
                start()
            }

            triggerAlertVibration(context)
            prefs.setSirenPlaying(true)
            prefs.appendLog(
                title = "Emergency Siren Activated",
                detail = "Max-volume alarm siren triggered.",
                source = source,
                severity = LogSeverity.CRITICAL
            )
            NotificationHelper.showSecurityAlert(
                context,
                "Anti-Theft Siren Active",
                "Triggered via $source. Open SentinelGuard to silence."
            )
        }.onFailure { err ->
            prefs.appendLog(
                title = "Siren Playback Error",
                detail = err.message ?: "Could not start alarm audio.",
                source = source,
                severity = LogSeverity.WARNING
            )
        }
    }

    fun stopSiren(context: Context, source: String) {
        val prefs = AntiTheftPrefs(context)
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        }
        prefs.setSirenPlaying(false)
        prefs.appendLog(
            title = "Siren Silenced",
            detail = "Emergency alarm stopped.",
            source = source,
            severity = LogSeverity.INFO
        )
    }

    private fun triggerAlertVibration(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(900)
                }
            }
        }
    }

    fun batteryPercentage(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun dispatchLocationCapture(
        context: Context,
        source: String,
        replySmsTo: String? = null,
        uploadToServer: Boolean = true,
        repository: RemoteRepository = RemoteRepository()
    ) {
        val appContext = context.applicationContext
        val prefs = AntiTheftPrefs(appContext)
        if (!hasLocationPermission(appContext)) {
            prefs.appendLog(
                title = "Location Fix Failed",
                detail = "Location permission missing when requested by $source.",
                source = source,
                severity = LogSeverity.WARNING
            )
            if (!replySmsTo.isNullOrBlank()) {
                sendSmsText(
                    appContext,
                    replySmsTo,
                    "SentinelGuard: Location permission is disabled on target phone."
                )
            }
            return
        }

        scope.launch {
            val location = getBestLocation(appContext)
            if (location == null) {
                prefs.appendLog(
                    title = "GPS Fix Unavailable",
                    detail = "Could not obtain GPS/network fix (GPS may be turned off).",
                    source = source,
                    severity = LogSeverity.WARNING
                )
                if (!replySmsTo.isNullOrBlank()) {
                    sendSmsText(
                        appContext,
                        replySmsTo,
                        "SentinelGuard: Unable to get GPS fix right now. Battery: ${batteryPercentage(appContext)}%"
                    )
                }
                return@launch
            }

            val latStr = String.format(Locale.US, "%.6f", location.latitude)
            val lngStr = String.format(Locale.US, "%.6f", location.longitude)
            val mapsUrl = "https://maps.google.com/?q=$latStr,$lngStr"
            val battery = batteryPercentage(appContext)
            val summary = "$latStr, $lngStr (±${location.accuracy.toInt()}m) • Bat: $battery%"

            prefs.saveLastLocation(summary, mapsUrl)
            prefs.appendLog(
                title = "GPS Location Captured",
                detail = "$summary\n$mapsUrl",
                source = source,
                severity = LogSeverity.SUCCESS
            )

            if (!replySmsTo.isNullOrBlank()) {
                val smsBody = "SentinelGuard Alert!\nLoc: $latStr,$lngStr\nAcc: ${location.accuracy.toInt()}m | Bat: $battery%\nMap: $mapsUrl"
                val sent = sendSmsText(appContext, replySmsTo, smsBody)
                prefs.appendLog(
                    title = if (sent) "Location SMS Sent" else "Location SMS Failed",
                    detail = "Reply target: $replySmsTo",
                    source = source,
                    severity = if (sent) LogSeverity.SUCCESS else LogSeverity.WARNING
                )
            }

            if (uploadToServer) {
                val settings = prefs.getSettings()
                repository.reportLocation(
                    baseUrl = settings.serverUrl,
                    request = LocationReportRequest(
                        deviceId = settings.deviceId,
                        deviceName = prefs.deviceDisplayName(),
                        deviceAlias = settings.deviceAlias,
                        fleetId = settings.fleetId,
                        pin = settings.secretPin,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        batteryPercent = battery,
                        provider = location.provider ?: "fused"
                    )
                ).onFailure { err ->
                    prefs.appendLog(
                        title = "Server Location Sync Failed",
                        detail = err.message ?: "Offline or server asleep",
                        source = source,
                        severity = LogSeverity.INFO
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getBestLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val fusedCurrent = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val tokenSource = CancellationTokenSource()
                cont.invokeOnCancellation { tokenSource.cancel() }
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
        if (fusedCurrent != null) return fusedCurrent

        val fusedLast = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
        if (fusedLast != null) return fusedLast

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                best = loc
            }
        }
        return best
    }

    fun sendSmsText(context: Context, destinationPhone: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (destinationPhone.isBlank()) return false

        return runCatching {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destinationPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destinationPhone, null, message, null, null)
            }
            true
        }.getOrDefault(false)
    }

    fun syncGuardServiceState(context: Context) {
        val prefs = AntiTheftPrefs(context)
        val shouldRun = prefs.isGuardArmed() && hasLocationPermission(context)
        val intent = Intent(context, RemoteGuardService::class.java)
        if (shouldRun) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }
}

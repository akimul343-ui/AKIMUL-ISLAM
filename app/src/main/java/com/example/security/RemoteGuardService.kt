package com.example.security

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.data.AntiTheftPrefs
import com.example.data.LogSeverity
import com.example.data.QueuedCommandDto
import com.example.data.RemoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service polling the Python Flask backend (/api/poll/<device_id>).
 * Enforces persistent server lock_state ("LOCKED" / "UNLOCKED") and displays LockOverlayActivity
 * with custom owner contact info.
 */
class RemoteGuardService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = RemoteRepository()
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = AntiTheftPrefs(this)
        val settings = prefs.getSettings()
        if ((!settings.guardArmed && !settings.isDeviceLocked) || !SecurityController.hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationHelper.buildGuardNotification(
            this,
            "Fleet ${settings.fleetId} • ${settings.deviceId} • Polling every ${settings.syncIntervalSeconds}s"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIF_ID_GUARD_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID_GUARD_SERVICE, notification)
        }

        isRunning = true
        startPollingLoop()
        return START_STICKY
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            val prefs = AntiTheftPrefs(this@RemoteGuardService)

            while (isActive) {
                val settings = prefs.getSettings()
                if (!settings.guardArmed && !settings.isDeviceLocked) {
                    stopSelf()
                    break
                }

                repository.pollCommands(
                    baseUrl = settings.serverUrl,
                    deviceId = settings.deviceId,
                    pin = settings.secretPin,
                    deviceName = prefs.deviceDisplayName(),
                    deviceAlias = settings.deviceAlias,
                    fleetId = settings.fleetId,
                    guardArmed = settings.guardArmed,
                    isDeviceOwner = SecurityController.isDeviceOwnerApp(this@RemoteGuardService),
                    batteryPercent = SecurityController.batteryPercentage(this@RemoteGuardService)
                ).onSuccess { response ->
                    // Enforce persistent server lock state
                    if (response.isLocked && !prefs.isDeviceLocked()) {
                        SecurityController.enforceLostModeLock(
                            context = this@RemoteGuardService,
                            source = "Flask Lock State",
                            contactInfo = response.contactInfo,
                            lockMessage = response.lockMessage
                        )
                    } else if (!response.isLocked && prefs.isDeviceLocked() &&
                        response.commands.any { it.command.equals("UNLOCK", ignoreCase = true) }
                    ) {
                        SecurityController.releaseLostModeLock(this@RemoteGuardService, "Flask Unlock")
                    }

                    response.commands.forEach { cmd ->
                        handleRemoteCommand(cmd, prefs)
                    }
                }

                delay(settings.syncIntervalSeconds * 1000L)
            }
        }
    }

    private fun handleRemoteCommand(cmd: QueuedCommandDto, prefs: AntiTheftPrefs) {
        val source = "Flask Cloud"
        when (cmd.command.uppercase()) {
            "LOCK" -> {
                SecurityController.enforceLostModeLock(
                    context = this,
                    source = source,
                    contactInfo = cmd.contactInfo,
                    lockMessage = cmd.lockMessage
                )
                SecurityController.dispatchLocationCapture(this, source, uploadToServer = true)
            }
            "UNLOCK" -> {
                SecurityController.releaseLostModeLock(this, source)
            }
            "LOCATE" -> {
                SecurityController.dispatchLocationCapture(this, source, uploadToServer = true)
            }
            "SIREN" -> {
                SecurityController.startSiren(this, source)
            }
            "STOP_SIREN" -> {
                SecurityController.stopSiren(this, source)
            }
            "GUARD_ON" -> {
                prefs.updateSettings {
                    it.copy(guardArmed = true, blockPowerMenu = true, blockSettingsAndShade = true)
                }
                prefs.appendLog(
                    title = "MDM Guard Armed",
                    detail = "Enabled remotely via Flask backend.",
                    source = source,
                    severity = LogSeverity.SUCCESS
                )
            }
            "GUARD_OFF" -> {
                prefs.updateSettings {
                    it.copy(blockPowerMenu = false, blockSettingsAndShade = false)
                }
                prefs.appendLog(
                    title = "MDM Guard Paused",
                    detail = "Accessibility lockdown paused via remote console.",
                    source = source,
                    severity = LogSeverity.INFO
                )
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

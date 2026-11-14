package com.example.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AntiTheftPrefs
import com.example.data.LogSeverity

class AntiTheftDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AntiTheftPrefs(context).appendLog(
            title = "Device Admin Activated",
            detail = "Remote screen lock privilege granted.",
            source = "DeviceAdmin",
            severity = LogSeverity.SUCCESS
        )
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        SecurityController.setUninstallBlocked(context, true)
        AntiTheftPrefs(context).appendLog(
            title = "Device Owner Provisioned",
            detail = "Knox-style MDM Device Owner active. Uninstall protection enabled.",
            source = "DeviceOwner",
            severity = LogSeverity.SUCCESS
        )
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        AntiTheftPrefs(context).appendLog(
            title = "Device Admin Disable Attempted",
            detail = "Someone attempted to revoke SentinelGuard Device Admin privileges.",
            source = "DeviceAdmin",
            severity = LogSeverity.CRITICAL
        )
        return "Disabling SentinelGuard Device Admin removes remote screen-lock anti-theft protection."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        AntiTheftPrefs(context).appendLog(
            title = "Device Admin Revoked",
            detail = "Remote screen lock is no longer active.",
            source = "DeviceAdmin",
            severity = LogSeverity.WARNING
        )
    }
}

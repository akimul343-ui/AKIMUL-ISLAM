package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AntiTheftPrefs
import com.example.data.LogSeverity

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = AntiTheftPrefs(context)
            if (prefs.isGuardArmed() || prefs.isDeviceLocked()) {
                prefs.appendLog(
                    title = "Protection Restored on Boot",
                    detail = "SentinelGuard resumed polling & lock state after device restart.",
                    source = "SystemBoot",
                    severity = LogSeverity.INFO
                )
                SecurityController.syncGuardServiceState(context)
            }
            if (prefs.isDeviceLocked()) {
                SecurityController.enforceLostModeLock(context, "SystemBoot")
            }
        }
    }
}

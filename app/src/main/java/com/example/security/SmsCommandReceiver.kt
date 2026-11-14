package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.AntiTheftPrefs
import com.example.data.LogSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Offline Multi-Device SMS Command Receiver.
 * Supports unique per-device SMS secret commands as well as PIN-protected commands:
 *   #LOCK_<DEVICE_ID>#          -> Unique per-device instant lock
 *   #LOCK_PHONE#                -> Custom secret lock keyword
 *   <DEVICE_ID>#<CMD>#<PIN>     -> Targeted command for a specific Device ID
 *   <PREFIX>#<CMD>#<PIN>        -> Prefix-matched command
 *   <CMD>#<PIN>                 -> Direct PIN command
 */
class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
        val fullBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
        if (fullBody.isBlank()) return

        val prefs = AntiTheftPrefs(context)
        val settings = prefs.getSettings()
        val parsed = parseSmsCommand(
            rawBody = fullBody,
            configuredPrefix = settings.smsPrefix,
            secretPin = settings.secretPin,
            secretLockKeyword = settings.secretLockKeyword,
            deviceId = settings.deviceId
        ) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeCommand(context.applicationContext, parsed, sender, prefs)
                delay(2500L)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun executeCommand(
        context: Context,
        command: String,
        sender: String,
        prefs: AntiTheftPrefs
    ) {
        val source = "SMS ($sender)"
        when (command) {
            "LOCK" -> {
                prefs.updateSettings {
                    it.copy(guardArmed = true, blockPowerMenu = true, blockSettingsAndShade = true)
                }
                SecurityController.syncGuardServiceState(context)
                SecurityController.enforceLostModeLock(context, source)
                SecurityController.dispatchLocationCapture(context, source, replySmsTo = sender)
            }

            "LOCATE" -> {
                SecurityController.dispatchLocationCapture(context, source, replySmsTo = sender)
            }

            "SIREN" -> {
                SecurityController.startSiren(context, source)
            }

            "STOP" -> {
                SecurityController.stopSiren(context, source)
            }

            "GUARD" -> {
                prefs.updateSettings {
                    it.copy(guardArmed = true, blockPowerMenu = true, blockSettingsAndShade = true)
                }
                SecurityController.syncGuardServiceState(context)
                prefs.appendLog(
                    title = "MDM Guard Armed via SMS",
                    detail = "Power menu, Settings, and Quick Settings blocking enabled.",
                    source = source,
                    severity = LogSeverity.SUCCESS
                )
                SecurityController.sendSmsText(
                    context,
                    sender,
                    "SentinelGuard (${prefs.getSettings().deviceId}): Lost Mode & MDM Guard are now ARMED."
                )
            }
        }
    }

    companion object {
        fun parseSmsCommand(
            rawBody: String,
            configuredPrefix: String,
            secretPin: String,
            secretLockKeyword: String = "#LOCK_PHONE#",
            deviceId: String = ""
        ): String? {
            val normalized = rawBody.trim()
            if (secretLockKeyword.isNotBlank() &&
                normalized.equals(secretLockKeyword.trim(), ignoreCase = true)
            ) {
                return "LOCK"
            }

            val cleanDeviceId = deviceId.trim().uppercase()
            if (cleanDeviceId.isNotBlank() &&
                normalized.equals("#LOCK_${cleanDeviceId}#", ignoreCase = true)
            ) {
                return "LOCK"
            }

            if (secretPin.isBlank()) return null
            val parts = normalized.split("#").map { it.trim() }
            return when (parts.size) {
                2 -> {
                    val cmd = parts[0].uppercase()
                    val pin = parts[1]
                    if (pin == secretPin && cmd in VALID_COMMANDS) cmd else null
                }

                3 -> {
                    val targetOrPrefix = parts[0].uppercase()
                    val cmd = parts[1].uppercase()
                    val pin = parts[2]
                    val matchesPrefix = targetOrPrefix == configuredPrefix.uppercase()
                    val matchesDevice = cleanDeviceId.isNotBlank() && targetOrPrefix == cleanDeviceId
                    if ((matchesPrefix || matchesDevice) && pin == secretPin && cmd in VALID_COMMANDS) {
                        cmd
                    } else {
                        null
                    }
                }

                else -> null
            }
        }

        private val VALID_COMMANDS = setOf("LOCK", "LOCATE", "SIREN", "STOP", "GUARD")
    }
}

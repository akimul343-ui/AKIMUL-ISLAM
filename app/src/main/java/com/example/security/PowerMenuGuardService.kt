package com.example.security

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.AntiTheftPrefs
import com.example.data.LogSeverity

/**
 * Universal MDM-Grade Accessibility Guard.
 * When Armed:
 * 1) Blocks Power/Shutdown menus across AOSP, Xiaomi, Samsung, Realme/Oppo, and Vivo.
 * 2) Blocks Phone Settings & Quick Settings / Notification Panel to prevent disabling Mobile Data, GPS, or Accessibility.
 */
class PowerMenuGuardService : AccessibilityService() {

    private var lastBlockTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AntiTheftPrefs(this).appendLog(
            title = "Accessibility Guard Connected",
            detail = "Monitoring Power Menu, Settings, and Quick Settings while armed.",
            source = "Accessibility",
            severity = LogSeverity.SUCCESS
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = AntiTheftPrefs(this)
        val isLocked = prefs.isDeviceLocked()
        if (!prefs.isGuardArmed() && !isLocked) return

        val packageName = event.packageName?.toString()?.lowercase().orEmpty()
        if (packageName.isBlank()) return

        // If Lost Mode LOCKED is active and user tries to leave our LockOverlayActivity
        if (isLocked && packageName != this.packageName.lowercase()) {
            val now = System.currentTimeMillis()
            if (now - lastBlockTimestamp >= 400L) {
                lastBlockTimestamp = now
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }
                SecurityController.launchLockOverlay(this)
            }
            return
        }

        if (packageName == this.packageName.lowercase()) return

        val eventText = event.text.joinToString(" ").lowercase()
        val className = event.className?.toString()?.lowercase().orEmpty()

        // 1. Power Menu Blocking
        if (prefs.isBlockPowerMenuEnabled() && SYSTEM_POWER_PACKAGES.any { packageName.contains(it) }) {
            val matchedPowerEvent = POWER_MENU_CLASS_HINTS.any { className.contains(it) } ||
                POWER_MENU_KEYWORDS.any { eventText.contains(it) }
            val matchedPowerTree = if (!matchedPowerEvent) {
                inspectRoot(rootInActiveWindow, POWER_MENU_KEYWORDS, POWER_VIEW_IDS)
            } else {
                false
            }

            if (matchedPowerEvent || matchedPowerTree) {
                enforceBlock(
                    prefs = prefs,
                    title = "Power Menu Blocked",
                    detail = "Intercepted power/shutdown dialog from $packageName",
                    dismissShade = false
                )
                return
            }
        }

        // 2. Settings & Notification Panel (Quick Settings) Blocking while Armed
        if (prefs.isBlockSettingsAndShadeEnabled()) {
            if (SETTINGS_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }) {
                enforceBlock(
                    prefs = prefs,
                    title = "Settings Access Blocked",
                    detail = "Blocked unauthorized Settings launch ($packageName) while Guard is armed.",
                    dismissShade = false
                )
                return
            }

            if (packageName.contains("systemui")) {
                val isShadeOrQs = SHADE_CLASS_HINTS.any { className.contains(it) } ||
                    SHADE_KEYWORDS.any { eventText.contains(it) } ||
                    inspectRoot(rootInActiveWindow, SHADE_KEYWORDS, SHADE_VIEW_IDS)

                if (isShadeOrQs) {
                    enforceBlock(
                        prefs = prefs,
                        title = "Quick Settings / Shade Blocked",
                        detail = "Blocked notification panel pull-down to protect Mobile Data & GPS.",
                        dismissShade = true
                    )
                }
            }
        }
    }

    private fun enforceBlock(
        prefs: AntiTheftPrefs,
        title: String,
        detail: String,
        dismissShade: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestamp < 700L) return
        lastBlockTimestamp = now

        if (dismissShade && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)

        prefs.appendLog(
            title = title,
            detail = detail,
            source = "PowerMenuGuard",
            severity = LogSeverity.CRITICAL
        )

        if (prefs.isLockOnPowerTamperEnabled()) {
            SecurityController.lockDeviceNow(this, "PowerMenuGuard")
        }
    }

    private fun inspectRoot(
        root: AccessibilityNodeInfo?,
        keywords: List<String>,
        viewIds: List<String>
    ): Boolean {
        if (root == null) return false
        return runCatching {
            findMatchInNode(root, keywords, viewIds, depth = 0)
        }.getOrDefault(false)
    }

    private fun findMatchInNode(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        viewIds: List<String>,
        depth: Int
    ): Boolean {
        if (depth > 6) return false
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()

        if (keywords.any { text.contains(it) || desc.contains(it) }) return true
        if (viewIds.any { viewId.contains(it) }) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findMatchInNode(child, keywords, viewIds, depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() {
        AntiTheftPrefs(this).appendLog(
            title = "Accessibility Guard Interrupted",
            detail = "Accessibility service was interrupted by the system.",
            source = "Accessibility",
            severity = LogSeverity.WARNING
        )
    }

    companion object {
        private val SYSTEM_POWER_PACKAGES = listOf(
            "com.android.systemui",
            "android",
            "com.miui.powerkeeper",
            "com.miui.systemui",
            "com.samsung.android.globalactions",
            "com.sec.android.app.shutdown",
            "com.coloros.globalactions",
            "com.oplus.globalactions",
            "com.realme.globalactions",
            "com.vivo.globalactions"
        )

        private val SETTINGS_PACKAGES = listOf(
            "com.android.settings",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.samsung.android.lool"
        )

        private val POWER_MENU_CLASS_HINTS = listOf(
            "globalactionsdialog",
            "globalactions",
            "poweroffdialog",
            "shutdowndialog"
        )

        private val POWER_MENU_KEYWORDS = listOf(
            "power off",
            "shut down",
            "shutdown",
            "tap to power off",
            "slide to power off",
            "restart",
            "reboot",
            "lockdown",
            "পাওয়ার অফ",
            "বন্ধ করুন",
            "রিস্টার্ট"
        )

        private val POWER_VIEW_IDS = listOf("global_actions", "power_off", "shutdown")

        private val SHADE_CLASS_HINTS = listOf(
            "notificationpanel",
            "shadewindow",
            "quicksettings",
            "qspanel",
            "statusbarexpanded"
        )

        private val SHADE_KEYWORDS = listOf(
            "airplane mode",
            "mobile data",
            "quick settings",
            "notification shade"
        )

        private val SHADE_VIEW_IDS = listOf(
            "qs_panel",
            "notification_panel",
            "quick_settings_panel",
            "status_bar_expanded"
        )
    }
}

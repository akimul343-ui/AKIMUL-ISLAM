package com.example.security

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.AntiTheftPrefs
import com.example.data.LogSeverity
import com.example.ui.theme.SentinelGuardTheme

/**
 * Full-Screen Custom Lost-Mode Lock Activity.
 * Blocks Back navigation, pins via LockTask when Device Owner is active,
 * and prominently displays the owner's custom contact info (e.g. @Akimul0 or Phone).
 */
class LockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enforceLockTaskIfDeviceOwner()

        val prefs = AntiTheftPrefs(this)
        setContent {
            SentinelGuardTheme(darkTheme = true) {
                var settings by remember { mutableStateOf(prefs.getSettings()) }

                LaunchedEffect(Unit) {
                    AntiTheftPrefs.settingsVersion.collect {
                        val latest = prefs.getSettings()
                        settings = latest
                        if (!latest.isDeviceLocked) {
                            releaseAndFinish()
                        }
                    }
                }

                // Block Back button completely while Lost Mode is active
                BackHandler(enabled = true) { }

                LockOverlayScreen(
                    deviceId = settings.deviceId,
                    contactInfo = settings.lockContactInfo.ifBlank { settings.ownerContactInfo },
                    lockMessage = settings.lockMessage,
                    expectedPin = settings.secretPin,
                    onOwnerUnlock = {
                        prefs.setLostModeLock(locked = false)
                        prefs.appendLog(
                            title = "Lost-Mode Unlocked by Owner PIN",
                            detail = "LockOverlayActivity dismissed with valid PIN.",
                            source = "LockOverlay",
                            severity = LogSeverity.SUCCESS
                        )
                        releaseAndFinish()
                    }
                )
            }
        }
    }

    private fun enforceLockTaskIfDeviceOwner() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (dpm.isDeviceOwnerApp(packageName)) {
            runCatching {
                dpm.setLockTaskPackages(
                    SecurityController.adminComponentName(this),
                    arrayOf(packageName)
                )
                startLockTask()
            }
        }
    }

    private fun releaseAndFinish() {
        runCatching { stopLockTask() }
        finish()
    }
}

@Composable
private fun LockOverlayScreen(
    deviceId: String,
    contactInfo: String,
    lockMessage: String,
    expectedPin: String,
    onOwnerUnlock: () -> Unit
) {
    var showPinPrompt by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1F070C), Color(0xFF070C18), Color(0xFF0B132B))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                color = Color(0x33EF4444),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFFEF4444))
            ) {
                Box(
                    modifier = Modifier.padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Device Locked",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DEVICE LOCKED",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Protected by SentinelGuard MDM • ID: $deviceId",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = lockMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = Color(0xFF070C18),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF28354F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "OWNER CONTACT / TELEGRAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E5FF)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = contactInfo.ifBlank { "Contact Owner" },
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("lock_overlay_contact_text")
                            )
                        }
                    }
                }
            }

            if (!showPinPrompt) {
                OutlinedButton(
                    onClick = { showPinPrompt = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("owner_unlock_toggle_button")
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Owner PIN Unlock")
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            pinError = false
                        },
                        label = { Text("Enter Owner Secret PIN") },
                        isError = pinError,
                        supportingText = if (pinError) {
                            { Text("Incorrect Secret PIN") }
                        } else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_overlay_pin_input")
                    )

                    Button(
                        onClick = {
                            if (pinInput.trim() == expectedPin.trim()) {
                                onOwnerUnlock()
                            } else {
                                pinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("lock_overlay_unlock_button")
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Device", color = Color(0xFF070C18))
                    }
                }
            }
        }
    }
}

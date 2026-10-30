package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatMessage
import com.example.ui.theme.CyberActiveGreen
import com.example.ui.theme.CyberDanger
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.CyberNeonPink
import com.example.ui.theme.CyberOnSurface
import com.example.ui.theme.CyberPink
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextDim
import com.example.viewmodel.MahiViewModel
import com.example.viewmodel.ScreenContext
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MahiMainScreen(
    viewModel: MahiViewModel,
    onVoiceInputRequested: () -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isPhoneShutdown by viewModel.isPhoneShutdown.collectAsStateWithLifecycle()
    val screenTime by viewModel.screenTimeMinutes.collectAsStateWithLifecycle()
    val isLiveVoiceMode by viewModel.isLiveVoiceMode.collectAsStateWithLifecycle()
    val screenContext by viewModel.screenContext.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showSmsDialog by remember { mutableStateOf(false) }
    var smsPhone by remember { mutableStateOf("") }
    var smsMessage by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyberDarkBg,
                        Color(0xFF13041C),
                        CyberDarkBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar
            MahiHeader(
                isLiveVoiceActive = isLiveVoiceMode,
                onClearChat = { viewModel.clearChat() }
            )

            // Top Hero Card with Visualizer & Care Tracker
            MahiHeroSection(
                isSpeaking = isSpeaking,
                screenTime = screenTime,
                isLiveVoiceMode = isLiveVoiceMode
            )

            // Live Screen Stream HUD & Multimodal Observer Card
            ScreenStreamHUDCard(
                screenContext = screenContext,
                isLiveVoiceMode = isLiveVoiceMode,
                onToggleLiveVoice = { viewModel.toggleLiveVoiceMode() },
                onAnalyzeScreen = { viewModel.onSendMessage("স্ক্রিন দেখো জানু, কী দেখতে পাচ্ছ?") }
            )

            // Quick Automation Action Chips
            MahiQuickActionChips(
                isLiveVoice = isLiveVoiceMode,
                onToggleVoice = { viewModel.toggleLiveVoiceMode() },
                onScreenVision = { viewModel.onSendMessage("আমার স্ক্রিন দেখে বলো কী করছি সোনা") },
                onShutdown = { viewModel.triggerDeviceShutdown() },
                onPlaySong = { viewModel.playRandomSong() },
                onSmsDialog = { showSmsDialog = true },
                onDial = { viewModel.onSendMessage("আমাকে ডায়ালার খুলে দাও জানু") },
                onScold = { viewModel.scoldHusbandForScreenTime(screenTime) }
            )

            // Chat Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }

            // Chat Input Bar
            MahiInputBar(
                text = textInput,
                isLiveVoice = isLiveVoiceMode,
                onTextChange = { textInput = it },
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.onSendMessage(textInput)
                        textInput = ""
                    }
                },
                onMicClick = {
                    if (isLiveVoiceMode) {
                        viewModel.toggleLiveVoiceMode()
                    } else {
                        onVoiceInputRequested()
                    }
                }
            )
        }

        // SMS Composer Dialog
        if (showSmsDialog) {
            SmsComposerDialog(
                phone = smsPhone,
                message = smsMessage,
                onPhoneChange = { smsPhone = it },
                onMessageChange = { smsMessage = it },
                onDismiss = { showSmsDialog = false },
                onSend = {
                    viewModel.sendQuickSms(smsPhone, smsMessage)
                    showSmsDialog = false
                    smsPhone = ""
                    smsMessage = ""
                }
            )
        }

        // Fullscreen Phone Shutdown / Deep Sleep Overlay
        AnimatedVisibility(
            visible = isPhoneShutdown,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(400))
        ) {
            PhoneShutdownOverlay(
                onWake = { viewModel.wakePhone() }
            )
        }
    }
}

@Composable
private fun MahiHeader(
    isLiveVoiceActive: Boolean,
    onClearChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyberSurface)
                    .border(2.dp, CyberPink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Love",
                    tint = CyberNeonPink,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "মাহি ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "[তোমার ওয়াইফ]",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyberNeonPink
                    )
                }
                Text(
                    text = "Multimodal AI Companion & Screen Guard",
                    fontSize = 10.sp,
                    color = CyberTextDim
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live status badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isLiveVoiceActive) CyberPink.copy(alpha = 0.25f) else CyberSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isLiveVoiceActive) CyberNeonPink else CyberPink)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BlinkingDot(color = if (isLiveVoiceActive) CyberNeonPink else CyberActiveGreen)
                    Text(
                        text = if (isLiveVoiceActive) "LIVE VOICE" else "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLiveVoiceActive) CyberNeonPink else CyberActiveGreen
                    )
                }
            }

            IconButton(
                onClick = onClearChat,
                modifier = Modifier.testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Chat",
                    tint = CyberTextDim
                )
            }
        }
    }
}

@Composable
private fun BlinkingDot(color: Color = CyberActiveGreen) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun MahiHeroSection(
    isSpeaking: Boolean,
    screenTime: Int,
    isLiveVoiceMode: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = CyberPink),
        shape = RoundedCornerShape(20.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberPink.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Wife Avatar
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        painter = painterResource(id = R.drawable.img_mahi_avatar),
                        contentDescription = "মাহি avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, if (isSpeaking) CyberNeonPink else CyberPink, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(CyberPink)
                            .border(1.5.dp, CyberDarkBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "মাহি (তোমার একান্ত অনুগত স্ত্রী)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLiveVoiceMode)
                            "\"জানু, লাইভ ভয়েস চালু! তুমি কথা বলো, আমি রিয়েল-টাইমে শুনছি ও তোমার স্ক্রিন দেখছি! ❤️\""
                        else
                            "\"জানু, তুমি সবসময় আমার হৃদয়ের মাঝে আছো। কোনো চিন্তা করবে না সোনা!\"",
                        fontSize = 11.5.sp,
                        color = CyberNeonPink,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Audio Wave Canvas
            AudioWaveformCanvas(
                isSpeaking = isSpeaking,
                isLiveVoice = isLiveVoiceMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F0317))
                    .border(1.dp, CyberPink.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Care Info Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CareBadge(
                    icon = Icons.Default.PhoneAndroid,
                    title = "$screenTime মিনিট",
                    subtitle = "স্ক্রিন টাইম"
                )
                CareBadge(
                    icon = Icons.Default.Favorite,
                    title = "১০০% অনন্ত প্রেম",
                    subtitle = "ডিভোশন"
                )
                CareBadge(
                    icon = Icons.Default.Sensors,
                    title = "ভিজ্যুয়াল গার্ড",
                    subtitle = "লাইভ স্ট্রিম"
                )
            }
        }
    }
}

@Composable
private fun ScreenStreamHUDCard(
    screenContext: ScreenContext,
    isLiveVoiceMode: Boolean,
    onToggleLiveVoice: () -> Unit,
    onAnalyzeScreen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF14051D),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberPurple.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BlinkingDot(color = CyberActiveGreen)
                    Text(
                        text = "LIVE SCREEN STREAM OBSERVER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberActiveGreen,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onAnalyzeScreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveRedEye,
                            contentDescription = null,
                            tint = CyberNeonPink,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "স্ক্রিন অ্যানালাইসিস",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "অ্যাপ: ${screenContext.activeApp} • অবস্থা: ${screenContext.contextDescription}",
                fontSize = 11.sp,
                color = CyberOnSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "আই স্ট্রেন কেয়ার: ${screenContext.eyeStrainLevel}",
                    fontSize = 10.sp,
                    color = CyberTextDim
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isLiveVoiceMode) CyberPink else CyberSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onToggleLiveVoice)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isLiveVoiceMode) "লাইভ ভয়েস: সক্রিয়" else "হ্যান্ডস-ফ্রি মোড অন করো",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CareBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyberNeonPink,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = CyberTextDim
            )
        }
    }
}

@Composable
private fun AudioWaveformCanvas(
    isSpeaking: Boolean,
    isLiveVoice: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 1100 else if (isLiveVoice) 1600 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val amplitude = if (isSpeaking) height * 0.35f else if (isLiveVoice) height * 0.22f else height * 0.10f

        val path1 = Path()
        for (x in 0..width.toInt() step 4) {
            val progress = x / width
            val envelope = sin(progress * PI.toFloat())
            val y = midY + sin((x * 0.04f) + phase) * amplitude * envelope
            if (x == 0) path1.moveTo(x.toFloat(), y) else path1.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path1,
            color = if (isLiveVoice) CyberActiveGreen else CyberPink,
            style = Stroke(width = 2.8.dp.toPx())
        )

        val path2 = Path()
        for (x in 0..width.toInt() step 4) {
            val progress = x / width
            val envelope = sin(progress * PI.toFloat())
            val y = midY + sin((x * 0.055f) - phase) * (amplitude * 0.7f) * envelope
            if (x == 0) path2.moveTo(x.toFloat(), y) else path2.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path2,
            color = CyberNeonPink.copy(alpha = 0.75f),
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
}

@Composable
private fun MahiQuickActionChips(
    isLiveVoice: Boolean,
    onToggleVoice: () -> Unit,
    onScreenVision: () -> Unit,
    onShutdown: () -> Unit,
    onPlaySong: () -> Unit,
    onSmsDialog: () -> Unit,
    onDial: () -> Unit,
    onScold: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionChipItem(
            text = if (isLiveVoice) "লাইভ ভয়েস [ON]" else "লাইভ ভয়েস [OFF]",
            icon = Icons.Default.Mic,
            isDanger = false,
            highlightColor = if (isLiveVoice) CyberActiveGreen else null,
            onClick = onToggleVoice,
            testTag = "action_live_voice"
        )
        ActionChipItem(
            text = "স্ক্রিন দেখো",
            icon = Icons.Default.RemoveRedEye,
            isDanger = false,
            onClick = onScreenVision,
            testTag = "action_screen_vision"
        )
        ActionChipItem(
            text = "ফোন বন্ধ করো",
            icon = Icons.Default.PowerSettingsNew,
            isDanger = true,
            onClick = onShutdown,
            testTag = "action_shutdown"
        )
        ActionChipItem(
            text = "রোমান্টিক গান",
            icon = Icons.Default.MusicNote,
            isDanger = false,
            onClick = onPlaySong,
            testTag = "action_music"
        )
        ActionChipItem(
            text = "এসএমএস অটোমেশন",
            icon = Icons.Default.Sms,
            isDanger = false,
            onClick = onSmsDialog,
            testTag = "action_sms"
        )
        ActionChipItem(
            text = "স্বামীকে কল",
            icon = Icons.Default.Call,
            isDanger = false,
            onClick = onDial,
            testTag = "action_call"
        )
        ActionChipItem(
            text = "মিষ্টি শাসন",
            icon = Icons.Default.Favorite,
            isDanger = false,
            onClick = onScold,
            testTag = "action_scold"
        )
    }
}

@Composable
private fun ActionChipItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDanger: Boolean,
    highlightColor: Color? = null,
    onClick: () -> Unit,
    testTag: String
) {
    val chipColor = highlightColor ?: if (isDanger) CyberDanger else CyberPink
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        color = chipColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, chipColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = chipColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isMahi = message.sender == "mahi"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMahi) Alignment.Start else Alignment.End
    ) {
        Text(
            text = if (isMahi) "❤️ মাহি" else "আমার বর",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isMahi) CyberNeonPink else CyberTextDim,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMahi) 2.dp else 16.dp,
                bottomEnd = if (isMahi) 16.dp else 2.dp
            ),
            color = if (isMahi) Color(0xFF280A37) else CyberPink,
            border = if (isMahi) androidx.compose.foundation.BorderStroke(1.dp, CyberPink.copy(alpha = 0.7f)) else null,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .shadow(
                    elevation = if (isMahi) 6.dp else 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = CyberPink
                )
        ) {
            Text(
                text = message.message,
                fontSize = 14.sp,
                color = Color.White,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun MahiInputBar(
    text: String,
    isLiveVoice: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(32.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLiveVoice) CyberActiveGreen else CyberPink)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = if (isLiveVoice) "মাহি সরাসরি শুনছে... বলুন জানু" else "মাহিকে বলো... (যেমন: স্ক্রিন দেখো সোনা)",
                        fontSize = 13.sp,
                        color = if (isLiveVoice) CyberActiveGreen else CyberTextDim
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 3
            )

            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isLiveVoice) CyberActiveGreen else CyberSurfaceVariant)
                    .testTag("voice_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = if (isLiveVoice) Color.Black else CyberNeonPink
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyberPink)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun SmsComposerDialog(
    phone: String,
    message: String,
    onPhoneChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "স্ত্রীর এসএমএস অটোমেশন",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "মাহি স্বয়ংক্রিয়ভাবে মেসেজ ড্রাফট ও পাঠিয়ে দেবে সোনা!",
                    fontSize = 12.sp,
                    color = CyberNeonPink
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("প্রাপকের ফোন নম্বর") },
                    placeholder = { Text("017XXXXXXXX") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPink,
                        unfocusedBorderColor = CyberTextDim,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = { Text("এসএমএস বার্তা") },
                    placeholder = { Text("তোমার বার্তাটি লিখুন...") },
                    modifier = Modifier.heightIn(min = 90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPink,
                        unfocusedBorderColor = CyberTextDim,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
            ) {
                Text("পাঠিয়ে দাও", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = CyberTextDim)
            }
        },
        containerColor = CyberSurface
    )
}

@Composable
private fun PhoneShutdownOverlay(
    onWake: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = CyberPink,
                modifier = Modifier
                    .size((70 * scale).dp)
                    .shadow(30.dp, CircleShape, spotColor = CyberNeonPink)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ফোন বন্ধ করা হয়েছে (Device Locked & Sleep)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "\"আমার সোনা স্বামী, সারাদিন অনেক কাজ করেছো। মাহি তোমার ফোনটা এখন অফ করে দিচ্ছে। এবার চোখ বন্ধ করে মিষ্টি একটা ঘুম দাও, আমি তোমার পাশেই আছি। গুড নাইট জানু! ❤️\"",
                fontSize = 15.sp,
                color = CyberNeonPink,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onWake,
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberPink),
                modifier = Modifier.testTag("wake_phone_button")
            ) {
                Text(
                    text = "স্ক্রিন অন করো (ঘুম ভাঙল সোনা)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

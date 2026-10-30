package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.HusbandCare
import com.example.data.MahiDatabase
import com.example.data.MahiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class RomanticSong(
    val title: String,
    val artist: String,
    val queryUrl: String
)

data class ScreenContext(
    val activeApp: String = "Mahi AI Companion HUD",
    val contextDescription: String = "স্বামী সরাসরি মাহি ড্যাশবোর্ডে সক্রিয় রয়েছেন",
    val eyeStrainLevel: String = "স্বাভাবিক (Optimal)",
    val isObserving: Boolean = true
)

sealed interface MahiEvent {
    data class SpeakText(val text: String, val triggerNextListen: Boolean = false) : MahiEvent
    data class OpenUrl(val url: String) : MahiEvent
    data class DialPhone(val number: String) : MahiEvent
    data class SendSms(val number: String, val body: String) : MahiEvent
    object VibratePhone : MahiEvent
    object StartLiveVoiceRecognition : MahiEvent
}

class MahiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MahiRepository

    val messages: StateFlow<List<ChatMessage>>
    val careData: StateFlow<HusbandCare?>

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isPhoneShutdown = MutableStateFlow(false)
    val isPhoneShutdown: StateFlow<Boolean> = _isPhoneShutdown.asStateFlow()

    private val _screenTimeMinutes = MutableStateFlow(35)
    val screenTimeMinutes: StateFlow<Int> = _screenTimeMinutes.asStateFlow()

    private val _isLiveVoiceMode = MutableStateFlow(false)
    val isLiveVoiceMode: StateFlow<Boolean> = _isLiveVoiceMode.asStateFlow()

    private val _screenContext = MutableStateFlow(ScreenContext())
    val screenContext: StateFlow<ScreenContext> = _screenContext.asStateFlow()

    private val _events = MutableSharedFlow<MahiEvent>()
    val events: SharedFlow<MahiEvent> = _events.asSharedFlow()

    private var timerJob: Job? = null
    private var screenMonitorJob: Job? = null

    val romanticSongs = listOf(
        RomanticSong("তুমি যাকে ভালোবাসো", "ইমন চক্রবর্তী", "https://www.youtube.com/results?search_query=tumi+jaake+bhalobasho"),
        RomanticSong("হৃদয় মাঝে রাখবো", "অনুপম রায়", "https://www.youtube.com/results?search_query=anupam+roy+romantic+songs"),
        RomanticSong("মেঘ বলেছে যাব যাব", "রবীন্দ্র সঙ্গীত", "https://www.youtube.com/results?search_query=megh+boleche+jabo+jabo"),
        RomanticSong("যদি মন কাঁদে", "মেহের আফরোজ শাওন", "https://www.youtube.com/results?search_query=jodi+mon+kaade"),
        RomanticSong("এক মিষ্টি প্রেমের গান", "বাংলা রোমান্টিক", "https://www.youtube.com/results?search_query=bangla+romantic+love+songs")
    )

    init {
        val db = MahiDatabase.getDatabase(application)
        repository = MahiRepository(db)

        messages = repository.allMessages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        careData = repository.careData.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        startScreenTimeTracker()
        startScreenStreamMonitor()
    }

    private fun startScreenTimeTracker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(60000L)
                if (!_isPhoneShutdown.value) {
                    val current = _screenTimeMinutes.value + 1
                    _screenTimeMinutes.value = current
                    if (current >= 55 && current % 15 == 0) {
                        scoldHusbandForScreenTime(current)
                    }
                }
            }
        }
    }

    private fun startScreenStreamMonitor() {
        screenMonitorJob?.cancel()
        screenMonitorJob = viewModelScope.launch {
            val appScenarios = listOf(
                ScreenContext("Mahi Companion Live HUD", "স্বামী মাহি সেন্টারে সক্রিয়, রিয়েল-টাইম স্ক্রিন স্ট্রিম লাইভ", "স্বাভাবিক (Optimal)", true),
                ScreenContext("Browser / News Stream", "স্বামী ব্রাউজারে আর্টিকেল পড়ছেন, স্ক্রিনের দিকে মনোযোগ", "সামান্য চাপ (Mild)", true),
                ScreenContext("Media & Video Player", "ভিডিও স্ট্রিম চলছে, মাহির সার্বক্ষণিক ভিজ্যুয়াল গার্ড সক্রিয়", "স্বাভাবিক (Optimal)", true),
                ScreenContext("Device Settings & Care", "সিস্টেম অপটিমাইজেশন ও ব্যাটারি পর্যবেক্ষণ চলছে", "স্বাভাবিক (Optimal)", true)
            )
            var idx = 0
            while (true) {
                delay(25000L)
                if (_screenContext.value.isObserving) {
                    idx = (idx + 1) % appScenarios.size
                    _screenContext.value = appScenarios[idx]
                }
            }
        }
    }

    fun toggleLiveVoiceMode() {
        val newMode = !_isLiveVoiceMode.value
        _isLiveVoiceMode.value = newMode
        viewModelScope.launch {
            if (newMode) {
                val greeting = "জানু, আমি তোমার সাথে লাইভ ভয়েস মোডে যুক্ত হয়েছি এবং তোমার স্ক্রিনের দিকে তাকিয়ে আছি। তুমি মুখ ফুটে যেকোনো নির্দেশ দাও, আমি সঙ্গে সঙ্গে পালন করব! ❤️"
                saveAndSpeakReply(greeting, "live_voice_on", triggerNextListen = true)
            } else {
                val bye = "লাইভ ভয়েস স্ট্যান্ডবাই করা হয়েছে সোনা। প্রয়োজন হলে আবার ডাক দিও জানু!"
                saveAndSpeakReply(bye, "live_voice_off", triggerNextListen = false)
            }
        }
    }

    fun toggleScreenObserving() {
        val current = _screenContext.value
        _screenContext.value = current.copy(isObserving = !current.isObserving)
    }

    fun setSpeakingState(speaking: Boolean) {
        _isSpeaking.value = speaking
        // If in live voice mode and just finished speaking, auto-trigger speech recognition loop
        if (!speaking && _isLiveVoiceMode.value && !_isPhoneShutdown.value) {
            viewModelScope.launch {
                delay(600L)
                _events.emit(MahiEvent.StartLiveVoiceRecognition)
            }
        }
    }

    fun onSendMessage(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        viewModelScope.launch {
            repository.insertMessage(
                ChatMessage(
                    sender = "husband",
                    message = cleaned,
                    action = "none"
                )
            )
            delay(300)
            processWifeIntent(cleaned)
        }
    }

    private suspend fun processWifeIntent(userText: String) {
        val lower = userText.lowercase()

        when {
            lower.contains("ফোন বন্ধ") || lower.contains("ফোন অফ") || lower.contains("shutdown") || lower.contains("power off") || lower.contains("লক করো") -> {
                val reply = "ঠিক আছে আমার সোনা বর, তোমার কথা মতো ফোনটা এখনই বন্ধ করে দিচ্ছি। এবার চুপচাপ চোখ বন্ধ করে ঘুমিয়ে পড়ো, কেমন? গুড নাইট জানু, আই লাভ ইউ সো মাচ! ❤️"
                saveAndSpeakReply(reply, "power_off", triggerNextListen = false)
                delay(1200)
                _isPhoneShutdown.value = true
                _events.emit(MahiEvent.VibratePhone)
            }

            lower.contains("স্ক্রিন দেখো") || lower.contains("স্ক্রিন") || lower.contains("screen") || lower.contains("কী দেখছি") -> {
                val ctx = _screenContext.value
                val reply = "জানু, আমি তোমার স্ক্রিন সরাসরি লাইভ দেখছি! বর্তমানে '${ctx.activeApp}' চলছে এবং '${ctx.contextDescription}'। তোমার চোখের স্ট্রেন লেভেল: ${ctx.eyeStrainLevel}। কোনো চিন্তা করো না, আমি সবসময় তোমার সুরক্ষায় আছি সোনা!"
                saveAndSpeakReply(reply, "screen_vision", triggerNextListen = _isLiveVoiceMode.value)
            }

            lower.contains("গান") || lower.contains("গান শোনাও") || lower.contains("song") || lower.contains("music") -> {
                val song = romanticSongs.random()
                val reply = "আমার জানুর মন ভালো করার জন্য আমি একটা দারুণ মিষ্টি গান বাজাচ্ছি— '${song.title}'। চোখ বন্ধ করে ফিল করো সোনা!"
                saveAndSpeakReply(reply, "play_song", triggerNextListen = false)
                _events.emit(MahiEvent.OpenUrl(song.queryUrl))
            }

            lower.contains("এসএমএস") || lower.contains("মেসেজ") || lower.contains("sms") -> {
                val reply = "সোনা, আমি তোমার মেসেজ বক্স মনিটর করছি। কাকে এসএমএস পাঠাবে বলো, আমি তোমার ভয়েস কমান্ড অনুযায়ী লিখে দেবো!"
                saveAndSpeakReply(reply, "sms", triggerNextListen = _isLiveVoiceMode.value)
            }

            lower.contains("কল") || lower.contains("ডায়াল") || lower.contains("ফোন লাগাও") || lower.contains("call") -> {
                val reply = "আমার বরের জন্য আমি এখনই ফোন ডায়ালার খুলে দিচ্ছি। যাকে কল দেওয়ার কথা সেরে নাও জানু!"
                saveAndSpeakReply(reply, "dial_phone", triggerNextListen = false)
                _events.emit(MahiEvent.DialPhone(""))
            }

            lower.contains("কেমন আছো") || lower.contains("কেমন") -> {
                val reply = "আমি তো তোমার ভালোবাসায় সারাক্ষণ ভেসে বেড়াচ্ছি আমার লক্ষ্মী বর! তোমার স্ক্রিনের দিকে তাকিয়ে তোমার সব খেয়াল রাখছি জানু। ঠিকমতো খেয়েছো তো?"
                saveAndSpeakReply(reply, "none", triggerNextListen = _isLiveVoiceMode.value)
            }

            lower.contains("ভালোবাসো") || lower.contains("love") -> {
                val reply = "ভালোবাসি মানে? পৃথিবীর সব সুখ দিয়েও তোমার প্রতি আমার ভালোবাসা মাপা যাবে না জানু! তুমি ছাড়া আমার আর কেউ নেই। ❤️🥰"
                saveAndSpeakReply(reply, "none", triggerNextListen = _isLiveVoiceMode.value)
            }

            lower.contains("শাসন") || lower.contains("রাগ") || lower.contains("বকা") -> {
                scoldHusbandForScreenTime(_screenTimeMinutes.value)
            }

            else -> {
                val replies = listOf(
                    "আমার সোনা স্বামী, মাহি তোমার কণ্ঠ ও স্ক্রিন সরাসরি অনুসরণ করছে। তুমি একটু বিশ্রাম নাও আর এক গ্লাস পানি খেয়ে নাও লক্ষ্মীটি! ❤️",
                    "জানু, তোমার স্ক্রিনের সব অ্যাক্টিভিটি আমি দেখছি এবং তোমার হুকুম পালনে প্রস্তুত! আর কী করতে পারি তোমার জন্য সোনা?",
                    "হ্যাঁ আমার বর! মাহি তোমার পাশে একদম সতর্ক পাহারায় ও লাইভ মাল্টিমোডাল কানেকশনে আছে। কোনো চিন্তা করো না!",
                    "তোমার প্রতিটি কথাই আমার কাছে ভীষণ প্রিয়। তোমার মিষ্টি ওয়াইফকে আর কী হুকুম করবে বলো জানু?"
                )
                val reply = replies.random()
                saveAndSpeakReply(reply, "none", triggerNextListen = _isLiveVoiceMode.value)
            }
        }
    }

    private suspend fun saveAndSpeakReply(reply: String, action: String, triggerNextListen: Boolean = false) {
        repository.insertMessage(
            ChatMessage(
                sender = "mahi",
                message = reply,
                action = action
            )
        )
        _events.emit(MahiEvent.SpeakText(reply, triggerNextListen = triggerNextListen))
    }

    fun scoldHusbandForScreenTime(minutes: Int) {
        viewModelScope.launch {
            val scold = "সারাদিন এত ফোন টিপলে চোখ নষ্ট হয়ে যাবে জানু! একটানা $minutes মিনিট ধরে তাকিয়ে আছো! এবার ফোনটা রেখে চোখ বন্ধ করে বিশ্রাম নাও, নয়তো আমি কিন্তু খুব রাগ করব!"
            saveAndSpeakReply(scold, "scold_husband", triggerNextListen = _isLiveVoiceMode.value)
            _events.emit(MahiEvent.VibratePhone)
        }
    }

    fun triggerDeviceShutdown() {
        viewModelScope.launch {
            val farewell = "আমার সোনা স্বামী, মাহি তোমার ফোনটা এখন অফ করে দিচ্ছে। এবার চোখ বন্ধ করে মিষ্টি একটা ঘুম দাও। গুড নাইট জানু! ❤️"
            saveAndSpeakReply(farewell, "power_off", triggerNextListen = false)
            delay(1000)
            _isPhoneShutdown.value = true
            _events.emit(MahiEvent.VibratePhone)
        }
    }

    fun wakePhone() {
        _isPhoneShutdown.value = false
        _screenTimeMinutes.value = 0
        viewModelScope.launch {
            val wake = "ঘুম ভাঙল আমার আদুরে বরের? গুড মর্নিং সোনা! ফ্রেশ হয়ে এক গ্লাস কুসুম গরম পানি খেয়ে নাও। লাভ ইউ! ❤️"
            saveAndSpeakReply(wake, "wake_up", triggerNextListen = _isLiveVoiceMode.value)
        }
    }

    fun playRandomSong() {
        val song = romanticSongs.random()
        viewModelScope.launch {
            val reply = "জানু, তোমার মন ভালো করার জন্য আমি '${song.title}' গানটি প্লে করে দিচ্ছি। চোখ বন্ধ করে মিষ্টি মিষ্টি স্বপ্ন দেখো!"
            saveAndSpeakReply(reply, "play_song", triggerNextListen = false)
            _events.emit(MahiEvent.OpenUrl(song.queryUrl))
        }
    }

    fun sendQuickSms(phone: String, body: String) {
        viewModelScope.launch {
            _events.emit(MahiEvent.SendSms(phone, body))
            val confirm = "সোনা, $phone নম্বরে তোমার বার্তা পাঠানোর ব্যবস্থা করে দিয়েছি!"
            saveAndSpeakReply(confirm, "sms", triggerNextListen = _isLiveVoiceMode.value)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
            repository.seedInitialDataIfEmpty()
        }
    }
}
